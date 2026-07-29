#!/usr/bin/env python3
"""Genera el certificado con el que se prueba la firma digital.

Un `.p12` es material criptográfico y **no se versiona**, ni siquiera uno de juguete:
un certificado en el repositorio acaba usado por error para firmar algo de verdad, y
además obliga a explicar en cada revisión por qué hay una clave privada dentro. Así
que se genera aquí, queda en una carpeta ignorada, y quien quiera correr los tests
que lo necesitan ejecuta esto una vez.

    python scripts/certificado_de_prueba.py

Usa `keytool`, que viene con el JDK que ya hace falta para compilar el proyecto. Se
eligió frente a OpenSSL porque OpenSSL no está garantizado en Windows y keytool sí:
si se puede compilar la aplicación, se puede generar el certificado.

**Este certificado no vale para nada real.** Es autofirmado, así que Adobe lo dará
por no confiable hasta que se añada a mano a los certificados de confianza —que es
justo el paso manual del criterio de la Fase 4—. Sirve para comprobar que la firma
está bien construida, no para firmar documentos que vayan a alguna parte.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path

DESTINO = Path(__file__).resolve().parent.parent / "fixtures-externos"
FICHERO = "certificado-de-prueba.p12"

ALIAS = "dracpdf-pruebas"
CONTRASENA = "dracpdf"
DIAS = 3650

# Nombre distinguido que aparecerá como firmante. Deliberadamente dice lo que es, para
# que nadie que lo vea en un PDF firmado se piense que es una identidad real.
DN = "CN=DracPDF Pruebas (NO VALIDO), OU=Pruebas automaticas, O=DracPDF, C=ES"


def keytool() -> str | None:
    """Busca keytool en el PATH y, si no está, en el JAVA_HOME de Gradle."""
    encontrado = shutil.which("keytool")
    if encontrado:
        return encontrado

    # Android Studio trae su propio JDK y no siempre está en el PATH.
    candidatos = [
        Path("C:/Program Files/Android/Android Studio/jbr/bin/keytool.exe"),
        Path("C:/Program Files/Java"),
        Path("/usr/bin/keytool"),
    ]
    for candidato in candidatos:
        if candidato.is_file():
            return str(candidato)
        if candidato.is_dir():
            for hallado in candidato.glob("*/bin/keytool*"):
                return str(hallado)
    return None


def generar(destino: Path, forzar: bool) -> int:
    if destino.exists() and not forzar:
        print(f"Ya existe {destino}. Con --forzar se rehace.")
        return 0

    herramienta = keytool()
    if herramienta is None:
        print("ERROR: no encuentro keytool. Viene con el JDK; añade su bin al PATH.")
        return 1

    destino.parent.mkdir(parents=True, exist_ok=True)
    if destino.exists():
        destino.unlink()

    orden = [
        herramienta,
        "-genkeypair",
        "-alias", ALIAS,
        "-keyalg", "RSA",
        "-keysize", "2048",
        "-sigalg", "SHA256withRSA",
        "-validity", str(DIAS),
        "-storetype", "PKCS12",
        "-keystore", str(destino),
        "-storepass", CONTRASENA,
        "-keypass", CONTRASENA,
        "-dname", DN,
        # Sin esto, keytool genera un certificado sin usos declarados y algunos
        # verificadores rechazan la firma por no poder comprobar para qué sirve.
        "-ext", "KeyUsage=digitalSignature,nonRepudiation",
        "-ext", "ExtendedKeyUsage=emailProtection",
    ]

    print(f"Generando {destino.name} con keytool...")
    resultado = subprocess.run(orden, capture_output=True, text=True)
    if resultado.returncode != 0:
        print("ERROR de keytool:")
        print(resultado.stdout)
        print(resultado.stderr)
        return 1

    print(f"Listo: {destino}")
    print(f"  alias:       {ALIAS}")
    print(f"  contraseña:  {CONTRASENA}")
    print(f"  caduca en:   {DIAS} días")
    print()
    print("Para que Adobe lo dé por válido hay que añadirlo a los certificados de")
    print("confianza a mano. Sin eso dirá «la validez es desconocida», que es lo")
    print("correcto: es un certificado autofirmado.")
    return 0


def main() -> int:
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("--forzar", action="store_true", help="lo rehace aunque ya exista")
    opciones = argumentos.parse_args()
    return generar(DESTINO / FICHERO, opciones.forzar)


if __name__ == "__main__":
    sys.exit(main())
