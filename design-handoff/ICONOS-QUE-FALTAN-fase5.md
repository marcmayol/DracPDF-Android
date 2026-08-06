# Iconos que faltan · revisión de conformidad (Fase 5)

Segunda tanda, después de `ICONOS-QUE-FALTAN.md`. Salen de montar el **menú del ⋮
de la pantalla de inicio**, que la maqueta de la §19 ya dibuja desplegado con cuatro
entradas:

    ☀  Ajustes              (Fase 2)
    ◐  Tema                 (Fase 2)
    ⓘ  Ayuda                (Fase 2)
    🗎  Acerca de DracPDF    (Fase 1)

Dos tienen icono en el paquete —`ic_settings` para Ajustes y `ic_theme` para Tema, ya
implementadas— y **dos no lo tienen**. El menú se ha entregado sin ellas antes que
inventarles un glifo: la regla de la casa es que si la interfaz necesita un icono que
no está en el paquete, se pide.

Subido al proyecto de Claude Design como `uploads/ICONOS-QUE-FALTAN-fase5.md`.

---

## 1 · `ic_info` — «Ayuda»

La maqueta lo dibuja como una **(i) dentro de un círculo**.

Ninguno de los 56 significa eso. Los dos que más se le acercan ya tienen otro trabajo
dentro de esta misma aplicación, y usarlos aquí los rompería:

- `ic_properties` son **los datos del PDF** —tamaño, autor, número de páginas— y tiene
  su entrada propia en el ⋮ del visor, que ya está puesta.
- `ic_note` es una **anotación dentro de la página**, de la Fase 8.

Donde se usa: entrada «Ayuda» del menú del inicio.

## 2 · `ic_about` — «Acerca de DracPDF»

La maqueta lo dibuja como una **hoja de documento**.

Aquí la colisión es peor que en el anterior: en un lector de PDF una hoja suelta ya
significa *un documento*, que es justo lo que dicen `ic_documents` y `ic_pages_grid`.
Si «Acerca de» se queda con una hoja, el menú tendrá dos glifos parecidos con
significados que no se parecen en nada.

**Sugerencia, no decisión:** puede que este no necesite icono propio y le sirva mejor
la silueta del dragón de la marca, que es literalmente de lo que la pantalla habla.
Si es así, basta con decirlo y se usa `marca_dragon_*`, que ya está entregada — y esta
petición se queda en un solo icono.

---

## Encaje y entrega

Lo mismo que el resto del paquete, para que no se note la costura:

- Retícula 24, trazo 2, terminales redondeados, **un solo color** (`currentColor`).
- Se renderizan a 24 dp dentro de un área táctil de 48 dp.
- Entrega en `icons/android-vector/ic_*.xml` más el SVG fuente y las seis variantes
  `qt-tinted` (dark, light, accent, ember, muted, disabled), para que el DracPDF de
  escritorio los tenga el día que le toque.

## Contexto

Van con `PETICION-fase5.md`, que además pregunta dos cosas que no son iconos: el
color de `onPrimary` del botón primario en oscuro —el token dice tinta y la maqueta lo
dibuja blanco— y si la barra contextual por modo alcanza también al modo de colocar
una firma.
