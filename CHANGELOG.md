# Cambios

Lo que cambia en cada versión, contado para quien la instala y no para quien la
programa. Este fichero es la **fuente única de las notas que enseña DracApps**:
`scripts/publicar_release.py` copia el apartado de la versión que publica al cuerpo
de la Release, y la tienda enseña ese cuerpo tal cual. Si el apartado no existe, el
script aborta antes de construir nada.

Formato: un apartado `## v<versionName>` por versión, con la fecha de publicación al
lado; mientras no esté publicada, «sin publicar». La primera línea del apartado hace
de titular en la tienda, así que conviene que se entienda sola.

## v0.1.0 — sin publicar

Primera versión de DracPDF para Android.

El mismo motor que el DracPDF de escritorio, ahora en el móvil: se abren PDFs desde
el explorador de archivos, desde el share sheet de cualquier app o desde la propia
aplicación, y se leen con zoom por pellizco, doble toque y panel de miniaturas.
Varios documentos pueden estar abiertos a la vez.

Qué se puede hacer con un documento:

- **Formularios**: rellenar campos de texto, casillas, opciones y listas de un PDF
  oficial y guardarlos, con aviso claro cuando el formulario es XFA y no se puede.
- **Firma dibujada**: firmar con el dedo, colocar la firma donde toque y guardarla en
  una biblioteca para reutilizarla.
- **Firma digital PAdES**: firmar con un certificado `.p12` propio, con sello visible,
  y comprobar las firmas que ya trae el documento.
- **Caja de herramientas**: unir, dividir, organizar páginas arrastrándolas, proteger y
  desproteger con contraseña, comprimir y convertir.
- **Leer de verdad**: buscar en el documento con los resultados resaltados, seleccionar
  texto y copiarlo, índice del documento, ir a una página, seguir enlaces —los que
  salen fuera preguntan antes—, imprimir, y ajustar la vista a ancho, a página, en
  doble página o girada, con lo elegido guardado.
- **Recientes**: la aplicación recuerda los documentos por los que has pasado y **por
  qué página ibas** en cada uno.
- **Marcar**: resaltar, subrayar, tachar, poner notas y escribir sobre la página, con
  anotaciones estándar que se ven y se pueden borrar desde cualquier otro visor.
- **Editar**: añadir y quitar imágenes, y corregir un texto de forma que el original
  desaparece de verdad en vez de quedarse debajo.
- **Convertir**: a HTML, Markdown, texto, Word, ODT, RTF, imágenes, y las tablas a CSV
  o XLSX. Y al revés: hacer un PDF con imágenes, Markdown, HTML o texto.
- **Escanear**: fotografiar hojas con la cámara, enderezarlas y guardarlas como PDF.

Los documentos firmados se abren bloqueados para edición y ofrecen guardar una copia
editable, para no romper firmas ajenas sin querer. Los que llegan compartidos desde
otra aplicación avisan de que son prestados y ofrecen quedárselos.

Tema claro y oscuro, o el del sistema, con la identidad Ladón del escritorio.
