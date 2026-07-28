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

Requiere JDK 17 y el SDK de Android (compileSdk 36).

## Diseño

`design-handoff/` es la fuente canónica de la identidad: iconos, capas del icono
adaptativo y marca, tal como los entregó Claude Design. Los recursos de `res/`
salen de ahí por copia; no se redibujan ni se optimizan.

## Licencia

**Copyright (c) 2026 Marc Mayol.** Código visible con uso no comercial
permitido; ver [`LICENSE`](LICENSE).
