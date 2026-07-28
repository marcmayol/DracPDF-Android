# Iconos que faltan · DracPDF Android

Cruce de los **53 iconos** del paquete de identidad Ladón contra las **diez fases**
del `PLAN.md`, no sólo contra la Fase 1.

El resultado corto: el catálogo cubre casi todo. Falta **uno seguro**, hay **una
pregunta de diseño** de la que dependen dos, y **tres menores** que dependen de cómo
se resuelva su pantalla.

---

## 1 · Falta seguro

| Icono | Fase | Para qué |
|---|---|---|
| **`ic_documents`** | 1 · tarea 6 | El selector de documentos abiertos. El registro admite varios PDF a la vez y un intent entrante añade sin machacar la sesión. Ninguno de los 53 significa eso: `duplicate` es copiar, `pages_grid` son las páginas de *un* documento y `recent` es el historial. |

Encaje: retícula 24, trazo 2, terminales redondeados, un solo color. Entrega en
`icons/android-vector/ic_documents.xml` más su SVG y las seis variantes `qt-tinted`,
para que el escritorio pueda usarlo el día que le toque.

---

## 2 · Una pregunta de diseño antes de pedir dos iconos

La Fase 8 pide **resaltar / subrayar / tachar** como tres acciones. El paquete trae
`highlight` y **no trae subrayar ni tachar**.

Pero pedir los dos iconos sería decidir el diseño por nuestra cuenta, porque la
maqueta apunta a otra cosa: en la barra de selección de texto, **«Resaltar» no se
dibuja con el icono `highlight`**, sino con una muestra de color ámbar. El marcado
está tratado como un estilo, no como un icono. Y esa barra ya tiene sus cuatro
huecos ocupados —Copiar · Resaltar · Nota · Todo—, así que subrayar y tachar no
caben como botones sueltos.

**La pregunta:** ¿cómo se ofrecen las tres formas de marcado en el móvil?

- ¿Una hoja de marcado con las tres, cada una con su muestra?
- ¿Un desplegable al mantener pulsado «Resaltar»?
- ¿Tres iconos y una barra de selección más larga?

De la respuesta sale si hacen falta `ic_underline` e `ic_strikethrough` o si no hace
falta ninguno.

---

## 3 · Menores, según cómo quede su pantalla

| Icono | Fase | Para qué | Cuándo haría falta |
|---|---|---|---|
| `ic_text_fix` | 8 · punto 3 | «Corregir texto». En el escritorio es una acción distinta de «Añadir texto» dentro del menú Edición, y `text_add` significa añadir. | Si en móvil es una acción con icono propio y no una entrada de menú. |
| `ic_table` | 9 · punto 3 | Tablas a CSV y XLSX. | Sólo si la hoja de conversión lista los formatos con icono en vez de con texto. |
| `ic_link` | 7 · punto 2 | Enlaces externos, que se abren con confirmación. | Probablemente nunca: los enlaces se pintan sobre el papel, no en una barra. |

---

## 4 · Tres que Android no usará (y no es un fallo)

`present` · `fullscreen` · `more_horiz`

El diseño dice explícitamente que «pantalla completa» **no se porta** —en móvil eso
es el modo inmersivo, que no tiene botón— y que el menú es siempre el ⋮ vertical. La
presentación no está en alcance en ninguna fase. Están en el paquete porque es el
mismo que usa el DracPDF de escritorio, donde sí se usan los tres.

---

## 5 · Lo que sí está cubierto

Para que conste que el resto del catálogo aguanta las diez fases:

- **Visor**: `arrow_back` `search` `more_vert` `bookmark` `goto_page` `zoom_in`
  `zoom_out` `fit_width` `fit_page` `page_single` `page_double` `page_prev`
  `page_next` `pages_grid` `recent` `theme` `settings` `properties`
- **Formularios y firma**: `form_fill` `sign_draw` `sign_cert` `verify` `save`
- **Herramientas**: `merge` `split` `compress` `lock` `unlock` `convert` `export`
  `extract` `duplicate` `trash` `rotate` `rotate_left` `print` `drag_handle` `check`
- **Texto y anotaciones**: `text_add` `highlight` `note` `image_add` `undo` `redo`
- **Selección**: `copy` `select_all` `share` `close` `open` `tools`

*(`convert` y `export` acabarán fundidos en una sola entrada: ver el punto 6 de
`PETICION-huecos-fase1.md`.)*
