package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.SeekableInputStream
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentos
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * El adaptador contra MuPDF de verdad: sin dobles, sobre PDF reales escritos por el
 * propio motor.
 */
@RunWith(AndroidJUnit4::class)
class MuPdfDocumentRepositoryTest {
    private lateinit var carpeta: File
    private lateinit var fuente: FuenteDocumentos
    private lateinit var repositorio: MuPdfDocumentRepository

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        carpeta = File(contexto.cacheDir, "fixtures").apply { mkdirs() }
        fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        repositorio = MuPdfDocumentRepository(SesionesMuPdf(fuente), fuente)
    }

    private fun assertCerca(
        esperado: Int,
        real: Int,
        tolerancia: Int = 2,
    ) {
        assertTrue(
            "Se esperaba $esperado ± $tolerancia y llegó $real",
            kotlin.math.abs(esperado - real) <= tolerancia,
        )
    }

    private fun origenDe(
        nombre: String,
        paginas: Int,
        contrasena: String? = null,
    ): OrigenDocumento {
        val fichero = GeneradorFixtures.documento(File(carpeta, nombre), paginas, contrasena)
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    @Test
    fun abre_un_pdf_y_cuenta_sus_paginas() {
        val id = IdDocumento("prueba-1")
        val documento = repositorio.abrir(id, origenDe("doce.pdf", 12))

        assertEquals(12, documento.paginas)
        assertEquals("doce.pdf", documento.nombre)
        repositorio.cerrar(id)
    }

    @Test
    fun el_tamano_de_pagina_sale_en_puntos_y_sin_rasterizar() {
        val id = IdDocumento("prueba-2")
        repositorio.abrir(id, origenDe("a4.pdf", 3))

        val tamano = repositorio.tamanoPagina(id, 0)

        assertEquals(595f, tamano.ancho, 1f)
        assertEquals(842f, tamano.alto, 1f)
        // Lo importante: preguntar el tamaño no cuenta como render.
        assertEquals(0, repositorio.paginasRenderizadas)
        repositorio.cerrar(id)
    }

    @Test
    fun renderiza_a_la_escala_pedida_con_cuatro_bytes_por_pixel() {
        val id = IdDocumento("prueba-3")
        repositorio.abrir(id, origenDe("render.pdf", 3))

        val aUno = repositorio.renderizar(id, 0, 1f)
        val aDos = repositorio.renderizar(id, 0, 2f)

        // Con tolerancia de un par de píxeles: MuPDF redondea la caja de la página a
        // píxeles enteros y el resultado puede quedarse a uno de distancia.
        assertCerca(595, aUno.ancho)
        assertCerca(842, aUno.alto)
        assertCerca(aUno.ancho * 2, aDos.ancho, tolerancia = 3)
        assertCerca(aUno.alto * 2, aDos.alto, tolerancia = 3)
        // RGBA: es justo lo que espera un Bitmap ARGB_8888 de Android.
        assertEquals(aUno.ancho * aUno.alto * 4, aUno.bytes)
        assertEquals(2, repositorio.paginasRenderizadas)
        repositorio.cerrar(id)
    }

    @Test
    fun el_render_lleva_tinta_y_no_es_una_hoja_en_blanco() {
        val id = IdDocumento("prueba-4")
        repositorio.abrir(id, origenDe("tinta.pdf", 1))

        val pagina = repositorio.renderizar(id, 0, 1f)

        // Se cuentan los píxeles que no son blancos: el fixture escribe un número en
        // 48 puntos y un marco, así que tiene que haber unos cuantos.
        var oscuros = 0
        var i = 0
        while (i < pagina.pixeles.size) {
            if (pagina.pixeles[i].toInt() and 0xFF < 128) oscuros++
            i += 4
        }
        assertTrue("El render salió en blanco: $oscuros píxeles con tinta", oscuros > 500)
        repositorio.cerrar(id)
    }

    @Test
    fun dos_paginas_distintas_dan_renders_distintos() {
        val id = IdDocumento("prueba-5")
        repositorio.abrir(id, origenDe("distintas.pdf", 5))

        val primera = repositorio.renderizar(id, 0, 0.5f)
        val tercera = repositorio.renderizar(id, 2, 0.5f)

        assertNotEquals(
            "Las páginas 1 y 3 han salido idénticas: se está renderizando siempre la misma",
            primera.pixeles.toList(),
            tercera.pixeles.toList(),
        )
        repositorio.cerrar(id)
    }

    @Test
    fun un_pdf_cifrado_pide_la_contrasena() {
        val origen = origenDe("cifrado.pdf", 2, contrasena = "abrete")

        assertThrows(ErrorDocumento.NecesitaContrasena::class.java) {
            repositorio.abrir(IdDocumento("prueba-6"), origen)
        }
    }

    @Test
    fun una_contrasena_incorrecta_no_abre_el_pdf_cifrado() {
        val origen = origenDe("cifrado2.pdf", 2, contrasena = "abrete")

        assertThrows(ErrorDocumento.ContrasenaIncorrecta::class.java) {
            repositorio.abrir(IdDocumento("prueba-7"), origen, "sesamo")
        }
    }

    @Test
    fun con_la_contrasena_correcta_el_pdf_cifrado_abre_y_renderiza() {
        val id = IdDocumento("prueba-8")
        val origen = origenDe("cifrado3.pdf", 2, contrasena = "abrete")

        val documento = repositorio.abrir(id, origen, "abrete")

        assertEquals(2, documento.paginas)
        assertTrue(repositorio.renderizar(id, 1, 0.5f).bytes > 0)
        repositorio.cerrar(id)
    }

    @Test
    fun un_fichero_que_no_es_pdf_se_rechaza_sin_llevarse_el_proceso() {
        val basura = File(carpeta, "basura.pdf").apply { writeText("esto no es un PDF, ni de lejos") }

        assertThrows(ErrorDocumento.NoSePuedeLeer::class.java) {
            repositorio.abrir(IdDocumento("prueba-9"), OrigenDocumento.Privado(basura.absolutePath, "basura.pdf"))
        }
    }

    @Test
    fun pedir_algo_de_un_documento_cerrado_es_un_error_de_dominio() {
        val id = IdDocumento("prueba-10")
        repositorio.abrir(id, origenDe("cerrado.pdf", 2))
        repositorio.cerrar(id)

        assertThrows(ErrorDocumento.NoEstaAbierto::class.java) { repositorio.renderizar(id, 0, 1f) }
    }

    @Test
    fun varios_documentos_abiertos_a_la_vez_no_se_pisan() {
        val uno = IdDocumento("multi-1")
        val otro = IdDocumento("multi-2")

        val documentoUno = repositorio.abrir(uno, origenDe("uno.pdf", 4))
        val documentoOtro = repositorio.abrir(otro, origenDe("otro.pdf", 9))

        assertEquals(4, documentoUno.paginas)
        assertEquals(9, documentoOtro.paginas)
        // Y siguen respondiendo bien después de haber abierto el segundo.
        assertEquals(595f, repositorio.tamanoPagina(uno, 3).ancho, 1f)
        assertEquals(595f, repositorio.tamanoPagina(otro, 8).ancho, 1f)

        repositorio.cerrarTodo()
        assertThrows(ErrorDocumento.NoEstaAbierto::class.java) { repositorio.tamanoPagina(uno, 0) }
    }

    @Test
    fun aguanta_peticiones_desde_varios_hilos_a_la_vez() {
        // Éste es el test que justifica el hilo por documento. Sin serializar, MuPDF
        // no lanza una excepción: se lleva el proceso por delante con un SIGSEGV, así
        // que si esto termina y da verde, la serialización está funcionando.
        val id = IdDocumento("hilos")
        repositorio.abrir(id, origenDe("hilos.pdf", 20))

        val hilos = 6
        val porHilo = 10
        val pistoletazo = CountDownLatch(1)
        val ejecutor = Executors.newFixedThreadPool(hilos)

        repeat(hilos) { h ->
            ejecutor.submit {
                pistoletazo.await()
                repeat(porHilo) { i ->
                    repositorio.renderizar(id, (h + i) % 20, 0.25f)
                    repositorio.tamanoPagina(id, (h + i) % 20)
                }
            }
        }
        pistoletazo.countDown()
        ejecutor.shutdown()

        assertTrue(ejecutor.awaitTermination(120, TimeUnit.SECONDS))
        assertEquals(hilos * porHilo, repositorio.paginasRenderizadas)
        repositorio.cerrar(id)
    }

    @Test
    fun el_flujo_de_saf_se_posiciona_sin_copiar_el_fichero() {
        // Comprobación directa del SeekableInputStream: MuPDF salta al final del
        // fichero para leer el índice de objetos antes que ninguna otra cosa, así que
        // si seek(SEEK_END) mintiera, ningún PDF abriría.
        val fichero = GeneradorFixtures.documento(File(carpeta, "flujo.pdf"), 2)
        val flujo = fuente.abrir(OrigenDocumento.Privado(fichero.absolutePath, "flujo.pdf"))

        try {
            val tamano = flujo.seek(0, SeekableInputStream.SEEK_END)
            assertEquals(fichero.length(), tamano)

            flujo.seek(0, SeekableInputStream.SEEK_SET)
            assertEquals(0L, flujo.position())

            val cabecera = ByteArray(5)
            flujo.read(cabecera)
            assertEquals("%PDF-", String(cabecera))
            assertEquals(5L, flujo.position())
        } finally {
            (flujo as? AutoCloseable)?.close()
        }
    }
}
