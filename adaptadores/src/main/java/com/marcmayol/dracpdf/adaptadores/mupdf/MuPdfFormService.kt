package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.PDFWidget
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.FormatoTexto
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import com.marcmayol.dracpdf.dominio.puertos.FormService

/**
 * El puerto de formularios sobre MuPDF.
 *
 * Entra por [SesionesMuPdf], igual que el repositorio, para tocar el mismo documento
 * desde el mismo hilo: el formulario que se rellena tiene que ser el del documento
 * que se está viendo, no una segunda copia.
 *
 * Todo lo que sale de aquí son datos planos. Los `PDFWidget` viven mientras viva la
 * página que los cargó, así que se leen enteros dentro del bloque y se devuelven
 * copiados; guardar uno para más tarde sería guardar un puntero a memoria liberada.
 */
class MuPdfFormService(
    private val sesiones: SesionesMuPdf,
) : FormService {
    override fun formulario(id: IdDocumento): Formulario =
        sesiones.en(id) { documento ->
            val pdf = documento as? PDFDocument ?: return@en Formulario(TipoFormulario.NINGUNO)
            val acroForm = pdf.hasAcroForm()
            val xfa = pdf.hasXFAForm()
            val tipo =
                when {
                    xfa && !acroForm -> TipoFormulario.XFA_PURO
                    xfa && acroForm -> TipoFormulario.XFA_HIBRIDO
                    acroForm -> TipoFormulario.ACROFORM
                    else -> TipoFormulario.NINGUNO
                }
            Formulario(tipo = tipo, campos = if (acroForm) conObjetos { contarCampos(pdf, it) } else 0)
        }

    override fun camposDePagina(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario> =
        sesiones.en(id) { documento ->
            val hoja = documento.loadPage(pagina) as? PDFPage ?: return@en emptyList()
            try {
                conObjetos { objetos ->
                    val leidos =
                        hoja.widgets.orEmpty().mapIndexed { indice, widget ->
                            objetos.anotarAnotacion(widget)
                            campoDe(widget, pagina, indice, objetos)
                        }
                    enOrdenDeTabulacion(hoja, leidos, objetos)
                }
            } finally {
                hoja.destroy()
            }
        }

    /**
     * Los campos en el orden en que el documento quiere que se recorran.
     *
     * Por omisión, el orden de tabulación de un PDF es el del array `/Annots` de la
     * página, que es en el que MuPDF los entrega: no hay nada que hacer. Pero una
     * página puede pedir otro con `/Tabs`, y entonces manda el papel y no el fichero:
     * `/R` recorre por filas y `/C` por columnas, que es lo que espera quien mira el
     * impreso y no el PDF.
     *
     * `/Tabs /S` pide el orden del árbol de estructura del documento —el mismo que
     * usan los lectores de pantalla—, y eso hay que ir a buscarlo al `/StructTreeRoot`.
     * Aquí se deja en el orden de `/Annots`, que es la aproximación que usan los
     * visores que no implementan la estructura y que en los impresos bien hechos
     * coincide, porque quien los generó escribió las anotaciones en ese mismo orden.
     * Se decide así a sabiendas, no por olvido.
     */
    private fun enOrdenDeTabulacion(
        hoja: PDFPage,
        campos: List<CampoFormulario>,
        objetos: ObjetosMuPdf,
    ): List<CampoFormulario> =
        when (objetos.clave(objetos.anotar(hoja.getObject()), "Tabs")?.asName()) {
            "R" -> campos.sortedWith(compareBy({ banda(it.marco.y0) }, { it.marco.x0 }))
            "C" -> campos.sortedWith(compareBy({ banda(it.marco.x0) }, { it.marco.y0 }))
            else -> campos
        }

    /**
     * A qué fila (o columna) pertenece una coordenada.
     *
     * Sin agrupar en bandas, dos campos de la misma línea con medio punto de
     * diferencia se leerían como dos filas y el recorrido saltaría de una a otra.
     */
    private fun banda(coordenada: Float): Int = (coordenada / ALTURA_DE_BANDA_PT).toInt()

    override fun escribirTexto(
        id: IdDocumento,
        campo: IdCampo,
        valor: String,
    ): CampoFormulario = enElWidget(id, campo) { widget -> widget.setTextValue(valor) }

    override fun alternar(
        id: IdDocumento,
        campo: IdCampo,
    ): CampoFormulario = enElWidget(id, campo) { widget -> widget.toggle() }

    override fun elegirOpcion(
        id: IdDocumento,
        campo: IdCampo,
        opcion: String,
    ): CampoFormulario = enElWidget(id, campo) { widget -> widget.setChoiceValue(opcion) }

    /**
     * Aplica un cambio al widget que toca y devuelve el campo tal como queda.
     *
     * Los tres pasos están juntos —cargar la página, cambiar, releer— y a propósito:
     * el `PDFWidget` sólo vive mientras viva su página, así que el valor resultante
     * hay que leerlo aquí dentro. Sacar el widget fuera para releerlo después sería
     * leer memoria ya liberada.
     */
    private fun enElWidget(
        id: IdDocumento,
        campo: IdCampo,
        cambio: (PDFWidget) -> Unit,
    ): CampoFormulario =
        sesiones.en(id) { documento ->
            val hoja =
                documento.loadPage(campo.pagina) as? PDFPage
                    ?: throw IllegalArgumentException("La página ${campo.pagina} no admite formularios")
            try {
                conObjetos { objetos ->
                    val widgets = hoja.widgets.orEmpty()
                    widgets.forEach(objetos::anotarAnotacion)
                    val widget =
                        widgets.getOrNull(campo.indice)
                            ?: throw IllegalArgumentException("El campo $campo ya no está en el documento")

                    cambio(widget)
                    // Regenera la apariencia del campo: sin esto el valor está en el PDF
                    // pero la página se sigue dibujando con lo de antes, y el usuario cree
                    // que no se ha guardado nada.
                    widget.update()

                    campoDe(widget, campo.pagina, campo.indice, objetos)
                }
            } finally {
                hoja.destroy()
            }
        }

    /**
     * El número de campos del catálogo, sin cargar ninguna página.
     *
     * El árbol de campos no es plano: un campo puede colgar de otro por `/Kids`, y
     * las hojas del árbol son los campos de verdad. Los `/Kids` que son widgets no
     * cuentan aparte, porque son las apariencias del mismo campo —los botones de un
     * grupo de radio, sin ir más lejos—, y contarlos diría que un formulario de tres
     * campos tiene doce.
     */
    private fun contarCampos(
        pdf: PDFDocument,
        objetos: ObjetosMuPdf,
    ): Int {
        val raiz = objetos.clave(objetos.clave(objetos.anotar(pdf.trailer), "Root"), "AcroForm")
        val campos = objetos.clave(raiz, "Fields")
        if (campos == null || campos.isNull) return 0
        val nodos = (0 until campos.size()).mapNotNull { objetos.anotar(campos.get(it)?.resolve()) }
        return contarEn(nodos, profundidad = 0, objetos = objetos)
    }

    private fun contarEn(
        nodos: List<PDFObject>,
        profundidad: Int,
        objetos: ObjetosMuPdf,
    ): Int {
        if (profundidad > PROFUNDIDAD_MAXIMA) return 0
        return nodos.sumOf { nodo ->
            val hijos = camposHijos(nodo, objetos)
            if (hijos.isEmpty()) 1 else contarEn(hijos, profundidad + 1, objetos)
        }
    }

    /**
     * Los `/Kids` de un nodo que son campos de verdad, o `null` si no hay ninguno.
     *
     * Lo que los distingue es **tener nombre propio** (`/T`), y no ser o no ser
     * widgets. Un campo terminal viene casi siempre fusionado con su widget en un solo
     * objeto, así que «es widget» no significa «no es campo»: en el W-9 del IRS los
     * hijos de `Page1[0]` son los veintitrés campos del impreso, todos widgets, y
     * mirando el `/Subtype` se contaban como uno.
     *
     * Los `/Kids` **sin** `/T` sí son apariencias del mismo campo —los dos botones de
     * un grupo de radio— y no cuentan aparte.
     */
    private fun camposHijos(
        nodo: PDFObject,
        objetos: ObjetosMuPdf,
    ): List<PDFObject> {
        val hijos = objetos.clave(nodo, "Kids")
        if (hijos == null || hijos.isNull || hijos.size() == 0) return emptyList()
        return (0 until hijos.size())
            .mapNotNull { objetos.anotar(hijos.get(it)?.resolve()) }
            .filter { hijo ->
                val nombre = objetos.clave(hijo, "T")
                nombre != null && !nombre.isNull
            }
    }

    private fun campoDe(
        widget: PDFWidget,
        pagina: Int,
        indice: Int,
        objetos: ObjetosMuPdf,
    ): CampoFormulario {
        val nombre = widget.name.orEmpty()
        val etiqueta = widget.label?.takeIf { it.isNotBlank() && it != nombre }
        val caja = widget.bounds
        val esTexto = widget.fieldType == PDFWidget.TYPE_TEXT
        val tipo = tipoDe(widget.fieldType)
        return CampoFormulario(
            nombre = nombre,
            pagina = pagina,
            indice = indice,
            tipo = tipo,
            valor = widget.value.orEmpty(),
            marcado = tipo.esMarca && estaEncendido(widget, objetos),
            // Los límites ya vienen en el sistema de la página, con su rotación
            // aplicada: los mismos ejes en los que se rasteriza, que es lo que permite
            // al overlay poner el campo encima del hueco dibujado y no al lado.
            marco = RectPt(caja.x0, caja.y0, caja.x1, caja.y1),
            etiqueta = etiqueta,
            opciones = widget.options?.toList().orEmpty(),
            soloLectura = widget.isReadOnly,
            obligatorio = (widget.fieldFlags and PDFWidget.FIELD_IS_REQUIRED) != 0,
            multilinea = esTexto && widget.isMultiline,
            esContrasena = esTexto && widget.isPassword,
            maxLongitud = widget.maxLen.takeIf { esTexto && it > 0 },
            formatoTexto = if (esTexto) formatoDe(widget.textFormat) else FormatoTexto.NINGUNO,
        )
    }

    /**
     * Si esta casilla o este botón concreto están marcados.
     *
     * Se mira el estado de apariencia del widget (`/AS`) y **no** el valor del campo,
     * porque en un grupo de radio el valor es del grupo: los dos botones dirían que
     * valen «Si» y los dos saldrían marcados. `/AS` es de cada widget, y es lo que el
     * propio motor usa para decidir cuál dibuja encendido.
     */
    private fun estaEncendido(
        widget: PDFWidget,
        objetos: ObjetosMuPdf,
    ): Boolean {
        val estado = objetos.clave(objetos.anotar(widget.`object`), "AS")?.asName()
        return if (estado != null) {
            estado != CampoFormulario.APAGADO
        } else {
            // Sin `/AS` —un PDF mal formado— lo único que queda es el valor del campo.
            val valor = widget.value.orEmpty()
            valor.isNotBlank() && valor != CampoFormulario.APAGADO
        }
    }

    private fun tipoDe(tipo: Int): TipoCampo =
        when (tipo) {
            PDFWidget.TYPE_TEXT -> TipoCampo.TEXTO
            PDFWidget.TYPE_CHECKBOX -> TipoCampo.CASILLA
            PDFWidget.TYPE_RADIOBUTTON -> TipoCampo.RADIO
            PDFWidget.TYPE_COMBOBOX -> TipoCampo.COMBO
            PDFWidget.TYPE_LISTBOX -> TipoCampo.LISTA
            PDFWidget.TYPE_BUTTON -> TipoCampo.BOTON
            PDFWidget.TYPE_SIGNATURE -> TipoCampo.FIRMA
            else -> TipoCampo.DESCONOCIDO
        }

    private fun formatoDe(formato: Int): FormatoTexto =
        when (formato) {
            PDFWidget.TX_FORMAT_NUMBER -> FormatoTexto.NUMERO
            PDFWidget.TX_FORMAT_DATE -> FormatoTexto.FECHA
            PDFWidget.TX_FORMAT_TIME -> FormatoTexto.HORA
            PDFWidget.TX_FORMAT_SPECIAL -> FormatoTexto.ESPECIAL
            else -> FormatoTexto.NINGUNO
        }

    /** Los tipos que se guardan como estado encendido/apagado y no como valor. */
    private val TipoCampo.esMarca: Boolean
        get() = this == TipoCampo.CASILLA || this == TipoCampo.RADIO

    private companion object {
        /**
         * Tope de anidamiento del árbol de campos. Un formulario real no pasa de dos o
         * tres niveles; el tope está para que un PDF con un ciclo no se lleve la pila
         * por delante.
         */
        const val PROFUNDIDAD_MAXIMA = 32

        /**
         * Lo que se considera «la misma fila» al ordenar por filas o columnas: doce
         * puntos, algo menos que la altura de una línea de texto normal.
         */
        const val ALTURA_DE_BANDA_PT = 12f
    }
}
