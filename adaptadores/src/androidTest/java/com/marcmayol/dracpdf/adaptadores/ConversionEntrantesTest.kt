package com.marcmayol.dracpdf.adaptadores

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.StructuredText
import com.marcmayol.dracpdf.adaptadores.conversion.EntradasAPdf
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.ConvertirAPdf
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.EntradaAConvertir
import com.marcmayol.dracpdf.dominio.puertos.TipoDeEntrada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lo que entra al PDF: imágenes, texto, Markdown y HTML.
 *
 * Es la mitad entrante del criterio F9 bis —«las entrantes producen un PDF con tantas
 * páginas como ficheros o imágenes se dieron»—, y lo que vigila es exactamente eso: la
 * **cuenta de páginas**, que es la única promesa que la conversión hace en voz alta.
 *
 * El resultado se relee **con MuPDF**, no con lo que lo escribió. Un texto convertido
 * tiene que salir con letras de verdad dentro del PDF, no con dibujos que se le parezcan,
 * y la diferencia sólo se ve pidiéndole el texto a un motor que no participó en escribirlo.
 */
@RunWith(AndroidJUnit4::class)
class ConversionEntrantesTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var convertir: ConvertirAPdf

    @Before
    fun montar() {
        val salidas = SalidasDeHerramienta(contexto.contentResolver)
        val temporales = File(contexto.cacheDir, "entrantes")
        convertir = ConvertirAPdf(EntradasAPdf(contexto.contentResolver, salidas, temporales))
    }

    @Test
    fun cada_imagen_elegida_es_una_pagina() {
        val fotos = (1..IMAGENES).map { numero -> imagen("foto-$numero.png") }
        val destino = privado("tres-fotos.pdf")

        val paginas = convertir(fotos.map { EntradaAConvertir(it, TipoDeEntrada.IMAGEN) }, destino)

        assertEquals("Cada foto tenía que ser una página", IMAGENES, paginas)
        assertEquals(IMAGENES, paginasDelPdf(File(destino.identificador)))
    }

    @Test
    fun un_markdown_llega_al_pdf_con_su_texto() {
        val fuente = escribir("notas.md", "# Título de las notas\n\nUn párrafo con acentos y eñes.\n")
        val destino = privado("notas.pdf")

        val paginas = convertir(listOf(EntradaAConvertir(fuente, TipoDeEntrada.MARKDOWN)), destino)

        assertEquals(1, paginas)
        val leido = sinEspacios(textoDelPdf(File(destino.identificador)))
        assertTrue("El título no llegó al PDF", "Títulodelasnotas" in leido)
        assertTrue("El párrafo no llegó al PDF", "acentosyeñes" in leido)
    }

    @Test
    fun el_html_entra_por_su_marcado_y_no_por_sus_etiquetas() {
        val fuente = escribir("pagina.html", "<h1>Encabezado</h1><p>Cuerpo del documento.</p>")
        val destino = privado("pagina.pdf")

        convertir(listOf(EntradaAConvertir(fuente, TipoDeEntrada.HTML)), destino)

        // Las etiquetas son instrucciones, no contenido: verlas impresas significaría que
        // el fichero se trató como texto plano y que el marcado se perdió por el camino.
        val leido = textoDelPdf(File(destino.identificador))
        assertTrue("El texto del HTML no está", "Encabezado" in leido)
        assertFalse("Las etiquetas se imprimieron tal cual", "<h1>" in leido)
    }

    @Test
    fun mezclar_fotos_y_texto_produce_un_solo_documento() {
        val entradas =
            listOf(
                EntradaAConvertir(imagen("recibo.png"), TipoDeEntrada.IMAGEN),
                EntradaAConvertir(escribir("nota.txt", "Lo que había detrás del recibo."), TipoDeEntrada.TEXTO),
                EntradaAConvertir(imagen("vuelta.png"), TipoDeEntrada.IMAGEN),
            )
        val destino = privado("mezcla.pdf")

        // Quien elige seis cosas quiere un documento de seis páginas, no seis documentos.
        val paginas = convertir(entradas, destino)

        assertEquals(entradas.size, paginas)
        assertTrue("El texto de en medio se perdió", "detrás del recibo" in textoDelPdf(File(destino.identificador)))
    }

    @Test
    fun un_texto_vacio_no_deja_un_documento_sin_paginas() {
        val fuente = escribir("vacio.txt", "   \n\n  ")
        val destino = privado("vacio.pdf")

        val paginas = convertir(listOf(EntradaAConvertir(fuente, TipoDeEntrada.TEXTO)), destino)

        // Cero páginas es un documento que ningún lector abre: antes que escribir eso donde
        // el usuario esperaba el suyo, no se escribe nada y se devuelve la cuenta.
        assertEquals(0, paginas)
        assertFalse("Se escribió un PDF sin páginas", File(destino.identificador).exists())
    }

    @Test
    fun el_resultado_no_puede_ser_uno_de_los_ficheros_de_origen() {
        val fuente = escribir("propio.txt", "Da igual lo que ponga.")

        assertThrows(IllegalArgumentException::class.java) {
            convertir(listOf(EntradaAConvertir(fuente, TipoDeEntrada.TEXTO)), fuente)
        }
    }

    // -- Andamiaje --------------------------------------------------------------------

    private fun privado(nombre: String): OrigenDocumento.Privado {
        val fichero = File(contexto.cacheDir, nombre)
        fichero.delete()
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    private fun escribir(
        nombre: String,
        contenido: String,
    ): OrigenDocumento.Privado {
        val destino = privado(nombre)
        File(destino.identificador).writeText(contenido)
        return destino
    }

    /** Un PNG de verdad, con sus bytes y su cabecera: el motor no admite otra cosa. */
    private fun imagen(nombre: String): OrigenDocumento.Privado {
        val destino = privado(nombre)
        val mapa = Bitmap.createBitmap(LADO, LADO, Bitmap.Config.ARGB_8888)
        mapa.eraseColor(Color.CYAN)
        File(destino.identificador).outputStream().use { salida ->
            mapa.compress(Bitmap.CompressFormat.PNG, CALIDAD_PNG, salida)
        }
        mapa.recycle()
        return destino
    }

    private fun paginasDelPdf(fichero: File): Int {
        val documento = Document.openDocument(fichero.absolutePath)
        try {
            return documento.countPages()
        } finally {
            documento.destroy()
        }
    }

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
                    // Por debajo del espacio son controles que el motor cuela, no texto.
                    linea.chars?.filter { it.c >= MINIMO_IMPRIMIBLE }?.forEach { appendCodePoint(it.c) }
                    append('\n')
                }
            }
        }

    /**
     * El texto sin un solo espacio.
     *
     * El PDF parte las líneas donde le toca y lo que se quiere demostrar es que las letras
     * están, no dónde cae el corte.
     */
    private fun sinEspacios(texto: String) = texto.filterNot { it.isWhitespace() }

    private companion object {
        const val IMAGENES = 3
        const val LADO = 200
        const val CALIDAD_PNG = 100

        const val MINIMO_IMPRIMIBLE = 0x20
    }
}
