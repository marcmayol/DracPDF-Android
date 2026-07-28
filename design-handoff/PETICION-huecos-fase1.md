# Petición a Claude Design · huecos detectados al implementar la Fase 1

Proyecto: **DracPDF Android** (`12145c22-8a62-4f15-bcb7-41d3448492c9`,
fichero `DracPDF Android.dc.html`). Ampliación de la identidad Ladón.

El paquete entregado cubre la Fase 1 casi entero: los 53 iconos como
VectorDrawable, las tres capas del icono adaptativo por densidad y la marca.
Al montar la Parte 0 y la tabla de acciones de la Fase 1 han aparecido cinco
huecos. No se han improvisado: se piden.

## 1 · Icono que falta

**«Documentos abiertos»** — la Fase 1 termina con un selector multi-documento
(el registro admite varios PDF abiertos a la vez, y un intent entrante añade sin
machacar la sesión). Ninguno de los 53 significa eso: `duplicate` es copiar,
`pages_grid` son las páginas de un documento y `recent` es el historial.

Encaje pedido: misma retícula 24, trazo 2, terminales redondeados, un color;
entrega en `icons/android-vector/ic_documents.xml` más su SVG y las seis
variantes `qt-tinted`, para que el escritorio pueda usarlo el día que le toque.

## 2 · Selector de documentos abiertos (pantalla)

La sección 15 dice que el menú Archivo del escritorio se reparte entre el ⋮ y la
pantalla de inicio, pero no dibuja dónde se ven ni se cierran los documentos que
ya están abiertos. Hace falta la maqueta: ¿hoja inferior con una fila por
documento y ✕ a la derecha? ¿pestañas bajo la barra superior? ¿la propia
pantalla de inicio con una sección «Abiertos» encima de «Recientes»?

## 3 · Contraseña de PDF cifrado

Un PDF cifrado pide contraseña antes de poder verse. El diseño no lo maqueta.
Propuesta a validar o sustituir: hoja inferior calcada de «Firmar con
certificado» —título, campo de contraseña de 48 dp con borde de acento, Cancelar
y Abrir— y el mensaje de error bajo el campo, no en un *toast*.

## 4 · Ir a página

La píldora «3 / 12» es su entrada natural y la sección 15 la nombra como hoja
inferior, pero no está dibujada: cuántos controles lleva (campo, teclado
numérico, ¿deslizador?), y qué pasa si el número está fuera de rango.

## 5 · Contenido del ⋮ de la pantalla de inicio

Está dibujado en la maqueta «Sin documento» pero nunca desplegado. En la Fase 1
sólo habría «Acerca de DracPDF»; conviene saber qué acaba viviendo ahí para no
tener que rehacerlo (¿Ajustes? ¿Tema? ¿Ayuda?).

## Fuera de la Fase 1, pero conviene saberlo ya

Nada más de lo entregado bloquea las fases siguientes: firma, verificación,
formularios, herramientas, búsqueda, organización de páginas y operaciones
largas están maquetadas y con sus iconos.
