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
- **Caja de herramientas**: unir, dividir, organizar páginas, proteger y desproteger
  con contraseña, comprimir y convertir a imágenes o a texto.

Los documentos firmados se abren bloqueados para edición y ofrecen guardar una copia
editable, para no romper firmas ajenas sin querer.

Tema claro y oscuro, o el del sistema, con la identidad Ladón del escritorio.
