package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfContenido
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfEdicion
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Editar el contenido de la página: imágenes y corrección de texto.
 *
 * El punto que de verdad importa del criterio F8 es **«el original no extraíble tras
 * corregir»**: tapar el texto viejo con un rectángulo lo dejaría intacto debajo y
 * cualquiera lo sacaría copiando. Aquí se comprueba pidiéndole al motor el texto de la
 * página después de corregir.
 */
@RunWith(AndroidJUnit4::class)
class EdicionDeContenidoTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var edicion: MuPdfEdicion
    private lateinit var contenido: MuPdfContenido
    private val abiertos = mutableListOf<IdDocumento>()

    @Before
    fun montarElMotor() {
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        edicion = MuPdfEdicion(sesiones)
        contenido = MuPdfContenido(sesiones, fuente)
    }

    @After
    fun cerrarTodo() {
        abiertos.toList().forEach { runCatching { repositorio.cerrar(it) } }
        abiertos.clear()
    }

    @Test
    fun una_imagen_a_toda_pagina_se_reconoce_como_lo_que_es() {
        val fichero = GeneradorFixtures.conImagenes(File(contexto.cacheDir, "escaneado.pdf"), paginas = 1)
        val id = abrir(fichero, "escaneado")

        val imagenes = edicion.imagenesDe(id, 0)

        assertEquals(1, imagenes.size)
        // Es el aviso que pide la fase: quitarla dejaría la hoja en blanco, porque el
        // documento **es** esa imagen.
        assertTrue("Una imagen a sangre debería avisar de que es la página entera", imagenes.single().esLaPaginaEntera)
    }

    @Test
    fun un_documento_de_texto_no_tiene_imagenes_que_quitar() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "sin-imagenes.pdf"), 1), "sin-imagenes")

        assertTrue(edicion.imagenesDe(id, 0).isEmpty())
    }

    @Test
    fun una_imagen_anadida_aparece_y_no_se_confunde_con_la_hoja() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "con-anadida.pdf"), paginas = 1)
        val id = abrir(fichero, "con-anadida")

        edicion.anadirImagen(id, 0, RectPt(100f, 300f, 260f, 420f), pngDePrueba())

        val imagenes = edicion.imagenesDe(id, 0)
        assertEquals(1, imagenes.size)
        assertFalse("Una imagen pequeña no es la página entera", imagenes.single().esLaPaginaEntera)
    }

    @Test
    fun quitar_una_imagen_la_saca_del_documento() {
        val fichero = GeneradorFixtures.conImagenes(File(contexto.cacheDir, "quitar-imagen.pdf"), paginas = 1)
        val id = abrir(fichero, "quitar-imagen")
        val imagen = edicion.imagenesDe(id, 0).single()

        edicion.quitarImagen(id, 0, imagen.marco)
        repositorio.guardarIncremental(id)
        repositorio.cerrar(id)
        abiertos.remove(id)

        assertTrue(edicion.imagenesDe(abrir(fichero, "quitar-imagen-2"), 0).isEmpty())
    }

    @Test
    fun tras_corregir_un_texto_el_original_ya_no_se_puede_sacar() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "corregir.pdf"), paginas = 1)
        val id = abrir(fichero, "corregir")
        assertTrue("El fixture debería traer el título", "Pagina 1 de 1" in contenido.textoDe(id, 0))

        val corregido = edicion.corregirTexto(id, 0, SOBRE_EL_TITULO, "Hoja 1", tamano = 20f)
        repositorio.guardarIncremental(id)
        repositorio.cerrar(id)
        abiertos.remove(id)

        assertTrue("La corrección debería caber", corregido)
        val texto = contenido.textoDe(abrir(fichero, "corregir-2"), 0)
        assertFalse("El texto viejo sigue ahí debajo: eso es taparlo, no corregirlo", "Pagina 1 de 1" in texto)
    }

    @Test
    fun un_texto_que_no_cabe_se_dice_en_vez_de_recortarlo() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "no-cabe.pdf"), paginas = 1)
        val id = abrir(fichero, "no-cabe")
        val original = contenido.textoDe(id, 0)

        val largo = "Esta corrección es larguísima y no hay manera de que quepa en una sola línea corta"
        val hecho = edicion.corregirTexto(id, 0, RectPt(72f, 90f, 160f, 110f), largo, tamano = 14f)

        assertFalse("Debería negarse en vez de recortar", hecho)
        // Y no ha tocado nada: negarse significa dejar el documento como estaba.
        assertEquals(original, contenido.textoDe(id, 0))
    }

    /** Un PNG diminuto de verdad, generado al vuelo: no se versionan binarios. */
    private fun pngDePrueba(): ByteArray {
        val mapa = android.graphics.Bitmap.createBitmap(LADO_PNG, LADO_PNG, android.graphics.Bitmap.Config.ARGB_8888)
        mapa.eraseColor(android.graphics.Color.rgb(200, 80, 60))
        return ByteArrayOutputStream()
            .also { mapa.compress(android.graphics.Bitmap.CompressFormat.PNG, CALIDAD, it) }
            .toByteArray()
    }

    private fun abrir(
        fichero: File,
        clave: String,
    ): IdDocumento {
        val id = IdDocumento(clave)
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, fichero.name), null)
        abiertos += id
        return id
    }

    private companion object {
        /** El título del fixture, en coordenadas de página (el motor cuenta desde arriba). */
        val SOBRE_EL_TITULO = RectPt(70f, 85f, 500f, 135f)
        const val LADO_PNG = 64
        const val CALIDAD = 100
    }
}
