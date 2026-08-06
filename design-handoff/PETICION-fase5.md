# Petición a Claude Design · conformidad visual (Fase 5)

Proyecto: **DracPDF Android** (`12145c22-8a62-4f15-bcb7-41d3448492c9`,
fichero `DracPDF Android.dc.html`). Ampliación de la identidad Ladón.

Al auditar pantalla a pantalla contra las maquetas de la §19 han salido tres
cosas que no se pueden resolver leyendo más el documento: una contradicción
entre las dos entregas, unos iconos que la maqueta usa y el paquete no trae, y
una regla cuyo alcance no está dibujado. No se improvisan: se preguntan.

## 1 · El botón primario: ¿tinta o blanco?

Las dos entregas dicen cosas distintas y la implementación ha seguido una de
ellas a ciegas:

- `export-android/Color.kt` fija `LadonDarkOnPrimary = #1A1D23` —tinta— y así
  está hecho: «Abrir PDF» sale con el texto casi negro sobre el coral `#E0534A`.
- La maqueta de la §19 dibuja ese mismo botón con el **texto blanco** sobre el
  mismo coral.

No es un matiz: son dos botones distintos a la vista. El contraste tampoco
empata —blanco sobre `#E0534A` da ≈3,3:1, y la tinta ≈6,6:1—, así que el token
es el que aguanta el criterio de accesibilidad y la maqueta el que se parece más
a la marca en el resto de superficies. **¿Cuál manda?** Si manda la maqueta,
hace falta el valor nuevo de `onPrimary` para el esquema oscuro; si manda el
token, conviene repintar el botón de la maqueta para que nadie más lo copie.

En claro no hay duda: `#A83228` con blanco encima, que es lo implementado.

## 2 · Iconos de «Ayuda» y «Acerca de»

El menú del ⋮ de la pantalla de inicio, ya desplegado en la maqueta, trae cuatro
entradas: Ajustes, Tema, Ayuda y Acerca de DracPDF. Las dos primeras tienen
icono en el paquete —`ic_settings`, `ic_theme`—; las otras dos están dibujadas
con una (i) dentro de un círculo y con una hoja de documento, y **ninguno de los
56 significa eso**: `properties` son los datos del PDF y `note` es una anotación
dentro de la página.

Se han quedado fuera del menú antes que inventarlos. Encaje pedido: retícula 24,
trazo 2, terminales redondeados, un solo color, entrega en
`icons/android-vector/` más el SVG y las seis variantes `qt-tinted`.

## 3 · Alcance de la barra contextual por modo

La §15 dice que la barra de un modo **sustituye** a la superior —✕ · título ·
acción de confirmación— y que nunca conviven dos. La §19 lo dibuja para el modo
de formulario, y así se ha implementado: dentro del formulario ya no hay
«atrás», hay ✕ · «Formulario» · «Guardar».

Lo que no está dibujado es qué pasa en el **modo de colocar una firma**, que hoy
conserva la barra del documento y pone sus dos controles abajo —Cancelar ·
«Arrastra la firma y ajústala» · Colocar—, junto al pulgar que arrastra. ¿La
regla lo alcanza y hay que subir sus controles, o la colocación es la excepción
por ser un modo que se hace con la mano sobre la página?

La misma pregunta valdrá para los modos de marcado de la Fase 8, así que
conviene una regla y no un caso.
