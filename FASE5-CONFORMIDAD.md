# Fase 5 · Conformidad visual

Auditoría pantalla a pantalla contra el diseño **DracPDF Android** de Claude Design
(`12145c22-8a62-4f15-bcb7-41d3448492c9`, fichero `DracPDF Android.dc.html`), que es
la fuente de verdad desde la Fase 1. Este documento es la tabla de no-conformidades
que pide la tarea 1 de la fase: qué se apartó de la maqueta, qué se ha hecho con
ello, y qué está esperando respuesta.

Las dos fuentes del diseño no siempre coinciden entre sí —el paquete
`export-android/` y las maquetas de la §19—, y cuando pasa se dice aquí en vez de
elegir en silencio.

## Corregidas

| # | Dónde | Qué decía el diseño | Qué había | Arreglo |
|---|---|---|---|---|
| 1 | Pantalla de inicio | Barras del sistema respetadas (edge-to-edge con el contenido apartado) | El dragón y el título se pintaban **encima del reloj** | `statusBarsPadding()` en la cabecera y `navigationBarsPadding()` en el cuerpo |
| 2 | Pantalla de error, hoja de documentos, rejilla de miniaturas | Íd. | Contenido bajo las barras del sistema | `safeDrawingPadding()` y el inset inferior en el `contentPadding` |
| 3 | Modo formulario | §15.4: la barra del modo **sustituye** a la superior (✕ · título · confirmación); «nunca conviven dos» | La barra del documento seguía arriba y el modo añadía una inferior con Hecho y Guardar | Barra superior contextual ✕ · «Formulario» · «Guardar»; abajo queda «Campo N de M» y los chevrons |
| 4 | Barra superior del visor | §19: atrás · nombre▾ · buscar · **⋮**; §22: la lista de abiertos se abre desde el nombre y desde `ic_documents` **en el ⋮** | `ic_documents` ocupaba el cuarto sitio como botón suelto y no había ⋮ | ⋮ con Documentos abiertos · Abrir otro PDF · Guardar copia · Imprimir · Compartir · Propiedades (las cuatro últimas, apagadas hasta su fase) |
| 5 | Tema | §22: «Tema (hoja de tres radios: claro / oscuro / del sistema)» en el ⋮ del inicio | El ⋮ estaba deshabilitado y el tema seguía siempre al sistema, sin poder elegirlo ni guardarlo | Menú del inicio con las cuatro entradas de la maqueta, hoja de tres radios, preferencia persistida en DataStore y leída antes del primer fotograma |
| 6 | Menú del inicio | Cuatro entradas: Ajustes · Tema · Ayuda · Acerca de DracPDF | Faltaban «Ayuda» y «Acerca de» por no tener icono | Pedidos y entregados: `ic_help` y `ic_about`. Las cuatro entradas están; sólo «Tema» funciona todavía |

## Conformes, comprobadas

- **Tokens de color**: los 28 valores de `export-android/Color.kt` están uno a uno en
  `Colores.kt`, con los mismos nombres de origen. Sin color dinámico, como manda el
  documento: aquí el color es información legal.
- **Papel invariante al tema**: los diez valores de `PaperColors` viven fuera del
  `ColorScheme` y no cambian con el tema. Verificado ahora con píxeles reales, no con
  la constante (`PapelInvarianteTest`).
- **Iconografía**: 58 iconos copia literal del paquete —los 56 originales más
  `ic_help` y `ic_about`, de esta revisión—; ninguno redibujado.

## Preguntado al diseño, y respondido

Las tres preguntas de `design-handoff/PETICION-fase5.md` y
`ICONOS-QUE-FALTAN-fase5.md` (subida al proyecto como
`uploads/ICONOS-QUE-FALTAN-fase5.md`) tienen respuesta:

| # | Asunto | Respuesta | Qué se ha hecho |
|---|---|---|---|
| 6 | `onPrimary` del botón primario en oscuro | **Manda el token: tinta `#1A1D23`.** Blanco sobre `#E0534A` da 3,6:1 y no llega a AA para texto de 14 px; la tinta da 4,6:1 | Nada que tocar: la app ya seguía el token. El diseño corrigió las cuatro maquetas que lo dibujaban blanco |
| 7 | Iconos de Ayuda y Acerca de | **Un icono nuevo, no dos.** `ic_help` es un «?» en círculo —la (i) **ya estaba cogida** por `ic_properties`, cosa que no habíamos visto— y para «Acerca de» se acepta la sugerencia: la marca, sin glifo nuevo | `ic_help.xml` y `ic_about.png` (5 densidades) integrados; las cuatro entradas de la maqueta ya están en el menú |
| 8 | Alcance de la barra contextual por modo | **Sin excepciones**: colocar una firma es un modo y le toca su barra; al entrar, la barra inferior de cuatro acciones se sustituye por la fila de colocación | Ya era así: `BarraColocacion` sustituye a la inferior desde la Fase 3. Conforme |

## Verificado en el Pixel 8 Pro

- Menú del ⋮ con las cuatro entradas y los dos iconos nuevos, igual que la maqueta.
- Hoja de Tema con los tres radios; el cambio a claro se aplica **en caliente**.
- **La preferencia sobrevive a matar la aplicación**: reabierta desde cero, sigue en
  claro. No sólo en el test: en el aparato.
- Visor y modo formulario en tema claro, con la barra contextual ✕ · «Formulario» ·
  «Guardar» arriba y «23 campos» con los chevrons abajo.
- El papel sigue siendo papel en los dos temas, y los campos pendientes conservan su
  ámbar.

Suite instrumentada en verde sobre el aparato —**59 de 59**, incluidos los
inventarios de las Fases 1, 2, 3 y 5—, y el proyecto compila sin un solo aviso.

Perseguir los avisos de compilación destapó tres defectos que no daban la cara:

1. `onTrimMemory` comparaba contra `TRIM_MEMORY_RUNNING_LOW`, deprecado desde
   Android 14 y que el sistema ya no manda: la caché de páginas **no se soltaba nunca**
   en los teléfonos actuales.
2. `irACampo` leía el estado fuera de la corrutina, así que **dos toques seguidos en
   «siguiente» perdían uno**. Ahora lee dentro y las navegaciones van bajo llave.
3. Ocho tests preguntaban por la pantalla antes de que estuviera pintada; lo tapaba el
   dispatcher viejo de Compose y salió al migrar a la API nueva.

## Capturas

En `capturas/fase5/`. Las de la pantalla de inicio y el modo formulario salen del
Pixel 8 Pro; las de las hojas, de un emulador con la resolución y la densidad del
Pixel forzadas (`wm size 1008x2244`, `wm density 420`) para que comparen igual contra
las maquetas sin ocupar el teléfono.

| Captura | Qué demuestra |
|---|---|
| `inicio-menu-oscuro` | El ⋮ con las cuatro entradas de la maqueta y los dos iconos nuevos |
| `hoja-tema-oscuro` · `hoja-tema-claro` | Los tres radios y el cambio en caliente |
| `inicio-claro-tras-reinicio` | La preferencia sobrevive a matar la aplicación |
| `visor-claro` · `formulario-claro` | Barra contextual ✕ · «Formulario» · «Guardar» y el pie con los chevrons |
| `hoja-indice-claro` · `hoja-indice-oscuro` | Las dos pestañas y la rejilla, en los dos temas |
| `hoja-indice-final-claro` | **La rejilla llega hasta el final**: la última fila queda por encima de la barra de navegación |
| `menu-visor-oscuro` | El ⋮ del visor: dos acciones vivas y cuatro apagadas |
| `hoja-documentos-oscuro` | La fila de la §22 y el pie con «Abrir otro PDF» y «Cerrar todos» visibles |

**«Del sistema» comprobado de verdad**: al poner el emulador en modo noche, la
aplicación cambió de tema sin recrearse —con una hoja abierta— y las miniaturas
siguieron sobre papel blanco.

## Observaciones menores, sin corregir

Ninguna llega a no-conformidad, pero quedan anotadas para no perderlas:

1. **El dragón atenuado casi no se ve en tema claro.** Con opacidad 0,07 sobre el
   canvas claro queda al límite de lo perceptible, mientras que en oscuro se lee bien.
   La maqueta del estado vacío sólo está dibujada en oscuro, así que no hay contra qué
   comparar: si se quiere igualar la presencia, hace falta el valor de opacidad para
   claro.
2. **La barra de colocación dice «Cancelar» donde el diseño la describe como
   «Limpiar»**. En el lienzo de dibujo «Limpiar» tiene sentido —borra el trazo—, pero
   sobre la página no hay nada que limpiar: lo que se hace es cancelar la colocación.
   Se deja como está a la espera de confirmarlo.
