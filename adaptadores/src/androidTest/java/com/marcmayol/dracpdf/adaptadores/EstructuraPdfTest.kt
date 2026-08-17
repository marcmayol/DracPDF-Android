package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfEstructura
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * La deducción de la estructura contra PDF de verdad.
 *
 * Es la mitad del criterio F9 que no tiene que ver con escribir ficheros: que los títulos
 * se reconocen por su tamaño, que las tablas se adivinan por las posiciones y se declaran
 * aproximadas, y que un escaneado se detecta como lo que es en vez de dar un documento
 * vacío por bueno.
 */
@RunWith(AndroidJUnit4::class)
class EstructuraPdfTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var estructura: MuPdfEstructura
    private val abiertos = mutableListOf<IdDocumento>()

    @Before
    fun montarElMotor() {
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        estructura = MuPdfEstructura(sesiones)
    }

    @After
    fun cerrarTodo() {
        abiertos.forEach(repositorio::cerrar)
        abiertos.clear()
    }

    @Test
    fun el_titulo_se_deduce_del_tamano_de_la_letra() {
        val id = abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "titulos.pdf")))

        val documento = estructura.estructuraDe(id)

        val titulos = documento.bloques.filterIsInstance<BloqueDeTexto.Titulo>()
        assertEquals(listOf(GeneradorFixtures.TITULO_CON_TABLA), titulos.map { it.texto })
        // Nivel 1 porque es lo más grande del documento, no porque mida 24 puntos.
        assertEquals(1, titulos.single().nivel)
    }

    @Test
    fun el_parrafo_no_se_confunde_con_un_titulo() {
        val id = abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "parrafos.pdf")))

        val parrafos = estructura.estructuraDe(id).bloques.filterIsInstance<BloqueDeTexto.Parrafo>()

        assertTrue(
            "El párrafo no salió como párrafo: $parrafos",
            parrafos.any { GeneradorFixtures.PARRAFO_CON_TABLA in it.texto },
        )
    }

    @Test
    fun la_tabla_se_adivina_celda_a_celda_y_se_declara_aproximada() {
        val id = abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "tabla.pdf")))

        val tablas = estructura.estructuraDe(id).bloques.filterIsInstance<BloqueDeTexto.Tabla>()

        assertEquals(1, tablas.size)
        assertEquals(GeneradorFixtures.TABLA_ESPERADA, tablas.single().filas)
        assertEquals(0, tablas.single().pagina)
        // Sin `find_tables` en este binding no hay otra manera que las posiciones, y eso
        // se dice: es para lo que existe el campo.
        assertTrue("Una tabla deducida por posición es aproximada", tablas.single().aproximada)
    }

    @Test
    fun el_texto_de_la_tabla_no_sale_tambien_como_parrafos() {
        val id = abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "sin-duplicar.pdf")))

        val parrafos = estructura.estructuraDe(id).bloques.filterIsInstance<BloqueDeTexto.Parrafo>()

        assertTrue("«Grapas» está en la tabla y también suelto: $parrafos", parrafos.none { "Grapas" in it.texto })
    }

    @Test
    fun un_escaneado_no_tiene_texto_que_sacar() {
        val id = abrir(GeneradorFixtures.conImagenes(File(contexto.cacheDir, "escaneado.pdf"), paginas = 2))

        val documento = estructura.estructuraDe(id)

        // Un PDF que son fotos de papel no da ni una letra, y el modelo sabe decirlo.
        assertTrue("Un documento de puras imágenes tendría que salir vacío", documento.vacio)
    }

    @Test
    fun las_paginas_se_separan_por_su_corte() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "cortes.pdf"), paginas = 3))

        val documento = estructura.estructuraDe(id)

        // Tres páginas son dos cortes: ni uno delante ni uno detrás, que no separarían nada.
        assertEquals(2, documento.bloques.count { it is BloqueDeTexto.SaltoDePagina })
        assertFalse(documento.vacio)
    }

    private fun abrir(fichero: File): IdDocumento {
        val id = IdDocumento(fichero.name)
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, fichero.name), null)
        abiertos += id
        return id
    }
}
