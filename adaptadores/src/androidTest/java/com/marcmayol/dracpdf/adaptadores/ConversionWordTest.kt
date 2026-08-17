package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.StructuredText
import com.marcmayol.dracpdf.adaptadores.conversion.ComponedorPdfAndroid
import com.marcmayol.dracpdf.adaptadores.conversion.ConversorWordDocx
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.ConvertirAWord
import com.marcmayol.dracpdf.dominio.casos.ConvertirWordAPdf
import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Las dos direcciones de Word, contra ficheros de verdad.
 *
 * Es el criterio F9 bis del plan, y son dos pruebas distintas a propósito:
 *
 * - **La ida y vuelta** se hace con el escritor y el lector de la casa: lo que la
 *   aplicación escribe, la aplicación lo vuelve a leer con el mismo texto y los mismos
 *   títulos. Demuestra que el `.docx` que se produce es coherente.
 * - **El documento ajeno** se escribe a mano en el propio test, con las manías de Word
 *   —prefijos raros, el espacio de nombres redeclarado a mitad, palabras partidas en
 *   varias carreras, `xml:space="preserve"` y estilos con el identificador traducido—.
 *   Usar el escritor propio para fabricarlo probaría que sabemos leernos a nosotros
 *   mismos, que es lo que no interesa.
 *
 * El PDF compuesto se relee **con MuPDF**, no con el mismo motor que lo escribió: es la
 * única manera de demostrar que lleva texto de verdad y no dibujos de letras.
 */
@RunWith(AndroidJUnit4::class)
class ConversionWordTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var conversor: ConversorWordDocx
    private lateinit var componedor: ComponedorPdfAndroid

    @Before
    fun montar() {
        val salidas = SalidasDeHerramienta(contexto.contentResolver)
        val temporales = File(contexto.cacheDir, "conversion")
        conversor = ConversorWordDocx(contexto.contentResolver, salidas, temporales)
        componedor = ComponedorPdfAndroid(salidas, temporales)
    }

    // -- Ida y vuelta ---------------------------------------------------------------

    @Test
    fun un_docx_escrito_por_la_aplicacion_se_vuelve_a_leer_igual() {
        val destino = privado("ida-y-vuelta.docx")

        assertTrue(ConvertirAWord(conversor)(DOCUMENTO, destino))
        val leido = conversor.leer(destino)

        assertEquals(
            "Los títulos no sobrevivieron a la ida y vuelta",
            DOCUMENTO.bloques.filterIsInstance<BloqueDeTexto.Titulo>(),
            leido.bloques.filterIsInstance<BloqueDeTexto.Titulo>(),
        )
        assertEquals(
            "El texto de los párrafos cambió",
            DOCUMENTO.bloques.filterIsInstance<BloqueDeTexto.Parrafo>(),
            leido.bloques.filterIsInstance<BloqueDeTexto.Parrafo>(),
        )
    }

    @Test
    fun las_tablas_sobreviven_la_ida_y_vuelta() {
        val destino = privado("tabla.docx")
        conversor.escribir(DOCUMENTO, destino)

        val tablas = conversor.leer(destino).bloques.filterIsInstance<BloqueDeTexto.Tabla>()

        assertEquals(1, tablas.size)
        assertEquals(listOf(listOf("Concepto", "Importe"), listOf("Café con leche", "1,20 €")), tablas.single().filas)
    }

    @Test
    fun el_salto_de_pagina_se_escribe_y_se_reconoce() {
        val destino = privado("saltos.docx")
        conversor.escribir(DOCUMENTO, destino)

        assertEquals(
            1,
            conversor
                .leer(destino)
                .bloques
                .filterIsInstance<BloqueDeTexto.SaltoDePagina>()
                .size,
        )
    }

    @Test
    fun el_paquete_lleva_las_piezas_que_word_exige() {
        val destino = privado("piezas.docx")
        conversor.escribir(DOCUMENTO, destino)

        val piezas = ZipFile(File(destino.identificador)).use { zip -> zip.entries().toList().map { it.name } }

        listOf(
            "[Content_Types].xml",
            "_rels/.rels",
            "word/_rels/document.xml.rels",
            "word/document.xml",
            "word/styles.xml",
        ).forEach { pieza -> assertTrue("Falta $pieza en el paquete: $piezas", pieza in piezas) }
    }

    @Test
    fun un_documento_sin_texto_no_escribe_ningun_fichero() {
        val destino = privado("vacio.docx")

        // Un PDF escaneado da esto: cero bloques con letras. Escribir el .docx igual
        // dejaría al usuario con un documento en blanco y sin explicación.
        assertFalse(ConvertirAWord(conversor)(DocumentoEstructurado(emptyList()), destino))
        assertFalse(File(destino.identificador).exists())
    }

    // -- El documento ajeno ----------------------------------------------------------

    @Test
    fun un_docx_ajeno_se_lee_con_sus_titulos_y_su_texto() {
        val ajeno = escribirDocxAjeno("ajeno-lectura.docx")

        val leido = conversor.leer(ajeno)

        assertEquals(
            listOf(
                BloqueDeTexto.Titulo("Informe anual", 1),
                BloqueDeTexto.Titulo("Segunda parte", 2),
                BloqueDeTexto.Titulo("Tercera parte", 3),
            ),
            leido.bloques.filterIsInstance<BloqueDeTexto.Titulo>(),
        )
        val parrafos = leido.bloques.filterIsInstance<BloqueDeTexto.Parrafo>().map { it.texto }
        // La palabra venía partida en tres carreras y el espacio, en un `w:t` aparte.
        assertTrue("Las carreras partidas no se juntaron: $parrafos", "Conversión de eñes" in parrafos)
        assertTrue("No se respetó xml:space: $parrafos", parrafos.any { it == "Con espacios dentro" })
        // Texto borrado con control de cambios: el autor lo quitó, no vuelve.
        assertTrue("Resucitó texto borrado: $parrafos", parrafos.none { "BORRADO" in it })
    }

    @Test
    fun un_docx_ajeno_se_convierte_a_pdf_sin_perder_texto() {
        val ajeno = escribirDocxAjeno("ajeno-a-pdf.docx")
        val pdf = privado("ajeno.pdf")

        val paginas = ConvertirWordAPdf(conversor, componedor)(ajeno, pdf)

        assertTrue("No se escribió ninguna página", paginas >= 1)
        val texto = sinEspacios(textoDelPdf(File(pdf.identificador)))
        listOf(
            "Informeanual",
            "Conversióndeeñes",
            "Segundaparte",
            "Terceraparte",
            "Concepto",
            "Caféconleche",
            "1,20€",
        ).forEach { esperado ->
            assertTrue("Falta «$esperado» en el PDF: $texto", esperado in texto)
        }
    }

    @Test
    fun el_texto_largo_se_parte_en_paginas() {
        val destino = privado("largo.pdf")
        val bloques =
            (1..PARRAFOS_LARGOS).map { numero ->
                BloqueDeTexto.Parrafo("Párrafo número $numero. $RELLENO")
            }

        val paginas = componedor.componer(DocumentoEstructurado(bloques), destino)

        assertTrue("Todo eso no cabe en $paginas página(s)", paginas > 1)
        assertEquals("El PDF dice tener otras páginas", paginas, paginasDelPdf(File(destino.identificador)))
    }

    @Test
    fun los_acentos_y_las_enes_llegan_al_pdf() {
        val destino = privado("acentos.pdf")
        val frase = "Añoranza, cigüeña, ñandú y «El Niño»"

        componedor.componer(DocumentoEstructurado(listOf(BloqueDeTexto.Parrafo(frase))), destino)

        val texto = sinEspacios(textoDelPdf(File(destino.identificador)))
        assertTrue("El PDF no conservó los acentos: $texto", sinEspacios(frase) in texto)
    }

    // -- Andamiaje --------------------------------------------------------------------

    private fun privado(nombre: String): OrigenDocumento.Privado {
        val fichero = File(contexto.cacheDir, nombre)
        fichero.delete()
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    /**
     * Un `.docx` como los que produce Word, escrito aquí a mano.
     *
     * Trae, a propósito, todo lo que rompe a un lector ingenuo: el XML en una sola línea,
     * el espacio de nombres con prefijo `wpml` y redeclarado con otro prefijo a mitad del
     * cuerpo, palabras partidas en varias carreras con un `w:proofErr` colado en medio,
     * `xml:space="preserve"`, texto borrado con control de cambios, y estilos con el
     * identificador que pone el Word español —«Ttulo1», sin la í— en vez del inglés.
     */
    private fun escribirDocxAjeno(nombre: String): OrigenDocumento.Privado {
        val destino = privado(nombre)
        ZipOutputStream(File(destino.identificador).outputStream()).use { zip ->
            pieza(zip, "[Content_Types].xml", TIPOS_AJENOS)
            pieza(zip, "_rels/.rels", RELACIONES_AJENAS)
            pieza(zip, "word/styles.xml", ESTILOS_AJENOS)
            pieza(zip, "word/document.xml", DOCUMENTO_AJENO)
        }
        return destino
    }

    private fun pieza(
        zip: ZipOutputStream,
        nombre: String,
        contenido: String,
    ) {
        zip.putNextEntry(ZipEntry(nombre))
        zip.write(contenido.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** El texto que MuPDF encuentra dentro del PDF, que es la prueba de que hay texto. */
    private fun textoDelPdf(fichero: File): String {
        val documento = Document.openDocument(fichero.absolutePath)
        try {
            return buildString {
                for (numero in 0 until documento.countPages()) {
                    val hoja = documento.loadPage(numero)
                    val estructurado = hoja.toStructuredText()
                    try {
                        append(textoDe(estructurado))
                    } finally {
                        estructurado.destroy()
                        hoja.destroy()
                    }
                }
            }
        } finally {
            documento.destroy()
        }
    }

    private fun textoDe(estructurado: StructuredText): String =
        buildString {
            estructurado.blocks?.forEach { bloque ->
                bloque.lines?.forEach { linea ->
                    // Los caracteres de control que el motor cuela entre páginas no son
                    // texto y aquí sólo estorbarían.
                    linea.chars?.forEach { caracter -> if (caracter.c >= 0x20) appendCodePoint(caracter.c) }
                    append('\n')
                }
            }
        }

    private fun paginasDelPdf(fichero: File): Int {
        val documento = Document.openDocument(fichero.absolutePath)
        try {
            return documento.countPages()
        } finally {
            documento.destroy()
        }
    }

    /**
     * El texto sin un solo espacio.
     *
     * Comparar así evita el falso negativo tonto: el PDF parte las líneas donde le toca
     * y lo que se quiere demostrar es que las letras están, no dónde cae el corte.
     */
    private fun sinEspacios(texto: String) = texto.filterNot { it.isWhitespace() }

    private companion object {
        const val PARRAFOS_LARGOS = 40

        val RELLENO =
            "Este párrafo existe para gastar papel y obligar a que el texto no quepa en " +
                "una sola hoja, con acentos y con eñes para que el corte tenga que " +
                "medirse con la letra de verdad y no a ojo."

        val DOCUMENTO =
            DocumentoEstructurado(
                listOf(
                    BloqueDeTexto.Titulo("Informe anual", 1),
                    BloqueDeTexto.Parrafo("Español: ñandú, cigüeña y «comillas» de las de verdad."),
                    BloqueDeTexto.Titulo("Detalle", 2),
                    BloqueDeTexto.Parrafo("Segundo párrafo, con dos  espacios seguidos en medio."),
                    BloqueDeTexto.Tabla(
                        listOf(listOf("Concepto", "Importe"), listOf("Café con leche", "1,20 €")),
                        pagina = 1,
                    ),
                    BloqueDeTexto.SaltoDePagina(2),
                    BloqueDeTexto.Titulo("Anexo", 3),
                    BloqueDeTexto.Parrafo("Última línea del documento."),
                ),
            )

        const val NS_W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        const val PROLOGO = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

        val TIPOS_AJENOS =
            PROLOGO +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" " +
                "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd." +
                "openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "</Types>"

        val RELACIONES_AJENAS =
            PROLOGO +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/" +
                "relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>"

        /**
         * Los estilos como los escribe Word en español, con los tres casos que hay que
         * saber resolver: el que trae `w:outlineLvl` (manda el número), el que sólo trae
         * el nombre canónico inglés, y el que ni siquiera se llama como un título salvo
         * por su nombre traducido.
         */
        val ESTILOS_AJENOS =
            listOf(
                PROLOGO,
                "<w:styles xmlns:w=\"$NS_W\">",
                "<w:style w:type=\"paragraph\" w:styleId=\"Ttulo1\"><w:name w:val=\"heading 1\"/>",
                "<w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr></w:style>",
                "<w:style w:type=\"paragraph\" w:styleId=\"Ttulo2\"><w:name w:val=\"heading 2\"/></w:style>",
                "<w:style w:type=\"paragraph\" w:styleId=\"EstiloRaro\"><w:name w:val=\"Título 3\"/></w:style>",
                "</w:styles>",
            ).joinToString("")

        val DOCUMENTO_AJENO =
            listOf(
                PROLOGO,
                "<wpml:document xmlns:wpml=\"$NS_W\"><wpml:body>",
                // Título con el identificador traducido y el texto en dos carreras.
                "<wpml:p><wpml:pPr><wpml:pStyle wpml:val=\"Ttulo1\"/></wpml:pPr>",
                "<wpml:r><wpml:t>Informe</wpml:t></wpml:r>",
                "<wpml:r><wpml:t xml:space=\"preserve\"> anual</wpml:t></wpml:r></wpml:p>",
                // Palabra partida por la mitad, con el corrector colado entre carreras.
                "<wpml:p><wpml:r><wpml:t>Conver</wpml:t></wpml:r>",
                "<wpml:proofErr wpml:type=\"spellStart\"/>",
                "<wpml:r><wpml:t xml:space=\"preserve\">sión de e</wpml:t></wpml:r>",
                "<wpml:r><wpml:t>ñes</wpml:t></wpml:r>",
                "<wpml:del><wpml:r><wpml:delText> y esto está BORRADO</wpml:delText></wpml:r></wpml:del>",
                "</wpml:p>",
                // El espacio de nombres, redeclarado con otro prefijo a mitad del cuerpo.
                "<x:p xmlns:x=\"$NS_W\"><x:pPr><x:pStyle x:val=\"Ttulo2\"/></x:pPr>",
                "<x:r><x:t>Segunda parte</x:t></x:r></x:p>",
                "<wpml:p><wpml:r><wpml:lastRenderedPageBreak/>",
                "<wpml:t xml:space=\"preserve\">Con espacios dentro </wpml:t></wpml:r></wpml:p>",
                "<wpml:p><wpml:pPr><wpml:pStyle wpml:val=\"EstiloRaro\"/></wpml:pPr>",
                "<wpml:r><wpml:t>Tercera parte</wpml:t></wpml:r></wpml:p>",
                // Una tabla, con la celda partida en carreras como todo lo demás.
                "<wpml:tbl><wpml:tr>",
                "<wpml:tc><wpml:p><wpml:r><wpml:t>Concepto</wpml:t></wpml:r></wpml:p></wpml:tc>",
                "<wpml:tc><wpml:p><wpml:r><wpml:t>Importe</wpml:t></wpml:r></wpml:p></wpml:tc>",
                "</wpml:tr><wpml:tr>",
                "<wpml:tc><wpml:p><wpml:r><wpml:t>Café</wpml:t></wpml:r>",
                "<wpml:r><wpml:t xml:space=\"preserve\"> con leche</wpml:t></wpml:r></wpml:p></wpml:tc>",
                "<wpml:tc><wpml:p><wpml:r><wpml:t>1,20 €</wpml:t></wpml:r></wpml:p></wpml:tc>",
                "</wpml:tr></wpml:tbl>",
                "<wpml:p/>",
                "<wpml:sectPr><wpml:pgSz wpml:w=\"11906\" wpml:h=\"16838\"/></wpml:sectPr>",
                "</wpml:body></wpml:document>",
            ).joinToString("")
    }
}
