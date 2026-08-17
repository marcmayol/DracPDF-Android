# DracPDF Android

Port nativo de [DracPDF](https://github.com/marcmayol/DracPDF) a Android: visor
de PDF con formularios AcroForm, firma dibujada, firma digital PAdES, caja de
herramientas y conversiones, en **Kotlin + Jetpack Compose**.

Identidad visual **Ladón**, la misma del escritorio, traducida a táctil por el
diseño «DracPDF Android» de Claude Design. Regla que gobierna la interfaz: *el
documento es la pantalla*; el chrome es un invitado que se va solo.

Se distribuye por **[DracApps](https://marcmayol.com/DracApps/)**, la tienda de
la casa. Esta app no lleva sistema de auto-actualización propio.

## Estado

Fase 1 (visor) en curso. El detalle de fases y criterios de aceptación está en
[`PLAN.md`](PLAN.md).

## Arquitectura

```
dominio/       Kotlin puro (módulo JVM): modelo, puertos y casos de uso.
               No puede importar Android: el SDK no está en su classpath.
adaptadores/   MuPDF, Storage Access Framework, almacenamiento local.
ui/            Compose y tema Ladón.
app/           Manifiesto, intents y ensamblado.
```

Motor PDF: **MuPDF** (`com.artifex.mupdf:fitz`), el mismo motor C que PyMuPDF usa
en el escritorio y en la misma versión, para que el render, los formularios y el
texto estructurado se comporten igual en las dos aplicaciones.

## Desarrollo

```bash
./gradlew verificar          # ktlint + detekt + tests JVM
./gradlew :app:assembleDebug # APK de depuración
./gradlew connectedCheck     # tests instrumentados (necesita emulador o móvil)
```

Requiere JDK 17 y el SDK de Android (compileSdk 35).

## Diseño

`design-handoff/` es la fuente canónica de la identidad: iconos, capas del icono
adaptativo y marca, tal como los entregó Claude Design. Los recursos de `res/`
salen de ahí por copia; no se redibujan ni se optimizan.

## Publicación

Se publica con `scripts/publicar_release.py`, el ritual de la casa. La app no tiene
auto-actualización propia: quien instala y actualiza es
[DracApps](https://marcmayol.com/DracApps/), que lee la **última Release de este
repositorio**. Por eso publicar es exactamente eso, crear la Release; el catálogo se
regenera solo cada 6 h con el cron de la tienda.

```bash
python scripts/publicar_release.py --dry-run   # ensayo: construye y comprueba, no publica
python scripts/publicar_release.py             # publica de verdad
```

### Antes de la primera vez

1. **Keystore de release**. Es la identidad de la app para siempre: si se pierde, no
   hay forma de publicar una actualización que se instale encima de la anterior.

   ```bash
   keytool -genkeypair -v -keystore C:/Users/<tú>/dracpdf-release.jks \
       -alias dracpdf -keyalg RSA -keysize 4096 -validity 10000
   ```

2. **`keystore.properties` en la raíz** (está gitignored; nunca se commitea):

   ```properties
   storeFile=C:/Users/<tú>/dracpdf-release.jks
   storePassword=…
   keyAlias=dracpdf
   keyPassword=…
   ```

   **Escríbelo en UTF-8 sin BOM.** `Properties.load` lee el fichero como ISO-8859-1 y
   un BOM convierte la primera clave en `<BOM>storeFile`: Gradle no la encuentra, se
   compila sin firma y el APK no sirve. `Set-Content -Encoding utf8` de PowerShell
   escribe BOM; usa `[System.IO.File]::WriteAllText($ruta, $texto, (New-Object
   System.Text.UTF8Encoding($false)))`. El script comprueba el BOM y aborta si lo ve.

   Alternativa sin fichero: las variables de entorno `DRACPDF_STORE_FILE`,
   `DRACPDF_STORE_PASSWORD`, `DRACPDF_KEY_ALIAS` y `DRACPDF_KEY_PASSWORD`, que
   `app/build.gradle.kts` también lee.

3. **`gh` autenticado** (`gh auth login`) con permiso de escritura en el repo.
4. **JDK 17 y el SDK de Android**, con `aapt2` y `apksigner` en `build-tools`
   (`apkanalyzer` de `cmdline-tools` vale como plan B para leer el versionCode).

### Cada versión

1. Sube `versionCode` (de uno en uno) y `versionName` en `app/build.gradle.kts`: son
   la fuente única de la versión.
2. Escribe el apartado `## v<versionName>` en [`CHANGELOG.md`](CHANGELOG.md) con la
   fecha. Ese texto es literalmente el cuerpo de la Release y lo que DracApps enseña
   a quien actualiza; sin él, el script aborta.
3. Commitea y sube todo. El árbol tiene que estar limpio.
4. Ensaya con `--dry-run`, y si está en verde, publica.

### Qué comprueba el script antes de publicar

- Que no queda **nada sin commitear**: si no, el tag apuntaría a un commit que no
  contiene el código publicado.
- Que la **etiqueta `v<versionName>` está libre** en GitHub.
- Que hay **credenciales de firma** y que `keystore.properties` no lleva BOM.
- Que el **versionCode del APK construido** (leído con `aapt2`, o `apkanalyzer`)
  coincide con el declarado y **supera al que sirve la tienda** ahora mismo.
- Que la firma **no es la de debug** y es **la misma de siempre**, comparándola con
  `scripts/firma_esperada.txt` (se registra sola en la primera publicación).
- Que el repo está **dado de alta en el `apps.yaml` de DracApps**; si no lo está, lo
  añade, y con `--dry-run` solo enseña la línea que añadiría.

### Qué NO hace

- **No sube el versionCode ni escribe el CHANGELOG.** Eso lo decides tú.
- **No commitea el código.** Solo commitea `scripts/firma_esperada.txt` la primera
  vez que registra la firma.
- **No genera ni publica el catálogo de DracApps**, ni commitea nada en el
  repositorio de la tienda: deja el alta escrita en su `apps.yaml` para que la
  revises. El catálogo lo regenera el cron de la tienda cada 6 h, o tú a mano con
  `python scripts/generar_catalogo.py --publicar` desde `DracApps`.
- **No sube a Google Play** ni a ningún otro origen: la distribución es DracApps.
- **No instala nada en el móvil.** La primera instalación y la actualización se
  prueban desde la tienda, en el dispositivo.

Si la tienda no está en `~/Desktop/DracApps`, dilo con la variable de entorno
`DRACPDF_DRACAPPS`.

## Licencia

**Copyright (c) 2026 Marc Mayol.** Código visible con uso no comercial
permitido; ver [`LICENSE`](LICENSE).
