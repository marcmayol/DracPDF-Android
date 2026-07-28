package com.marcmayol.dracpdf.adaptadores.fixtures

import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.Rect
import java.io.File

/**
 * Fabrica los PDF de prueba. La regla de la casa dice que los fixtures se generan
 * por script y nunca se versionan como binarios; aquí el «script» es esto, y usa el
 * propio MuPDF para escribirlos.
 *
 * Que el motor que escribe el fixture sea el mismo que lo va a leer tiene una
 * ventaja concreta: un fallo del test no puede venir nunca de un PDF mal generado
 * por otra herramienta. Y evita que este repositorio dependa del entorno Python del
 * DracPDF de escritorio para poder probarse.
 */
object GeneradorFixtures {
    private const val ANCHO_A4 = 595f
    private const val ALTO_A4 = 842f

    /**
     * Un PDF con [paginas] páginas, cada una con su número escrito en grande y un
     * párrafo debajo, para que haya algo que rasterizar y algo que buscar.
     */
    fun documento(
        destino: File,
        paginas: Int,
        contrasena: String? = null,
    ): File {
        val pdf = PDFDocument()
        try {
            val fuente = pdf.addSimpleFont(Font("Helvetica"), Font.SIMPLE_ENCODING_LATIN)
            val recursos =
                pdf.newDictionary().apply {
                    put("Font", pdf.newDictionary().apply { put("F1", fuente) })
                }
            val caja = Rect(0f, 0f, ANCHO_A4, ALTO_A4)

            repeat(paginas) { indice ->
                val hoja = pdf.addPage(caja, 0, recursos, contenidoDe(indice + 1, paginas))
                pdf.insertPage(-1, hoja)
            }

            destino.parentFile?.mkdirs()
            pdf.save(destino.absolutePath, opcionesDe(contrasena))
        } finally {
            pdf.destroy()
        }
        return destino
    }

    private fun opcionesDe(contrasena: String?): String =
        if (contrasena == null) {
            ""
        } else {
            "encrypt=aes-256,user-password=$contrasena,owner-password=$contrasena"
        }

    /**
     * El contenido de una página, en el lenguaje de dibujo del PDF: el número de
     * página en 48 puntos y una línea de texto debajo.
     */
    private fun contenidoDe(
        numero: Int,
        total: Int,
    ): String =
        buildString {
            append("BT /F1 48 Tf 72 720 Td (Pagina $numero de $total) Tj ET\n")
            append("BT /F1 12 Tf 72 660 Td (DracPDF Android · fixture de la Fase 1) Tj ET\n")
            // Un marco, para que el render tenga tinta hasta los bordes y se note si
            // la escala se aplica mal.
            append("2 w 36 36 ${ANCHO_A4 - 72} ${ALTO_A4 - 72} re S\n")
        }
}
