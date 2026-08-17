package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfAnotaciones
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Las marcas sobre el documento, contra PDF de verdad.
 *
 * Lo que importa del criterio F8 es que **sobrevivan al fichero**: una anotación que
 * se ve mientras el documento está abierto y desaparece al guardarlo y reabrirlo no
 * es una anotación, es un dibujo. Por eso cada prueba guarda y vuelve a abrir.
 */
@RunWith(AndroidJUnit4::class)
class AnotacionesTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var anotaciones: MuPdfAnotaciones
    private val abiertos = mutableListOf<IdDocumento>()

    @Before
    fun montarElMotor() {
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        anotaciones = MuPdfAnotaciones(sesiones)
    }

    @After
    fun cerrarTodo() {
        abiertos.toList().forEach { runCatching { repositorio.cerrar(it) } }
        abiertos.clear()
    }

    @Test
    fun un_resaltado_guardado_sigue_ahi_al_reabrir() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "resaltar.pdf"), paginas = 2)
        val id = abrir(fichero, "resaltar")

        anotaciones.marcar(id, 0, TipoAnotacion.RESALTADO, listOf(SOBRE_EL_TITULO), ColorAnotacion.AMARILLO)
        repositorio.guardarIncremental(id)
        repositorio.cerrar(id)
        abiertos.remove(id)

        val releido = abrir(fichero, "resaltar-2")
        val marcas = anotaciones.listar(releido, 0)
        assertEquals(1, marcas.size)
        assertEquals(TipoAnotacion.RESALTADO, marcas.single().tipo)
        assertEquals(ColorAnotacion.AMARILLO, marcas.single().color)
    }

    @Test
    fun cada_tipo_de_marca_se_reconoce_por_lo_que_es() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "tipos.pdf"), paginas = 1)
        val id = abrir(fichero, "tipos")

        anotaciones.marcar(id, 0, TipoAnotacion.RESALTADO, listOf(SOBRE_EL_TITULO), ColorAnotacion.AMARILLO)
        anotaciones.marcar(id, 0, TipoAnotacion.SUBRAYADO, listOf(SOBRE_EL_PARRAFO), ColorAnotacion.AZUL)
        anotaciones.marcar(id, 0, TipoAnotacion.TACHADO, listOf(SOBRE_EL_PARRAFO), ColorAnotacion.ROSA)
        repositorio.guardarIncremental(id)

        val tipos = anotaciones.listar(id, 0).map { it.tipo }
        assertEquals(
            listOf(TipoAnotacion.RESALTADO, TipoAnotacion.SUBRAYADO, TipoAnotacion.TACHADO),
            tipos,
        )
    }

    @Test
    fun una_nota_conserva_lo_que_dice() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "nota.pdf"), paginas = 1)
        val id = abrir(fichero, "nota")

        anotaciones.anotar(id, 0, SOBRE_EL_TITULO, "Revisar esta cláusula", ColorAnotacion.VERDE)
        repositorio.guardarIncremental(id)
        repositorio.cerrar(id)
        abiertos.remove(id)

        val marcas = anotaciones.listar(abrir(fichero, "nota-2"), 0)
        assertEquals(TipoAnotacion.NOTA, marcas.single().tipo)
        assertEquals("Revisar esta cláusula", marcas.single().contenido)
    }

    @Test
    fun el_texto_escrito_sobre_la_pagina_se_guarda_con_sus_acentos() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "escribir.pdf"), paginas = 1)
        val id = abrir(fichero, "escribir")

        anotaciones.escribir(id, 0, SOBRE_EL_PARRAFO, "Añadido en el móvil", tamano = 14f)
        repositorio.guardarIncremental(id)
        repositorio.cerrar(id)
        abiertos.remove(id)

        val escrito = anotaciones.listar(abrir(fichero, "escribir-2"), 0).single()
        assertEquals(TipoAnotacion.TEXTO, escrito.tipo)
        assertEquals("Añadido en el móvil", escrito.contenido)
    }

    @Test
    fun borrar_una_marca_la_quita_del_fichero() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "borrar.pdf"), paginas = 1)
        val id = abrir(fichero, "borrar")
        anotaciones.marcar(id, 0, TipoAnotacion.RESALTADO, listOf(SOBRE_EL_TITULO), ColorAnotacion.AMARILLO)
        anotaciones.marcar(id, 0, TipoAnotacion.SUBRAYADO, listOf(SOBRE_EL_PARRAFO), ColorAnotacion.AZUL)

        anotaciones.borrar(id, 0, posicion = 0)
        repositorio.guardarIncremental(id)
        repositorio.cerrar(id)
        abiertos.remove(id)

        val queda = anotaciones.listar(abrir(fichero, "borrar-2"), 0)
        assertEquals(1, queda.size)
        assertEquals(TipoAnotacion.SUBRAYADO, queda.single().tipo)
    }

    @Test
    fun los_campos_de_formulario_no_se_cuentan_como_marcas() {
        // Un widget de formulario es una anotación para el formato, pero no es una
        // marca del usuario: si apareciera en la lista, borrarla se llevaría el campo.
        val fichero = File(contexto.cacheDir, "formulario-anotado.pdf")
        GeneradorFixtures.documento(fichero, paginas = 1)
        val id = abrir(fichero, "formulario-anotado")

        assertTrue(anotaciones.listar(id, 0).isEmpty())
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
        /**
         * Dos marcos sobre el texto del fixture, en coordenadas de página: el título
         * está arriba —el motor cuenta desde arriba, aunque el formato cuente desde
         * abajo— y el párrafo, justo debajo.
         */
        val SOBRE_EL_TITULO = RectPt(72f, 90f, 400f, 130f)
        val SOBRE_EL_PARRAFO = RectPt(72f, 170f, 400f, 190f)
    }
}
