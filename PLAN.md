# DracPDF Android

Port nativo de DracPDF a Android: visor de PDF con formularios AcroForm, firma dibujada, firma digital PAdES, caja de herramientas, conversiones y edición de contenido, en Kotlin + Jetpack Compose. Identidad visual: diseño "DracPDF Android" de Claude Design YA ENTREGADO (mismo proyecto que Ladón; fuente de verdad de la UI desde la Fase 1, con iconografía por tema en assets/icons-tinted, marca en assets/brand y preview del launcher). Distribución y actualizaciones: a través de DracApps, la tienda de la casa: esta app NO lleva sistema de auto-actualización propio; DracApps la instala y actualiza.

## Decisiones de arquitectura (fijas)
- Motor PDF: MuPDF Android (el mismo motor C que PyMuPDF usa en el escritorio: coherencia de comportamiento en render, formularios, texto estructurado y anotaciones). Licencia AGPL asumida (proyecto personal, código en GitHub)
- Firma digital PAdES: PDFBox-Android + Bouncy Castle (pyHanko no existe en Android); certificados PKCS#12 (.p12/.pfx) elegidos por el usuario vía SAF. Credencial abstracta como en escritorio (variante PKCS#12 hoy; puerta abierta a otras)
- Convivencia de motores por fichero, como en escritorio: MuPDF trabaja el documento; para firmar, se vuelca a disco (guardado incremental que preserva revisiones/firmas previas), PDFBox firma la copia de forma atómica y el registro recarga bajo el mismo id. Verificación siempre sobre los bytes reales de disco
- Acceso a ficheros SIEMPRE por Storage Access Framework (ACTION_OPEN_DOCUMENT / CREATE_DOCUMENT, permisos persistibles para recientes); nada de permisos amplios de almacenamiento. La app se registra para el intent VIEW de application/pdf (abrir PDFs desde otras apps) y para el share sheet (recibir y compartir)
- Arquitectura limpia idéntica al escritorio: dominio y casos de uso en Kotlin puro sin Android framework, puertos (DocumentRepository, FormService, SignatureService, ConversorPDF...), adaptadores MuPDF/PDFBox/SAF, registro de documentos compartido dueño del ciclo de vida y de las marcas (CAMBIOS_SIN_GUARDAR, FIRMADO detectado también al abrir)
- Reglas de aceptación de UI desde el día uno: cada fase declara su tabla de pantallas/acciones y un test de inventario las verifica desde la UI real (Compose testing), jamás invocando métodos internos. Los criterios funcionales se demuestran con scripts/tests sin UI sobre la pila real (JVM o instrumentados)
- Documentos FIRMADOS: edición rechazada con oferta de "guardar copia editable", como en escritorio
- Tolerancia móvil: la app puede morir en cualquier momento (proceso reclamado); todo estado en vuelo (colocación de firma, formulario a medias, operación larga) debe sobrevivir a muerte y recreación o degradar limpiamente. Operaciones largas con WorkManager o corrutinas + foreground según duración, nunca bloqueando la UI

## Fase 1: Visor
### Parte 0: identidad (diseño DracPDF Android)
0a. Importar el diseño con el MCP de claude_design (URL en el prompt): tokens a tema Compose/Material 3 claro y oscuro, tipografía y formas; nada de colores sueltos por el código
0b. Iconografía desde los SVG del paquete (assets/icons-tinted/*, respetando sus variantes light/dark/accent como recoloreado por tema y estado) y marca desde assets/brand; icono de launcher conforme al preview del diseño montado como icono adaptativo (si el paquete no trae las capas background/foreground/monochrome por separado, señalarlo: se piden a Claude Design antes de improvisar unas)
0c. Las pantallas maquetadas del diseño son la referencia obligada de toda la UI de todas las fases

### Tareas
1. Esqueleto del proyecto (Gradle, estructura hexagonal, detekt/ktlint, CI local)
2. Casos de uso AbrirDocumento (id de sesión + registro, como escritorio) y RenderizarPagina (escala como parámetro; validación de rango en dominio) + adaptador MuPDF
3. Visor Compose: LazyColumn de páginas con render perezoso (visibles ± 1), caché LRU por bytes indexada por (página, escala), zoom por pellizco y doble toque, scroll fluido
4. Panel/hoja de miniaturas perezosas
5. Apertura: SAF, intent VIEW desde otras apps, y recepción por share sheet; PDFs cifrados piden contraseña
6. Pestañas o selector de documentos abiertos (multi-documento sobre el registro)

**Criterio F1:** un PDF de 500+ páginas abre sin bloquear (render inicial < 2 s en dispositivo medio, solo páginas visibles renderizadas, demostrado con métricas); zoom y salto de página fluidos; abrir desde el explorador de archivos y desde otra app por share funciona. Inventario en verde.

## Fase 2: Formularios AcroForm
1. FormService sobre MuPDF: listar campos (texto, checkbox, radio, combo, lista), detección de XFA con aviso
2. Overlay Compose sobre el render: campos posicionados con la transformación página→pantalla, estilo "papel" fijo (el documento siempre es claro, independiente del tema)
3. Escritura en vivo al perder foco/toggle; documento en memoria como única fuente de verdad; guardado incremental vía SAF
4. Teclado y foco bien resueltos (scroll al campo enfocado, tipos de teclado por campo)

**Criterio F2:** rellenar un formulario oficial real (AcroForm) campo a campo de cada tipo, guardar, reabrir en otra app y ver los valores; XFA avisa. Verificación sin UI de persistencia por tipo. Inventario en verde.

## Fase 3: Firma dibujada
1. Canvas táctil de firma (aquí el dedo/stylus es ventaja sobre el ratón): captura con suavizado por curvas, fondo transparente, exportación PNG
2. Modo colocar: arrastrar/redimensionar el rectángulo sobre la página antes de confirmar; estampado con canal alfa
3. Biblioteca de firmas guardadas (almacenamiento privado, PNG + sidecar JSON, alta atómica, huérfanos ignorados)
4. Marca de cambios en el registro; controles contextuales del modo SOLO visibles durante la colocación

**Criterio F3:** firma dibujada, estampada y verificada con alfa presente tras guardar/reabrir; visible correcta en otro visor. Inventario en verde (controles contextuales invisibles fuera del modo).

## Fase 4: Firma digital PAdES
1. SignatureService con PDFBox + Bouncy Castle: cargar .p12 vía SAF con contraseña, firmar con sello visible (posición elegida como en la Fase 3)
2. Flujo de guardado: cambios de MuPDF volcados incrementalmente → PDFBox firma como revisión nueva en fichero temporal → reemplazo atómico → recarga bajo el mismo id → marca FIRMADO
3. Verificación de firmas del documento (panel con estados válida/inválida/desconocida) sobre bytes de disco; marca FIRMADO también al abrir documentos ya firmados (incluidos los de terceros)
4. Bloqueo de edición tras firma con oferta de copia editable; certificado de prueba generado por script en el repo (nunca binarios versionados); TSA opcional sin dependencias de red en tests

**Criterio F4:** PDF firmado en el móvil valida en verde en Adobe (escritorio, con el certificado de prueba en confianza: comprobación manual del usuario); la verificación propia distingue firmado íntegro de manipulado; edición post-firma rechazada; PDF firmado por terceros abre bloqueado. Todo lo automatizable con exit 0 sin red.

## Fase 5: Conformidad visual (la identidad se aplica desde la Fase 1; aquí se audita)
1. Revisión pantalla a pantalla contra las maquetas del diseño con capturas lado a lado en ambos temas; tabla de no-conformidades corregida commit a commit
2. Overlay de formularios, resaltados y selección anclados al "papel" (invariantes al tema), verificado con test de humo en ambos temas y tras cambio de tema en caliente
3. Estado sin documento con el dragón atenuado y recientes según maqueta; tema claro/oscuro/sistema persistente

**Criterio F5:** ambos temas conmutables y persistentes (test de regresión de persistencia); capturas junto a maquetas de todas las pantallas existentes; test de humo de temas incluyendo el overlay invariante y el viewport de todos los paneles con scroll. Inventario en verde.

## Fase 6: Caja de herramientas
1. Unir (por SAF multi-selección, sobre rutas/URIs, avisando si uno está abierto con cambios), organizar páginas desde miniaturas (extraer, eliminar, rotar, reordenar con arrastre), dividir por rangos
2. Proteger/desproteger con contraseña conocida (sin fuerza bruta), comprimir con antes/después, exportar páginas a imágenes y texto
3. Todas con salida atómica vía SAF, progreso cancelable, y respeto de FIRMADO (operar sobre copia o rechazar)

**Criterio F6:** ciclo completo sin UI (unir con orden verificado, dividir, proteger/reabrir/desproteger con igualdad, comprimir con reducción, exportar) con exit 0; operaciones sobre FIRMADO rechazadas. Inventario en verde.

## Fase 7: Fundamentos de visor móvil
1. Buscar en el documento (barra, resaltados activo/resto, navegación, sin bloquear), selección de texto por gesto largo con handles y copiar/compartir selección
2. Índice del documento (outline) como hoja/panel, ir a página, enlaces internos y externos (estos con confirmación)
3. Imprimir vía PrintManager del sistema; modos de vista (ajuste, doble página en apaisado/tablet, rotación de vista) persistentes
4. Recientes con permisos SAF persistidos, restauración de última página/zoom por documento, y comportamiento de arranque: abrir por intent muestra ese documento sin machacar la sesión guardada
5. Deshacer en formularios; propiedades del documento; compartir el PDF actual por share sheet

**Criterio F7:** búsqueda correcta contra fixture, copia exacta de selección conocida, outline coincidente, impresión a PDF del sistema con el rango pedido, persistencias tras cerrar/reabrir, deshacer en orden. Inventario en verde.

## Fase 8: Texto, anotaciones e imágenes
1. Añadir texto con fuentes OFL embebidas (colocación como la firma); resaltar/subrayar/tachar sobre la selección y notas, como anotaciones estándar visibles en otros visores y eliminables
2. Añadir imagen desde galería/SAF; eliminar imagen con selección visual del contorno exacto y avisos (multipágina, escaneo a página completa)
3. Corregir texto existente acotado (una línea, redacción + reinserción con fuente sustituta, aviso de límites, caso "no cabe" ofreciendo alternativas); si la calidad general resulta inaceptable, recorte decidido con el usuario y documentado
4. Deshacer cubre todo; FIRMADO rechaza todo

**Criterio F8:** el del escritorio adaptado: texto/anotaciones/imágenes persistentes tras reabrir, original no extraíble tras corregir, avisos demostrados; con exit 0. Inventario en verde.

## Fase 9: Conversiones (honestas en móvil)
1. Salientes con MuPDF: PDF→HTML, PDF→Markdown (heurística de títulos), PDF→texto y PDF→imágenes; detección de escaneados con aviso
2. PDF→Word y Word→PDF: EVALUACIÓN previa obligatoria antes de comprometerlas: no existen pdf2docx ni mammoth en Android; el diseño de esta fase propone opciones reales (portar lógica, biblioteca Java/Kotlin viable, o declararlas fuera de alcance móvil con el usuario) y SE DECIDE CON EL USUARIO antes de implementar nada
3. Integración en el menú/acciones según el diseño, con worker y progreso

**Criterio F9:** salientes verificadas sin UI (texto, títulos, imágenes, aviso de escaneado) con exit 0; el alcance de Word decidido y documentado en este plan. Inventario en verde.

## Fase 10: Distribución vía DracApps
1. publicar_release.py del repo (patrón de la casa): build firmado con la keystore, verificación de coherencia de versionCode (apkanalyzer vs manifiesto), Release con gh, y alta/actualización en el apps.yaml del catálogo de DracApps
2. Primera publicación real en DracApps con el usuario delante; instalación y actualización posterior demostradas desde la tienda en un dispositivo
3. CHANGELOG y notas de versión que DracApps muestra

**Criterio F10:** DracPDF Android instalable desde DracApps; una versión posterior detectada y actualizada desde la tienda sin tocar orígenes externos. El E2E real, con el usuario.
