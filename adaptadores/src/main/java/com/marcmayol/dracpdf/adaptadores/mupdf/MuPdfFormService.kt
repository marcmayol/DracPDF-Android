package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.PDFPage
import com.artifex.mupdf.fitz.PDFWidget
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.FormatoTexto
import com.marcmayol.dracpdf.dominio.modelo.Formulario
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
            Formulario(tipo = tipo, campos = if (acroForm) contarCampos(pdf) else 0)
        }

    override fun camposDePagina(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario> =
        sesiones.en(id) { documento ->
            val hoja = documento.loadPage(pagina) as? PDFPage ?: return@en emptyList()
            try {
                hoja.widgets.orEmpty().mapIndexed { indice, widget -> campoDe(widget, pagina, indice) }
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
    private fun contarCampos(pdf: PDFDocument): Int {
        val acroForm = pdf.trailer.get("Root")?.get("AcroForm") ?: return 0
        val raiz = acroForm.get("Fields") ?: return 0
        return contarEn(raiz, profundidad = 0)
    }

    private fun contarEn(
        nodos: PDFObject,
        profundidad: Int,
    ): Int {
        if (profundidad > PROFUNDIDAD_MAXIMA) return 0
        var total = 0
        for (indice in 0 until nodos.size()) {
            val nodo = nodos.get(indice)?.resolve() ?: continue
            val hijos = camposHijos(nodo)
            total += if (hijos != null) contarEn(hijos, profundidad + 1) else 1
        }
        return total
    }

    /**
     * Los `/Kids` de un nodo, si son campos de verdad. Un `/Kids` de widgets es la
     * apariencia del campo padre —los dos botones de un radio— y no campos nuevos.
     */
    private fun camposHijos(nodo: PDFObject): PDFObject? {
        val hijos = nodo.get("Kids")
        if (hijos == null || hijos.isNull || hijos.size() == 0) return null
        val primero = hijos.get(0)?.resolve() ?: return null
        val esWidget = primero.get("Subtype")?.asName() == "Widget"
        return if (esWidget) null else hijos
    }

    private fun campoDe(
        widget: PDFWidget,
        pagina: Int,
        indice: Int,
    ): CampoFormulario {
        val nombre = widget.name.orEmpty()
        val etiqueta = widget.label?.takeIf { it.isNotBlank() && it != nombre }
        val caja = widget.bounds
        val esTexto = widget.fieldType == PDFWidget.TYPE_TEXT
        return CampoFormulario(
            nombre = nombre,
            pagina = pagina,
            indice = indice,
            tipo = tipoDe(widget.fieldType),
            valor = widget.value.orEmpty(),
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

    private companion object {
        /**
         * Tope de anidamiento del árbol de campos. Un formulario real no pasa de dos o
         * tres niveles; el tope está para que un PDF con un ciclo no se lleve la pila
         * por delante.
         */
        const val PROFUNDIDAD_MAXIMA = 32
    }
}
