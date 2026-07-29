package com.marcmayol.dracpdf.adaptadores.fixtures

import com.artifex.mupdf.fitz.Font
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFObject
import com.artifex.mupdf.fitz.Rect
import java.io.File

/**
 * Fabrica los PDF de prueba **con formulario**, con la misma regla que
 * [GeneradorFixtures]: nada de binarios versionados, y el PDF lo escribe el mismo
 * motor que luego lo lee.
 *
 * El formulario que genera no es un ejemplo de juguete a propósito: trae un campo de
 * cada tipo del estándar, un grupo de radio montado como lo monta un formulario de
 * verdad —un campo padre con dos widgets colgando— y un campo de sólo lectura. Son
 * justo los casos donde un lector de formularios se equivoca: contar los widgets de
 * un radio como campos distintos, o dejar escribir en lo que el emisor bloqueó.
 */
object GeneradorFormularios {
    private const val ANCHO_A4 = 595f
    private const val ALTO_A4 = 842f

    /** Los nombres de campo del fixture, para que el test no los repita a mano. */
    const val CAMPO_NOMBRE = "nombre"
    const val CAMPO_DIRECCION = "direccion"
    const val CAMPO_ACEPTA = "acepta"
    const val CAMPO_SEXO = "sexo"
    const val CAMPO_PROVINCIA = "provincia"
    const val CAMPO_IDIOMA = "idioma"
    const val CAMPO_REFERENCIA = "referencia"

    /** Campos del formulario, contando el grupo de radio como uno solo. */
    const val CAMPOS = 7

    /** Widgets de la primera página: los siete campos, con el radio partido en dos. */
    const val WIDGETS_PRIMERA_PAGINA = 8

    val OPCIONES_PROVINCIA = listOf("Baleares", "Barcelona", "Madrid")
    val OPCIONES_IDIOMA = listOf("Catala", "Castellano", "English")

    /** Qué XFA lleva el documento, que es lo que decide si se puede rellenar. */
    enum class Xfa {
        /** Sólo AcroForm: el caso normal y el único plenamente rellenable. */
        NO,

        /** XFA con el AcroForm equivalente debajo. */
        HIBRIDO,

        /** XFA sin AcroForm: no hay nada que rellenar. */
        PURO,
    }

    /**
     * Un PDF de dos páginas: la primera con el formulario completo, la segunda sin un
     * solo campo, que es la que demuestra que preguntar por una página sin campos
     * devuelve una lista vacía y no los de la página anterior.
     */
    fun formulario(
        destino: File,
        xfa: Xfa = Xfa.NO,
    ): File {
        val pdf = PDFDocument()
        try {
            val fuente = pdf.addSimpleFont(Font("Helvetica"), Font.SIMPLE_ENCODING_LATIN)
            val recursos =
                pdf.newDictionary().apply {
                    put("Font", pdf.newDictionary().apply { put("F1", fuente) })
                }
            val caja = Rect(0f, 0f, ANCHO_A4, ALTO_A4)

            val primera = pdf.addPage(caja, 0, recursos, etiquetas())
            pdf.insertPage(-1, primera)
            val segunda = pdf.addPage(caja, 0, recursos, "BT /F1 14 Tf 72 760 Td (Sin campos) Tj ET\n")
            pdf.insertPage(-1, segunda)

            val campos = if (xfa == Xfa.PURO) pdf.newArray() else camposEn(pdf, primera)
            pdf.trailer
                .get("Root")
                .put("AcroForm", acroForm(pdf, campos, fuente, xfa))

            destino.parentFile?.mkdirs()
            pdf.save(destino.absolutePath, "")
        } finally {
            pdf.destroy()
        }
        return destino
    }

    /**
     * Mete un objeto en el documento y devuelve **una referencia indirecta** a él.
     *
     * No vale `addObject`: pese al nombre, el envoltorio Java devuelve el objeto ya
     * resuelto, no la referencia. Con objetos sueltos da igual, pero en cuanto dos se
     * apuntan —un campo padre a sus botones y cada botón a su padre— lo que queda es
     * un ciclo entre objetos directos, y MuPDF lo recorre en profundidad al colgarlo
     * del documento: desbordamiento de pila y SIGSEGV, sin excepción que capturar.
     */
    private fun indirecto(
        pdf: PDFDocument,
        contenido: PDFObject,
    ): PDFObject = pdf.createObject().also { it.writeObject(contenido) }

    private fun acroForm(
        pdf: PDFDocument,
        campos: PDFObject,
        fuente: PDFObject,
        xfa: Xfa,
    ): PDFObject {
        val acro =
            pdf.newDictionary().apply {
                put("Fields", campos)
                // La apariencia por defecto y sus recursos: sin esto, un visor que
                // regenere apariencias no sabe con qué fuente escribir lo que se teclea.
                put("DA", pdf.newString("/Helv 0 Tf 0 g"))
                put(
                    "DR",
                    pdf.newDictionary().apply {
                        put("Font", pdf.newDictionary().apply { put("Helv", fuente) })
                    },
                )
                if (xfa != Xfa.NO) put("XFA", pdf.addStream(XDP_MINIMO))
            }
        return indirecto(
            pdf,
            acro,
        )
    }

    /** Monta los campos, los cuelga de la página como anotaciones y los devuelve. */
    private fun camposEn(
        pdf: PDFDocument,
        pagina: PDFObject,
    ): PDFObject {
        val campos = pdf.newArray()
        val anotaciones = pdf.newArray()

        fun soltar(
            campo: PDFObject,
            vararg widgets: PDFObject,
        ) {
            campos.push(campo)
            widgets.forEach(anotaciones::push)
        }

        val texto = campoTexto(pdf, pagina, CAMPO_NOMBRE, "", fila(0))
        val direccion =
            campoTexto(pdf, pagina, CAMPO_DIRECCION, "", fila(1), banderas = MULTILINEA or OBLIGATORIO, maxLen = 40)
        val referencia = campoTexto(pdf, pagina, CAMPO_REFERENCIA, "ABC-123", fila(2), banderas = SOLO_LECTURA)
        val casilla = campoCasilla(pdf, pagina, CAMPO_ACEPTA, fila(3))
        val radio = grupoRadio(pdf, pagina, CAMPO_SEXO, fila(4), fila(5))
        val combo = campoEleccion(pdf, pagina, CAMPO_PROVINCIA, OPCIONES_PROVINCIA, fila(6), banderas = COMBO)
        val lista = campoEleccion(pdf, pagina, CAMPO_IDIOMA, OPCIONES_IDIOMA, fila(7), banderas = 0)

        soltar(texto, texto)
        soltar(direccion, direccion)
        soltar(referencia, referencia)
        soltar(casilla, casilla)
        soltar(radio.padre, *radio.widgets)
        soltar(combo, combo)
        soltar(lista, lista)

        pagina.put("Annots", anotaciones)
        return campos
    }

    private fun campoTexto(
        pdf: PDFDocument,
        pagina: PDFObject,
        nombre: String,
        valor: String,
        marco: Rect,
        banderas: Int = 0,
        maxLen: Int = 0,
    ): PDFObject =
        indirecto(
            pdf,
            widget(pdf, pagina, marco).apply {
                put("FT", pdf.newName("Tx"))
                put("T", pdf.newString(nombre))
                put("V", pdf.newString(valor))
                put("DA", pdf.newString("/Helv 11 Tf 0 g"))
                if (banderas != 0) put("Ff", banderas)
                if (maxLen > 0) put("MaxLen", maxLen)
            },
        )

    private fun campoCasilla(
        pdf: PDFDocument,
        pagina: PDFObject,
        nombre: String,
        marco: Rect,
    ): PDFObject =
        indirecto(
            pdf,
            widget(pdf, pagina, marco).apply {
                put("FT", pdf.newName("Btn"))
                put("T", pdf.newString(nombre))
                put("V", pdf.newName(APAGADO))
                put("AS", pdf.newName(APAGADO))
            },
        )

    /**
     * Un grupo de radio de verdad: un campo padre con el nombre y dos widgets sin
     * nombre propio colgando de él. Los widgets heredan el nombre del padre, que es
     * por lo que dos botones distintos se llaman igual y hace falta algo más que el
     * nombre para distinguirlos.
     */
    private fun grupoRadio(
        pdf: PDFDocument,
        pagina: PDFObject,
        nombre: String,
        primero: Rect,
        segundo: Rect,
    ): Radio {
        val padre =
            indirecto(
                pdf,
                pdf.newDictionary().apply {
                    put("FT", pdf.newName("Btn"))
                    put("T", pdf.newString(nombre))
                    put("Ff", RADIO)
                    put("V", pdf.newName(APAGADO))
                },
            )
        val botones =
            listOf(primero to "Si", segundo to "No").map { (marco, encendido) ->
                indirecto(
                    pdf,
                    widget(pdf, pagina, marco).apply {
                        put("Parent", padre)
                        put("AS", pdf.newName(APAGADO))
                        put(
                            "AP",
                            pdf.newDictionary().apply {
                                put(
                                    "N",
                                    pdf.newDictionary().apply {
                                        put(encendido, pdf.newDictionary())
                                        put(APAGADO, pdf.newDictionary())
                                    },
                                )
                            },
                        )
                    },
                )
            }
        padre.put("Kids", pdf.newArray().apply { botones.forEach(::push) })
        return Radio(padre, botones.toTypedArray())
    }

    private fun campoEleccion(
        pdf: PDFDocument,
        pagina: PDFObject,
        nombre: String,
        opciones: List<String>,
        marco: Rect,
        banderas: Int,
    ): PDFObject =
        indirecto(
            pdf,
            widget(pdf, pagina, marco).apply {
                put("FT", pdf.newName("Ch"))
                put("T", pdf.newString(nombre))
                put("V", pdf.newString(opciones.first()))
                put("DA", pdf.newString("/Helv 11 Tf 0 g"))
                put("Opt", pdf.newArray().apply { opciones.forEach { push(pdf.newString(it)) } })
                if (banderas != 0) put("Ff", banderas)
            },
        )

    /** Lo común a cualquier widget: es una anotación, tiene marco, y sabe su página. */
    private fun widget(
        pdf: PDFDocument,
        pagina: PDFObject,
        marco: Rect,
    ): PDFObject =
        pdf.newDictionary().apply {
            put("Type", pdf.newName("Annot"))
            put("Subtype", pdf.newName("Widget"))
            put("Rect", marco)
            put("P", pagina)
            // Bit 3 del estándar: imprimible. Sin esto, algunos visores no lo pintan.
            put("F", IMPRIMIBLE)
        }

    /** El hueco de la fila [indice], contando desde arriba de la página. */
    private fun fila(indice: Int): Rect {
        val arriba = ALTO_A4 - MARGEN - indice * PASO_FILA
        return Rect(MARGEN + SANGRIA, arriba - ALTO_CAMPO, ANCHO_A4 - MARGEN, arriba)
    }

    /** Las etiquetas impresas al lado de cada campo, para que el fixture se lea. */
    private fun etiquetas(): String =
        buildString {
            append("BT /F1 16 Tf 72 800 Td (Solicitud de prueba) Tj ET\n")
            listOf(
                CAMPO_NOMBRE,
                CAMPO_DIRECCION,
                CAMPO_REFERENCIA,
                CAMPO_ACEPTA,
                "$CAMPO_SEXO (si)",
                "$CAMPO_SEXO (no)",
                CAMPO_PROVINCIA,
                CAMPO_IDIOMA,
            ).forEachIndexed { indice, etiqueta ->
                val linea = ALTO_A4 - MARGEN - indice * PASO_FILA - ALTO_CAMPO + 4
                append("BT /F1 9 Tf $MARGEN $linea Td ($etiqueta) Tj ET\n")
            }
        }

    private class Radio(
        val padre: PDFObject,
        val widgets: Array<PDFObject>,
    )

    private const val MARGEN = 72f
    private const val SANGRIA = 90f
    private const val PASO_FILA = 40f
    private const val ALTO_CAMPO = 22f

    private const val APAGADO = "Off"
    private const val IMPRIMIBLE = 4

    // Banderas de campo del estándar, en los bits que les toca.
    private const val SOLO_LECTURA = 1
    private const val OBLIGATORIO = 1 shl 1
    private const val MULTILINEA = 1 shl 12
    private const val RADIO = 1 shl 15
    private const val COMBO = 1 shl 17

    /** Lo mínimo que hace que un PDF declare XFA sin traer un formulario entero. */
    private const val XDP_MINIMO =
        "<?xml version=\"1.0\"?><xdp:xdp xmlns:xdp=\"http://ns.adobe.com/xdp/\"></xdp:xdp>"
}
