#!/usr/bin/env python3
"""Descarga los formularios oficiales con los que se prueba la Fase 2.

La regla de la casa dice que los fixtures se generan por script y no se versionan
como binarios. Estos no se pueden generar: son documentos publicados por terceros,
y su valor está precisamente en que nadie de este repositorio los ha tocado. Así que
se descargan, se comprueba su huella, y quedan en una carpeta ignorada por git.

Los tests que dependen de ellos se saltan solos si no están, con un aviso que dice
cómo conseguirlos. Nadie se queda con la suite en rojo por no haber ejecutado esto.

    python scripts/descargar_formularios_oficiales.py
    python scripts/descargar_formularios_oficiales.py --listar

Por qué estos y no otros: el fixture que escribe MuPDF es limpio y previsible, que
es justo lo que un formulario de verdad no es. El W-9 trae nombres jerárquicos de
tres niveles, XFA por encima de su AcroForm y orden de tabulación estructural; sobre
él se descubrió que contar campos mirando el `/Subtype` daba 1 donde hay 23.
"""

from __future__ import annotations

import argparse
import hashlib
import sys
import urllib.request
from dataclasses import dataclass
from pathlib import Path

# Los fixtures viven fuera del control de versiones, junto al resto de lo que
# genera la compilación.
DESTINO = Path(__file__).resolve().parent.parent / "fixtures-externos"

TIEMPO_MAXIMO_S = 60


@dataclass(frozen=True)
class Formulario:
    nombre: str
    url: str
    sha256: str
    descripcion: str


FORMULARIOS = (
    Formulario(
        nombre="w9.pdf",
        url="https://www.irs.gov/pub/irs-pdf/fw9.pdf",
        sha256="2d420cbb4123dcf1fb82595b2359cfbb5d81f00b9df9d359fcc7af361d093f53",
        descripcion=(
            "W-9 del IRS (revisión de marzo de 2024). AcroForm real con nombres "
            "jerárquicos («topmostSubform[0].Page1[0].f1_01[0]»), 23 campos en la "
            "primera página, XFA híbrido y /Tabs /S."
        ),
    ),
)


def huella(fichero: Path) -> str:
    resumen = hashlib.sha256()
    with fichero.open("rb") as f:
        for trozo in iter(lambda: f.read(1 << 16), b""):
            resumen.update(trozo)
    return resumen.hexdigest()


def descargar(formulario: Formulario) -> bool:
    """Devuelve True si el fichero queda disponible y con la huella esperada."""
    destino = DESTINO / formulario.nombre

    if destino.exists():
        if huella(destino) == formulario.sha256:
            print(f"  ya estaba y coincide: {destino.name}")
            return True
        print(f"  {destino.name} está pero no coincide; se vuelve a bajar")

    print(f"  bajando {formulario.url}")
    try:
        peticion = urllib.request.Request(
            formulario.url,
            # Algunos servidores públicos rechazan al cliente de urllib sin más.
            headers={"User-Agent": "DracPDF-Android/fixtures"},
        )
        with urllib.request.urlopen(peticion, timeout=TIEMPO_MAXIMO_S) as respuesta:
            contenido = respuesta.read()
    except OSError as fallo:
        print(f"  ERROR: no se ha podido descargar: {fallo}")
        return False

    real = hashlib.sha256(contenido).hexdigest()
    if real != formulario.sha256:
        # No se guarda: un fixture distinto del esperado haría fallar los tests por
        # un motivo que no tiene nada que ver con el código, y encima en silencio.
        print(f"  ERROR: la huella no coincide.\n    esperada: {formulario.sha256}\n    obtenida: {real}")
        print("  El emisor puede haber publicado una revisión nueva. Compruébalo y,")
        print("  si el documento es legítimo, actualiza el sha256 de este script.")
        return False

    destino.write_bytes(contenido)
    print(f"  guardado en {destino}")
    return True


def listar() -> None:
    for formulario in FORMULARIOS:
        estado = "descargado" if (DESTINO / formulario.nombre).exists() else "falta"
        print(f"{formulario.nombre} [{estado}]")
        print(f"  {formulario.url}")
        print(f"  {formulario.descripcion}")


def main() -> int:
    argumentos = argparse.ArgumentParser(description=__doc__)
    argumentos.add_argument("--listar", action="store_true", help="sólo dice cuáles hay y si están")
    opciones = argumentos.parse_args()

    if opciones.listar:
        listar()
        return 0

    DESTINO.mkdir(parents=True, exist_ok=True)
    print(f"Formularios oficiales en {DESTINO}")

    fallos = 0
    for formulario in FORMULARIOS:
        print(f"\n{formulario.nombre}: {formulario.descripcion}")
        if not descargar(formulario):
            fallos += 1

    print()
    if fallos:
        print(f"{fallos} de {len(FORMULARIOS)} no se han podido preparar.")
        return 1

    print("Listo. Los tests que los usan ya no se saltarán.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
