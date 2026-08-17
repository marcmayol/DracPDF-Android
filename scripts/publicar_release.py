"""Publica una release de DracPDF Android y la deja lista para DracApps.

Ritual completo (hermano del de Kuse, Grimorio de Salud y Crónicas del Apetito, con
una diferencia importante: esta app NO tiene auto-actualización propia, así que aquí
no hay manifiesto ni GitHub Pages. Quien instala y actualiza es DracApps, y lo único
que la tienda necesita es que exista la Release y que el repo esté en su apps.yaml).

Pasos, en este orden y sin saltarse ninguno:

1. Árbol limpio: sin esto el tag de la Release apunta a un commit que no contiene el
   código publicado. Ya pasó de verdad en otra app de la casa.
2. Lectura del versionCode/versionName (fuente única: app/build.gradle.kts) y de las
   notas de la versión (fuente única: CHANGELOG.md, que es lo que la tienda enseña).
3. Build del APK de release FIRMADO con la keystore de verdad.
4. Verificación de coherencia: el versionCode que trae el APK construido (leído con
   aapt2, o con apkanalyzer si aapt2 no está) tiene que coincidir con el declarado y
   superar al que la tienda sirve hoy; la firma tiene que ser de release, nunca la de
   debug, y la misma de las versiones ya distribuidas.
5. Release en GitHub con el APK como asset (gh CLI, comprobando antes gh auth status).
6. Alta —o comprobación— del repo en el apps.yaml de DracApps, que es lo que hace que
   la app aparezca en la tienda.

Secretos: la firma sale de keystore.properties (en la raíz, gitignored) o de las
variables de entorno DRACPDF_STORE_FILE / DRACPDF_STORE_PASSWORD / DRACPDF_KEY_ALIAS /
DRACPDF_KEY_PASSWORD, que el propio build.gradle.kts ya sabe leer. Si no hay ninguna
de las dos fuentes, aborta. Ningún secreto se escribe en el repositorio.

Uso:
    python scripts/publicar_release.py                   # construye y publica
    python scripts/publicar_release.py --dry-run         # prepara sin publicar
    python scripts/publicar_release.py --notas "…"       # notas a mano (si no, CHANGELOG)
    python scripts/publicar_release.py --dry-run --apk X # ensaya sobre un APK ya hecho
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

# La consola de Windows viene en cp1252 y se comería los acentos de los mensajes.
for _flujo in (sys.stdout, sys.stderr):
    if hasattr(_flujo, "reconfigure"):
        _flujo.reconfigure(encoding="utf-8", errors="replace")

RAIZ = Path(__file__).resolve().parents[1]
BUILD_GRADLE = RAIZ / "app" / "build.gradle.kts"
CHANGELOG = RAIZ / "CHANGELOG.md"
FIRMA_ESPERADA = RAIZ / "scripts" / "firma_esperada.txt"
KEYSTORE_PROPS = RAIZ / "keystore.properties"
APK_RELEASE = RAIZ / "app" / "build" / "outputs" / "apk" / "release" / "app-release.apk"
APK_SIN_FIRMAR = APK_RELEASE.with_name("app-release-unsigned.apk")

_REPO = "marcmayol/DracPDF-Android"
_NOMBRE = "DracPDF"
_ENV_FIRMA = (
    "DRACPDF_STORE_FILE",
    "DRACPDF_STORE_PASSWORD",
    "DRACPDF_KEY_ALIAS",
    "DRACPDF_KEY_PASSWORD",
)

# La tienda. El apps.yaml es lo único que se edita a mano allí; el catálogo publicado
# se regenera solo cada 6 h con un cron de GitHub Actions, así que basta con que la
# Release exista y el repo esté dado de alta.
_DRACAPPS = Path(os.environ.get("DRACPDF_DRACAPPS", Path.home() / "Desktop" / "DracApps"))
_CATALOGO_PUBLICO = "https://marcmayol.com/DracApps/catalogo.json"
_APPLICATION_ID = "com.marcmayol.dracpdf"

# La entrada del catálogo: solo el repo. Nombre, descripción, icono, tamaño, hash y
# notas los saca DracApps del propio APK y de la Release, así que escribir aquí más
# campos sería duplicar datos que envejecen mal.
_COMENTARIO_ALTA = "Visor de PDF con formularios, firma dibujada y firma digital PAdES."

# DN del debug.keystore que instala el SDK: si el APK sale firmado con esto, la
# keystore de release no se cargó y publicarlo dejaría a todo el mundo sin poder
# actualizar (y a cualquiera capaz de firmar una "actualización" suya).
_DN_DEBUG = "CN=Android Debug"


# --- utilidades ---------------------------------------------------------------

def _ejecutar(cmd: list[str], **kw) -> None:
    print("»", " ".join(cmd))
    if subprocess.call(cmd, cwd=str(RAIZ), **kw) != 0:
        raise SystemExit(f"Falló: {' '.join(cmd)}")


def _salida(cmd: list[str]) -> str:
    return subprocess.run(cmd, cwd=str(RAIZ), capture_output=True, text=True).stdout


def _codigo(cmd: list[str]) -> int:
    return subprocess.run(cmd, cwd=str(RAIZ), capture_output=True, text=True).returncode


def _aviso(texto: str) -> None:
    print(f"Aviso: {texto}")


def sha256(ruta: Path) -> str:
    h = hashlib.sha256()
    with ruta.open("rb") as f:
        for bloque in iter(lambda: f.read(65536), b""):
            h.update(bloque)
    return h.hexdigest()


def _gradlew() -> str:
    return "gradlew.bat" if os.name == "nt" else "./gradlew"


# El único fichero que el propio script escribe: no cuenta como "cambio suelto".
_ARCHIVOS_DEL_SCRIPT = {"scripts/firma_esperada.txt"}


def cambios_pendientes() -> list[str]:
    """Ficheros con cambios sin commitear, salvo los que este script escribe."""
    pendientes = []
    for linea in _salida(["git", "status", "--porcelain"]).splitlines():
        if not linea.strip():
            continue
        ruta = linea[3:].strip().strip('"')
        if " -> " in ruta:  # renombrados: "origen -> destino"
            ruta = ruta.split(" -> ", 1)[1]
        if ruta.rstrip("/") in _ARCHIVOS_DEL_SCRIPT:
            continue
        pendientes.append(ruta)
    return pendientes


def asegurar_arbol_limpio(estricto: bool = True) -> None:
    """Aborta si queda código sin commitear.

    El tag de la Release se crea sobre lo que hay en GitHub, así que publicar con
    cambios en el working tree deja un tag que NO contiene el código de esa versión:
    el APK lleva la funcionalidad y el repositorio no. Le pasó de verdad con la v2.3
    de Kuse, y es la razón de que esta comprobación no sea opcional.
    """
    pendientes = cambios_pendientes()
    if not pendientes:
        print("Árbol limpio.")
        return

    lista = "\n".join(f"    {r}" for r in pendientes[:20])
    resto = f"\n    …y {len(pendientes) - 20} más" if len(pendientes) > 20 else ""
    if not estricto:
        _aviso(
            f"hay {len(pendientes)} ficheros sin commitear; en una publicación de "
            "verdad esto aborta.\n"
            f"{lista}{resto}"
        )
        return
    raise SystemExit(
        "Hay cambios sin commitear: la Release quedaría etiquetada sobre un commit "
        "que no contiene este código.\n"
        f"{lista}{resto}\n"
        "  Commitea (o guarda en stash) y vuelve a publicar. Aborto."
    )


# --- versión y notas (fuentes únicas) -----------------------------------------

def leer_version() -> tuple[int, str]:
    """versionCode y versionName, tal como los declara app/build.gradle.kts."""
    texto = BUILD_GRADLE.read_text(encoding="utf-8")
    vc = re.search(r"versionCode\s*=\s*(\d+)", texto)
    vn = re.search(r'versionName\s*=\s*"([^"]+)"', texto)
    if not vc or not vn:
        raise SystemExit("No se pudo leer versionCode/versionName de app/build.gradle.kts.")
    return int(vc.group(1)), vn.group(1)


def leer_notas_del_changelog(vn: str) -> str | None:
    """El apartado de esta versión en CHANGELOG.md, que es lo que la tienda enseña.

    DracApps toma como notas el cuerpo de la Release, y el cuerpo de la Release sale
    de aquí: así lo que lee quien actualiza desde la tienda y lo que queda escrito en
    el repositorio son literalmente el mismo texto, sin dos versiones que se separan.
    """
    if not CHANGELOG.is_file():
        return None
    cabecera = re.compile(rf"^##\s+v?{re.escape(vn)}(\s|$)")
    lineas = CHANGELOG.read_text(encoding="utf-8").splitlines()
    cuerpo: list[str] = []
    dentro = False
    for linea in lineas:
        if linea.startswith("## "):
            if dentro:
                break
            dentro = bool(cabecera.match(linea))
            continue
        if dentro:
            cuerpo.append(linea)
    texto = "\n".join(cuerpo).strip()
    return texto or None


def notas_de_version(vn: str, notas_a_mano: str) -> str:
    """Las notas que irán en la Release: las de --notas mandan sobre el CHANGELOG."""
    if notas_a_mano.strip():
        return notas_a_mano.strip()
    notas = leer_notas_del_changelog(vn)
    if notas:
        return notas
    raise SystemExit(
        f"CHANGELOG.md no tiene ningún apartado '## v{vn}' con contenido.\n"
        "  Esas líneas son las notas que DracApps enseña a quien actualiza: escríbelas\n"
        "  antes de publicar, o pásalas con --notas si es una versión sin nada que contar."
    )


# --- firma --------------------------------------------------------------------

def hay_credenciales_de_firma() -> bool:
    return KEYSTORE_PROPS.exists() or all(os.environ.get(k) for k in _ENV_FIRMA)


def asegurar_firma(estricto: bool = True) -> bool:
    """Comprueba que hay credenciales de firma antes de construir.

    No materializa nada: build.gradle.kts ya lee keystore.properties o las variables
    de entorno por sí mismo, así que aquí solo se verifica que exista una de las dos
    fuentes. Así ningún secreto pasa por un fichero temporal.
    """
    if KEYSTORE_PROPS.exists():
        comprobar_keystore_props_sin_bom()
        print("Firma: keystore.properties encontrado.")
        return True
    faltan = [k for k in _ENV_FIRMA if not os.environ.get(k)]
    if not faltan:
        print("Firma: usando variables de entorno.")
        return True

    mensaje = (
        "Faltan credenciales de firma de DracPDF.\n"
        "  Opción A: crea keystore.properties en la raíz (está gitignored) con\n"
        "            storeFile, storePassword, keyAlias y keyPassword.\n"
        f"  Opción B: define las variables de entorno: {', '.join(_ENV_FIRMA)}.\n"
        f"  Ahora mismo faltan: {', '.join(faltan)}."
    )
    if estricto:
        raise SystemExit(mensaje)
    _aviso(mensaje)
    return False


def comprobar_keystore_props_sin_bom() -> None:
    """Un BOM al principio del fichero deja el APK firmado en debug sin decir nada.

    Properties.load lee el fichero como ISO-8859-1, así que un UTF-8 con BOM convierte
    la primera clave en "<BOM>storeFile" y Gradle no la encuentra: se compila sin
    firma de release y el resultado no sirve. PowerShell escribe BOM por defecto con
    `Set-Content -Encoding utf8`, que es exactamente como se cuela.
    """
    if KEYSTORE_PROPS.read_bytes().startswith(b"\xef\xbb\xbf"):
        raise SystemExit(
            "keystore.properties empieza con un BOM y Gradle no verá la primera clave: "
            "el APK saldría sin firmar.\n"
            "  Vuelve a escribirlo en UTF-8 SIN BOM (en PowerShell, con "
            "[System.IO.File]::WriteAllText y UTF8Encoding($false)). Aborto."
        )


# --- herramientas del SDK (verificación de coherencia) ------------------------

def _sdk_dir() -> Path:
    local = RAIZ / "local.properties"
    if local.exists():
        m = re.search(r"sdk\.dir=(.+)", local.read_text(encoding="utf-8"))
        if m:
            return Path(m.group(1).strip().replace("\\\\", "\\").replace("\\:", ":"))
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(env):
            return Path(os.environ[env])
    raise SystemExit("No encuentro el Android SDK (local.properties o ANDROID_HOME).")


def _build_tool(nombre: str) -> Path | None:
    """Ruta a una herramienta de build-tools, la de versión más alta disponible."""
    for patron in (f"*/{nombre}.exe", f"*/{nombre}.bat", f"*/{nombre}"):
        candidatos = sorted((_sdk_dir() / "build-tools").glob(patron), reverse=True)
        if candidatos:
            return candidatos[0]
    return None


def _apkanalyzer() -> Path | None:
    """El plan B para leer el manifiesto: vive en cmdline-tools, no en build-tools."""
    for sufijo in (".bat", ""):
        ruta = _sdk_dir() / "cmdline-tools" / "latest" / "bin" / f"apkanalyzer{sufijo}"
        if ruta.is_file():
            return ruta
    return None


def version_code_del_apk(apk: Path) -> int:
    """Lo que el APK dice de sí mismo, que es lo único que se instala en el móvil."""
    aapt2 = _build_tool("aapt2")
    if aapt2 is not None:
        m = re.search(r"versionCode='(\d+)'", _salida([str(aapt2), "dump", "badging", str(apk)]))
        if m:
            return int(m.group(1))
    analyzer = _apkanalyzer()
    if analyzer is not None:
        m = re.search(r"\d+", _salida([str(analyzer), "manifest", "version-code", str(apk)]))
        if m:
            return int(m.group(0))
    raise SystemExit(
        "No pude leer el versionCode del APK: ni aapt2 (build-tools) ni apkanalyzer "
        "(cmdline-tools) contestaron. Instala uno de los dos desde el SDK Manager."
    )


def _certificados(apk: Path) -> str:
    apksigner = _build_tool("apksigner")
    if apksigner is None:
        return ""
    return _salida([str(apksigner), "verify", "--print-certs", str(apk)])


def huella_firma(apk: Path) -> str | None:
    """SHA-256 del certificado de firma del APK, o None si apksigner no está."""
    m = re.search(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", _certificados(apk))
    return m.group(1).lower() if m else None


def firmado_con_debug(apk: Path) -> bool:
    return _DN_DEBUG.lower() in _certificados(apk).lower()


# --- lo que la tienda sirve hoy -----------------------------------------------

def version_code_publicado() -> int | None:
    """versionCode que DracApps sirve hoy para esta app, o None si aún no está.

    Primero la URL pública, que es lo que ven los móviles de verdad; si no hay red, el
    catálogo generado del repositorio local de la tienda. Si no se puede saber, se
    devuelve None y se avisa: es preferible publicar sin esta comprobación que abortar
    por estar sin conexión.
    """
    for origen, cargar in (
        (_CATALOGO_PUBLICO, _catalogo_de_la_url),
        (str(_DRACAPPS / "docs" / "catalogo.json"), _catalogo_del_disco),
    ):
        try:
            catalogo = cargar()
        except Exception as e:  # noqa: BLE001
            _aviso(f"no pude leer {origen} ({e.__class__.__name__}).")
            continue
        if catalogo is None:
            continue
        for app in catalogo.get("apps", []):
            if app.get("id") == _APPLICATION_ID:
                return int(app["versionCode"])
        return None  # el catálogo se leyó y la app todavía no está: primera vez
    _aviso("no he podido comprobar qué versión sirve la tienda ahora mismo.")
    return None


def _catalogo_de_la_url() -> dict | None:
    with urllib.request.urlopen(_CATALOGO_PUBLICO, timeout=15) as r:
        return json.loads(r.read().decode("utf-8"))


def _catalogo_del_disco() -> dict | None:
    ruta = _DRACAPPS / "docs" / "catalogo.json"
    if not ruta.is_file():
        return None
    return json.loads(ruta.read_text(encoding="utf-8"))


# --- coherencia ----------------------------------------------------------------

def verificar_coherencia(vc_declarado: int, vn: str, apk: Path) -> None:
    """Cinturón y tirantes antes de publicar.

    El versionCode del APK construido y el declarado coinciden; la versión sube
    respecto a la que sirve la tienda; y la firma es de release y sigue siendo la
    misma de siempre (si cambia, ninguna instalación existente puede actualizarse:
    Android rechaza la actualización y hay que desinstalar, perdiendo los datos).
    """
    vc_apk = version_code_del_apk(apk)
    if vc_apk != vc_declarado:
        raise SystemExit(
            f"El APK construido tiene versionCode {vc_apk}, pero build.gradle.kts "
            f"declara {vc_declarado}. Aborto."
        )
    print(f"versionCode del APK: {vc_apk} (coincide con el declarado).")

    publicado = version_code_publicado()
    if publicado is None:
        print("La tienda todavía no publica esta app: será su primera versión.")
    elif vc_declarado <= publicado:
        raise SystemExit(
            f"El versionCode {vc_declarado} no supera al que sirve DracApps "
            f"({publicado}): la tienda no ofrecería la actualización. Sube el "
            "versionCode. Aborto."
        )
    else:
        print(f"La tienda sirve hoy el versionCode {publicado}; esta sube a {vc_declarado}.")

    if firmado_con_debug(apk):
        raise SystemExit(
            "El APK está firmado con el debug.keystore: la keystore de release no se "
            "cargó. Revisa keystore.properties (¿BOM?) o las variables de entorno. Aborto."
        )

    huella = huella_firma(apk)
    if huella is None:
        raise SystemExit(
            "El APK no tiene firma legible: apksigner no encuentra ningún certificado. "
            "Un APK sin firmar no se instala en ningún móvil. Aborto."
        )
    if FIRMA_ESPERADA.is_file():
        esperada = FIRMA_ESPERADA.read_text(encoding="utf-8").strip().lower()
        if esperada and esperada != huella:
            raise SystemExit(
                "La firma del APK ha cambiado respecto a la de las versiones ya "
                f"distribuidas ({esperada[:16]}… → {huella[:16]}…). Con otra firma, "
                "ninguna instalación existente puede actualizarse. Aborto."
            )
        print(f"Firma verificada: {huella[:16]}…")
    else:
        FIRMA_ESPERADA.write_text(huella + "\n", encoding="utf-8", newline="\n")
        print(f"Firma registrada por primera vez en {FIRMA_ESPERADA.name}: {huella[:16]}…")
    print(f"sha256 del APK: {sha256(apk)[:12]}… ({apk.stat().st_size} bytes)")
    print(f"Asset que se subirá: dracpdf-v{vn}.apk")


# --- construcción -------------------------------------------------------------

def construir() -> Path:
    asegurar_firma()
    _ejecutar([_gradlew(), ":app:assembleRelease"])
    if not APK_RELEASE.is_file():
        pista = (
            " Se generó app-release-unsigned.apk, o sea que Gradle no cargó la keystore."
            if APK_SIN_FIRMAR.is_file()
            else ""
        )
        raise SystemExit(f"No se generó el APK de release: {APK_RELEASE}.{pista}")
    return APK_RELEASE


# --- alta en la tienda ----------------------------------------------------------

def _linea_de_alta() -> str:
    return f"  - repo: {_REPO}"


def _bloque_de_alta() -> str:
    return f"\n  # {_COMENTARIO_ALTA}\n{_linea_de_alta()}\n"


def alta_en_dracapps(dry_run: bool) -> None:
    """Da de alta el repo en el apps.yaml de la tienda, o comprueba que ya está.

    Es el único paso que toca otro repositorio, y toca lo mínimo: una línea con el
    repo. Nombre, icono, versión, tamaño y hash los saca DracApps del propio APK de la
    última Release, así que aquí no se escribe nada que pueda quedarse desfasado. El
    catálogo publicado lo regenera su cron cada 6 h; este script no lo genera ni lo
    sube, para no pisarle el turno a la tienda.
    """
    apps_yaml = _DRACAPPS / "apps.yaml"
    if not apps_yaml.is_file():
        _aviso(
            f"no encuentro el apps.yaml de DracApps en {apps_yaml}.\n"
            "  Si la tienda está en otro sitio, dilo con la variable de entorno "
            "DRACPDF_DRACAPPS.\n"
            "  Mientras tanto, la app no saldrá en el catálogo hasta que añadas esto "
            f"a mano:\n{_bloque_de_alta().rstrip()}"
        )
        return

    texto = apps_yaml.read_text(encoding="utf-8")
    if re.search(rf"^\s*-\s*repo:\s*{re.escape(_REPO)}\s*$", texto, re.MULTILINE | re.IGNORECASE):
        print(f"DracApps: {_REPO} ya está de alta en {apps_yaml.name}.")
        return

    if dry_run:
        print(f"DracApps: {_REPO} NO está de alta; se añadiría a {apps_yaml}:")
        print("\n".join(f"    {l}" for l in _bloque_de_alta().strip("\n").splitlines()))
        return

    nuevo = _insertar_alta(texto)
    _validar_yaml(nuevo, apps_yaml)
    apps_yaml.write_text(nuevo, encoding="utf-8", newline="\n")
    print(f"DracApps: {_REPO} dado de alta en {apps_yaml}.")
    print("  Queda sin commitear a propósito: el alta la revisas y la subes tú en la tienda.")


def _insertar_alta(texto: str) -> str:
    """Mete el alta al final de la lista 'apps', respetando los comentarios.

    Se edita el texto y no el YAML deserializado porque apps.yaml está lleno de
    comentarios que explican cada app y por qué alguna lleva un patrón de APK; volcarlo
    con yaml.dump los borraría todos de un plumazo.
    """
    lineas = texto.splitlines()
    ultima = None
    for i, linea in enumerate(lineas):
        if re.match(r"^\s*-\s*repo:\s*\S+", linea):
            ultima = i
    if ultima is None:
        raise SystemExit(
            "El apps.yaml de DracApps no tiene ninguna entrada '- repo:' donde colgar "
            "esta. Añádela a mano y vuelve a intentarlo."
        )

    # Las claves opcionales de una app (apk:, nombre:…) van debajo de su '- repo:':
    # el alta nueva tiene que ir después de todas ellas, no en medio.
    fin = ultima + 1
    while fin < len(lineas) and re.match(r"^\s+[a-z_]+:", lineas[fin]):
        fin += 1

    bloque = _bloque_de_alta().strip("\n").splitlines()
    return "\n".join(lineas[:fin] + [""] + bloque + lineas[fin:]) + "\n"


def _validar_yaml(texto: str, origen: Path) -> None:
    """Relee lo que se va a escribir: mejor descubrir aquí que se ha roto el fichero.

    PyYAML es lo que usa la propia tienda, pero este repo no lo necesita para nada más;
    si no está instalado se avisa y se sigue, porque generar_catalogo.py volverá a
    validarlo con su propio lector antes de publicar nada.
    """
    try:
        import yaml  # noqa: PLC0415
    except ImportError:
        _aviso("PyYAML no está instalado: no puedo releer el apps.yaml que voy a escribir.")
        return
    try:
        datos = yaml.safe_load(texto)
    except yaml.YAMLError as e:
        raise SystemExit(f"Al añadir el alta, {origen.name} deja de ser YAML válido: {e}") from e
    repos = [a.get("repo") for a in (datos or {}).get("apps", []) if isinstance(a, dict)]
    if _REPO not in repos:
        raise SystemExit(f"El alta no ha quedado dentro de la lista 'apps' de {origen.name}.")


# --- publicación --------------------------------------------------------------

def verificar_gh(estricto: bool = True) -> bool:
    if _codigo(["gh", "auth", "status"]) == 0:
        print("gh: autenticado.")
        return True
    if estricto:
        raise SystemExit("gh no está autenticado. Ejecuta: gh auth login")
    _aviso("gh no está autenticado (o no está instalado). Haría falta para publicar.")
    return False


def asegurar_tag_libre(vn: str, estricto: bool = True) -> None:
    """Aborta si ya existe una Release con esa etiqueta.

    gh también fallaría al crearla, pero lo haría a mitad del ritual y con un mensaje
    de la API; es mejor enterarse antes de construir nada.
    """
    if _codigo(["gh", "release", "view", f"v{vn}", "--repo", _REPO]) != 0:
        print(f"La etiqueta v{vn} está libre en {_REPO}.")
        return
    mensaje = (
        f"Ya existe la Release v{vn} en {_REPO}. Sube el versionName (y el versionCode) "
        "en app/build.gradle.kts, o borra esa Release si fue un error."
    )
    if estricto:
        raise SystemExit(mensaje + " Aborto.")
    _aviso(mensaje)


def _asset_con_nombre(apk: Path, vn: str) -> Path:
    """Copia el APK con el nombre que verá quien lo descargue de la Release."""
    destino = apk.with_name(f"dracpdf-v{vn}.apk")
    if destino != apk:
        destino.write_bytes(apk.read_bytes())
    return destino


def publicar(apk: Path, vn: str, notas: str) -> None:
    asset = _asset_con_nombre(apk, vn)
    _ejecutar([
        "gh", "release", "create", f"v{vn}", str(asset),
        "--repo", _REPO,
        "--title", f"{_NOMBRE} {vn}",
        "--notes", notas,
    ])
    # La firma se registra la primera vez y no vuelve a cambiar: se commitea para que
    # el guardarraíl valga también en el próximo portátil.
    if cambios_en(FIRMA_ESPERADA):
        _ejecutar(["git", "add", str(FIRMA_ESPERADA)])
        _ejecutar(["git", "commit", "-m", f"Registra la firma de release en la v{vn}"])
        _ejecutar(["git", "push", "origin", "main"])


def cambios_en(ruta: Path) -> bool:
    relativa = ruta.relative_to(RAIZ).as_posix()
    return bool(_salida(["git", "status", "--porcelain", "--", relativa]).strip())


# --- orquestación ---------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Publica una release de DracPDF Android y la deja lista para DracApps."
    )
    parser.add_argument("--dry-run", action="store_true", help="prepara sin publicar")
    parser.add_argument("--notas", default="", help="notas de la versión (si no, CHANGELOG.md)")
    parser.add_argument(
        "--apk",
        type=Path,
        default=None,
        help="ensaya las comprobaciones sobre un APK ya construido (solo con --dry-run)",
    )
    args = parser.parse_args(argv)

    if args.apk and not args.dry_run:
        raise SystemExit(
            "--apk solo vale con --dry-run: publicar un APK que este script no ha "
            "construido es la forma más fácil de subir una versión que no es la del "
            "repositorio. Aborto."
        )

    estricto = not args.dry_run
    vc, vn = leer_version()
    notas = notas_de_version(vn, args.notas)
    print(f"{_NOMBRE} v{vn} (versionCode {vc})")
    print(f"Notas ({'--notas' if args.notas.strip() else 'CHANGELOG.md'}):")
    print("\n".join(f"    {l}" for l in notas.splitlines()))
    print()

    asegurar_arbol_limpio(estricto)
    verificar_gh(estricto)
    asegurar_tag_libre(vn, estricto)
    print()

    apk = _obtener_apk(args, estricto)
    if apk is not None:
        verificar_coherencia(vc, vn, apk)
        print(f"APK: {apk}")
    print()

    alta_en_dracapps(args.dry_run)

    if args.dry_run:
        print()
        print("--dry-run: no se ha publicado nada (ni Release, ni alta en la tienda).")
        if apk is None:
            print("  Sin APK no se han comprobado: versionCode real, firma ni sha256.")
        return 0

    publicar(apk, vn, notas)
    print()
    print(f"Release v{vn} publicada en https://github.com/{_REPO}/releases/tag/v{vn}.")
    print(
        "La tienda la recogerá sola en su próximo pase (cron cada 6 h). Para verla ya:\n"
        f"    cd {_DRACAPPS}\n"
        "    python scripts/generar_catalogo.py --dry-run\n"
        "    python scripts/generar_catalogo.py --publicar"
    )
    return 0


def _obtener_apk(args: argparse.Namespace, estricto: bool) -> Path | None:
    """El APK sobre el que se verifica todo: el que se construye, salvo en ensayos."""
    if args.apk is not None:
        apk = args.apk if args.apk.is_absolute() else (RAIZ / args.apk)
        if not apk.is_file():
            raise SystemExit(f"No existe el APK que has pasado con --apk: {apk}")
        _aviso(f"ensayo sobre un APK ya construido: {apk}")
        return apk
    if estricto or hay_credenciales_de_firma():
        return construir()
    asegurar_firma(estricto=False)
    _aviso("sin credenciales de firma no se construye nada: me salto el build.")
    return None


if __name__ == "__main__":
    raise SystemExit(main())
