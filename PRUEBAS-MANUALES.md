# Pruebas que hay que hacer a mano

Lo que ningún test automático puede demostrar, agrupado para hacerlo de una sentada.
Cada punto dice **qué hacer**, **qué tiene que pasar** y **qué significaría que fallara**,
para que un «no funciona» sea accionable en vez de una nota suelta.

El APK sale de `./gradlew :app:assembleDebug` y queda en
`app/build/outputs/apk/debug/app-debug.apk`.

Material que conviene tener a mano antes de empezar:

- Un PDF con formulario oficial. El W-9 se baja con
  `python scripts/descargar_formularios_oficiales.py` y queda en `fixtures-externos/`.
- Adobe Acrobat Reader instalado en el móvil, o el PDF a mano en el escritorio.
- Un stylus, si lo hay. Si no, el dedo.

---

## Fase 2 · Formularios

### 1. Un formulario relleno aquí se ve relleno en otro visor

Es el criterio F2 y el único punto que no se puede automatizar: lo que hay que
comprobar no es que el valor esté en el PDF —eso ya lo demuestran los tests—, sino
que **otro programa lo enseña**.

1. Abrir el W-9 en DracPDF, entrar en modo formulario y rellenar al menos: un campo
   de texto, una casilla, un radio y un combo.
2. Guardar.
3. Compartir el PDF a Adobe Acrobat Reader (o pasarlo al ordenador y abrirlo allí).

**Tiene que pasar:** los valores aparecen donde se escribieron.

**Si falla:** anotar *qué* campo y *cómo* se ve mal. Un campo vacío en Adobe pero
lleno en otros visores apunta a la capa XFA del W-9 —está previsto y avisado— y no a
que el relleno esté mal; en ese caso, probar el mismo ciclo con un formulario
AcroForm puro para separar las dos cosas.

### 2. Girar el móvil con el teclado abierto y un campo a medias

Es donde los formularios en móvil pierden el foco, el valor, o los dos.

1. Tocar un campo de texto, escribir media palabra **sin salir del campo**.
2. Girar el teléfono con el teclado abierto.

**Tiene que pasar:** lo escrito sigue ahí. Puede perderse el foco —el teclado puede
cerrarse—, pero **el texto no**.

**Si falla:** decir si se perdió el texto, el foco, o si la aplicación se cerró.

### 3. El teclado no tapa el campo que se está rellenando

Rellenar un campo de la **mitad inferior** de la página, con el teclado abierto.

**Tiene que pasar:** el campo queda por encima del teclado, sin tener que arrastrar.

---

## Fase 3 · Firma dibujada

### 4. Firmar con el dedo (y con lápiz, si lo hay)

Ningún test toca la pantalla de verdad: el suavizado sólo se juzga firmando.

1. Firmas → Dibujar, y firmar como se firma normalmente.
2. Mirar el trazo: **¿se ve curvo o se ven los segmentos?**

**Tiene que pasar:** el trazo sigue al dedo sin retraso apreciable y las curvas se
ven curvas.

**Si falla:** si se ven esquinas, el suavizado se queda corto; si va a tirones, el
problema es de rendimiento. Son dos arreglos distintos, así que conviene distinguir.

### 5. La firma colocada se ve donde se dejó, y en otro visor

1. Colocar la firma sobre una zona **con texto o líneas debajo**, ajustarla y
   confirmar.
2. Guardar y abrir el PDF en Adobe (o en el ordenador).

**Tiene que pasar:** la firma está en el mismo sitio y **no tiene recuadro**: se ve
lo que hay debajo alrededor del trazo.

**Si falla:** un rectángulo blanco o gris alrededor de la firma significa que el
canal alfa se perdió por el camino, pese a que los tests lo miden.

### 6. La firma no sale estirada

Colocar una firma **ancha y baja** y estirarla desde el asa hasta hacerla grande.

**Tiene que pasar:** la letra mantiene su forma; sólo cambia el tamaño.

---

## Fase 6 · Caja de herramientas

### 7. Las imágenes acaban en la carpeta que se eligió

El selector de carpetas del sistema no existe en el emulador de la misma forma que en
un teléfono con Drive y tarjeta, y es lo único de la conversión que no se puede
automatizar: los tests escriben en carpetas propias.

1. Herramientas → Convertir → Imágenes → JPEG, calidad al 60, páginas «1-3».
2. Elegir una carpeta cualquiera del teléfono.
3. Abrir esa carpeta desde la galería o el gestor de archivos.

**Tiene que pasar:** hay tres ficheros, uno por página, y se ven.

**Si falla:** si no aparece ninguno, el problema es el permiso del árbol elegido; si
aparecen con nombre raro o cero bytes, es la escritura. Son dos fallos distintos.

### 8. Reordenar páginas con el dedo

Los tests arrastran con coordenadas; el dedo es otra cosa.

1. Herramientas → Organizar páginas, mantener pulsada una miniatura y llevarla a otro
   sitio.

**Tiene que pasar:** la página se levanta, el hueco se abre bajo el dedo y al soltar
se queda donde se dejó. Sin mantener pulsado, el gesto **desplaza la rejilla**.

**Si falla:** si la lista se mueve en vez de la página, la pulsación larga no llega;
si la página cae en un sitio distinto del que se ve, es el cálculo del hueco.

---

## Fase 7 · Fundamentos de visor móvil

### 9. Imprimir de verdad, y con el rango pedido

Los tests comprueban que el PDF que se entrega lleva las páginas correctas; lo que no
pueden comprobar es el diálogo del sistema ni la impresora.

1. Abrir un documento de al menos 6 páginas → ⋮ → Imprimir.
2. En el diálogo del sistema, elegir «Guardar como PDF» y **páginas 2-4**.
3. Abrir el PDF resultante.

**Tiene que pasar:** tiene tres páginas, y son la 2, la 3 y la 4 del original.

**Si falla:** si salen las seis, el rango no está llegando; si sale una hoja en
blanco, el problema es la escritura al descriptor del sistema.

### 10. Seleccionar texto con el dedo

1. Mantener pulsada una palabra del documento.
2. Arrastrar las dos asas para ampliar la selección.
3. Copiar, y pegar en cualquier otra aplicación.

**Tiene que pasar:** se selecciona la palabra entera al primer toque, las asas ajustan
letra a letra, y lo pegado es exactamente lo que estaba resaltado.

**Si falla:** decir si el problema es que no selecciona nada —posible escaneado sin
texto— o que selecciona otra cosa.

### 11. Los recientes sobreviven al teléfono

1. Abrir dos o tres documentos desde sitios distintos (descargas, Drive, WhatsApp) y
   dejar cada uno por una página que no sea la primera.
2. Cerrar la aplicación **desde el gestor de tareas**, no con «atrás».
3. Volver a abrirla y tocar uno de los recientes.

**Tiene que pasar:** están los tres, en orden, y el que se abre lo hace **por la página
donde se dejó**. Los que llegaron compartidos desde otra aplicación pueden avisar de
que quizá ya no abran: eso es correcto, y al tocarlos tienen que fallar con un aviso
limpio, nunca cerrarse.

---

## Fase 8 · Marcar y editar

### 12. Un resaltado hecho aquí se ve en otro visor

Es lo que distingue una anotación de verdad de un rectángulo pintado encima, y sólo se
comprueba abriendo el documento en otro programa.

1. Mantener pulsada una frase → Resaltar. Repetir con Subrayar y Tachar.
2. Guardar y abrir el PDF en Adobe Acrobat (o en el ordenador).

**Tiene que pasar:** las tres marcas se ven, el texto de debajo **se puede seleccionar
y buscar**, y Adobe deja borrarlas.

**Si falla:** si el texto de debajo ya no se selecciona, la marca se ha fundido con el
contenido y eso no es una anotación.

### 13. Corregir un texto no deja el original debajo

1. Corregir una línea del documento por otra más corta.
2. Guardar, abrir en otro visor y **seleccionar y copiar** esa zona.

**Tiene que pasar:** lo que se copia es el texto nuevo. El viejo no está en ninguna
parte.

**Si falla:** si aparece el texto original, se está tapando en vez de corregir, que es
justo lo que esta función promete no hacer.

---

## Fase 9 · Conversiones y escáner

### 14. Los formatos de ofimática abren donde tienen que abrir

Los tests comprueban que el fichero se relee bien aquí; lo que no pueden es abrir Word.

1. Convertir un PDF con títulos y alguna tabla a **Word**, **ODT** y **XLSX**.
2. Abrir cada uno donde toque: Word o Google Docs, LibreOffice, y Excel o Sheets.

**Tiene que pasar:** abren sin avisos de fichero dañado, el texto está entero y los
títulos se ven como títulos.

**Si falla:** anotar **qué programa** se queja y qué dice; un ZIP mal montado y un XML
mal formado dan mensajes distintos.

### 15. Escanear una hoja de verdad

La cámara no se automatiza: esto es de dedo y papel.

1. Menú del inicio → «Escanear con la cámara», y dar el permiso cuando lo pida.
2. Fotografiar dos o tres hojas, un poco de lado a propósito.
3. Guardar el PDF.

**Tiene que pasar:** cada hoja sale enderezada y recortada, en el orden en que se
fotografiaron, y el PDF tiene tantas páginas como hojas.

**Si falla:** si la hoja sale girada o estirada, el problema es la corrección de
perspectiva; si sale entera con el fondo de la mesa, es que las esquinas se quedaron
en el borde de la foto.

---

## Fase 1 · Lo que quedó pendiente de comprobar en aparato real

### 16. Abrir un PDF desde otra aplicación

Enviar un PDF a DracPDF desde WhatsApp, Gmail y el gestor de archivos.

**Tiene que pasar:** se abre y se lee.

**Nota:** que el selector del sistema ofrezca «Siempre» y el trato fino de los URI
efímeros son de la Fase 11, no de ahora; aquí sólo se mira que abra.

### 17. Un documento largo de verdad

Abrir el PDF más gordo que haya a mano y hacer scroll rápido, zoom y saltos de
página.

**Tiene que pasar:** la primera página aparece enseguida y el scroll no se traba.
