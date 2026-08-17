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

## Fase 1 · Lo que quedó pendiente de comprobar en aparato real

### 9. Abrir un PDF desde otra aplicación

Enviar un PDF a DracPDF desde WhatsApp, Gmail y el gestor de archivos.

**Tiene que pasar:** se abre y se lee.

**Nota:** que el selector del sistema ofrezca «Siempre» y el trato fino de los URI
efímeros son de la Fase 11, no de ahora; aquí sólo se mira que abra.

### 10. Un documento largo de verdad

Abrir el PDF más gordo que haya a mano y hacer scroll rápido, zoom y saltos de
página.

**Tiene que pasar:** la primera página aparece enseguida y el scroll no se traba.
