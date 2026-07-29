package com.marcmayol.dracpdf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFirmas
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.firmas.FirmasViewModel
import com.marcmayol.dracpdf.ui.firmas.TAG_DIBUJAR_NUEVA
import com.marcmayol.dracpdf.ui.firmas.TAG_HOJA_DIBUJAR
import com.marcmayol.dracpdf.ui.firmas.TAG_HOJA_FIRMAS
import com.marcmayol.dracpdf.ui.firmas.TAG_LIENZO
import com.marcmayol.dracpdf.ui.firmas.TAG_SIN_FIRMAS
import com.marcmayol.dracpdf.ui.firmas.exportarFirma
import com.marcmayol.dracpdf.ui.firmas.tagFirma
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_ASA
import com.marcmayol.dracpdf.ui.visor.TAG_BARRA_COLOCACION
import com.marcmayol.dracpdf.ui.visor.TAG_COLOCACION
import com.marcmayol.dracpdf.ui.visor.TAG_COLOCACION_CANCELAR
import com.marcmayol.dracpdf.ui.visor.TAG_COLOCACION_CONFIRMAR
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FIRMAS
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Inventario de la Fase 3: la firma dibujada.
 *
 * Como en la Fase 2, se comprueban las dos mitades: que los controles del modo estén
 * mientras dura, y que **no existan** fuera de él. Los de colocar una firma son el
 * caso que el escritorio corrigió tarde, así que aquí se vigila desde el principio.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase3Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val grafos = mutableListOf<Grafo>()

    /**
     * La biblioteca de firmas vive en el almacenamiento privado de la aplicación y
     * **sobrevive a los tests**, que es justo lo que se quiere en producción y lo
     * último que se quiere aquí: el test de la biblioteca vacía la encontraría llena
     * de las firmas que dejó el test anterior.
     */
    @Before
    fun vaciarLaBiblioteca() {
        File(contexto.filesDir, "firmas").deleteRecursively()
    }

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    private class Montaje(
        val visor: VisorViewModel,
        val firmas: FirmasViewModel,
        val grafo: Grafo,
    )

    private fun montar(nombre: String): Montaje {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, nombre), paginas = 3)
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        val visor =
            VisorViewModel(grafo.casosDelVisor, grafo.registro, CachePaginas(PRESUPUESTO_PRUEBA))
                .also { it.mostrar(estado.id) }
        val firmas = FirmasViewModel(grafo.almacenFirmas)
        return Montaje(visor, firmas, grafo)
    }

    private fun pintar(montaje: Montaje) {
        composicion.setContent {
            TemaDracPDF {
                PantallaVisor(modelo = montaje.visor, alSalir = {}, firmas = montaje.firmas)
            }
        }
        composicion.waitForIdle()
    }

    /** Mete una firma en la biblioteca sin pasar por el lienzo. */
    private fun conFirmaGuardada(montaje: Montaje): Firma {
        val firma = montaje.grafo.almacenFirmas.guardar(GeneradorFirmas.png(), 240, 80, "De prueba")
        montaje.firmas.cargar()
        composicion.waitUntil(ESPERA_MS) {
            montaje.firmas.miniaturas.value
                .containsKey(firma.id.valor)
        }
        return firma
    }

    @Test
    fun el_destino_de_firmas_abre_la_biblioteca() {
        val montaje = montar("f3_biblioteca.pdf")
        pintar(montaje)

        composicion.onNodeWithTag(TAG_DESTINO_FIRMAS).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DESTINO_FIRMAS).assertIsEnabled()

        composicion.onNodeWithTag(TAG_DESTINO_FIRMAS).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_FIRMAS).assertIsDisplayed()
        // Sin firmas guardadas se dice, en vez de enseñar una lista vacía sin más.
        composicion.onNodeWithTag(TAG_SIN_FIRMAS).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DIBUJAR_NUEVA).assertIsDisplayed()
    }

    @Test
    fun desde_la_biblioteca_se_llega_al_lienzo() {
        val montaje = montar("f3_lienzo.pdf")
        pintar(montaje)

        composicion.onNodeWithTag(TAG_DESTINO_FIRMAS).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_DIBUJAR_NUEVA).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_DIBUJAR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_LIENZO).assertIsDisplayed()
    }

    @Test
    fun fuera_del_modo_los_controles_de_colocar_no_existen() {
        val montaje = montar("f3_fuera.pdf")
        pintar(montaje)

        // Ni deshabilitados: no se han compuesto.
        composicion.onNodeWithTag(TAG_BARRA_COLOCACION).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_COLOCACION_CONFIRMAR).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_COLOCACION_CANCELAR).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_COLOCACION).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_ASA).assertDoesNotExist()
    }

    @Test
    fun elegir_una_firma_entra_en_el_modo_de_colocacion() {
        val montaje = montar("f3_colocar.pdf")
        pintar(montaje)
        val firma = conFirmaGuardada(montaje)

        composicion.onNodeWithTag(TAG_DESTINO_FIRMAS).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(tagFirma(firma.id.valor)).performClick()
        composicion.waitForIdle()

        // La barra del modo, con sus dos controles siempre visibles mientras dura.
        composicion.onNodeWithTag(TAG_BARRA_COLOCACION).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_COLOCACION_CANCELAR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_COLOCACION_CONFIRMAR).assertIsDisplayed()
        // Y la firma flotando sobre la página, con su asa.
        composicion.onNodeWithTag(TAG_COLOCACION).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_ASA).assertIsDisplayed()

        assertNotNull(montaje.visor.estado.value.colocacion)
    }

    @Test
    fun cancelar_deja_el_documento_como_estaba() {
        val montaje = montar("f3_cancelar.pdf")
        pintar(montaje)
        val firma = conFirmaGuardada(montaje)
        montaje.visor.empezarAColocar(firma)
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_COLOCACION_CANCELAR).performClick()
        composicion.waitForIdle()

        assertNull("La colocación tenía que desaparecer", montaje.visor.estado.value.colocacion)
        // Lo importante: no se ha tocado el PDF, así que no hay nada que guardar.
        assertTrue("Cancelar no puede dejar cambios", !montaje.visor.estado.value.cambiosSinGuardar)
        composicion.onNodeWithTag(TAG_BARRA_COLOCACION).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_COLOCACION).assertDoesNotExist()
    }

    @Test
    fun confirmar_estampa_la_firma_y_marca_el_documento() {
        val montaje = montar("f3_confirmar.pdf")
        pintar(montaje)
        val firma = conFirmaGuardada(montaje)
        montaje.visor.empezarAColocar(firma)
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_COLOCACION_CONFIRMAR).performClick()
        composicion.waitUntil(ESPERA_MS) { montaje.visor.estado.value.cambiosSinGuardar }
        composicion.waitForIdle()

        assertNull(montaje.visor.estado.value.colocacion)
        assertTrue(montaje.visor.estado.value.cambiosSinGuardar)
        // Y los controles se van con el modo.
        composicion.onNodeWithTag(TAG_BARRA_COLOCACION).assertDoesNotExist()
    }

    @Test
    fun la_firma_nace_dentro_de_la_pagina_y_con_su_proporcion() {
        val montaje = montar("f3_marco.pdf")
        pintar(montaje)
        val firma = conFirmaGuardada(montaje)

        montaje.visor.empezarAColocar(firma)
        composicion.waitForIdle()

        val colocacion = montaje.visor.estado.value.colocacion!!
        val marco = colocacion.marco
        assertTrue("Tiene que caber en la página", marco.x0 >= 0f && marco.y0 >= 0f)
        assertTrue("Y no salirse por la derecha", marco.x1 <= ANCHO_A4 + 1f)
        // La proporción del PNG se respeta: si no, la firma saldría estirada.
        assertEquals(firma.proporcion, marco.alto / marco.ancho, 0.01f)
    }

    @Test
    fun el_lienzo_exporta_lo_dibujado_recortado_a_la_tinta() {
        // Sin interfaz: lo que se comprueba es el recorte, que es lo que hace que la
        // firma no salga diminuta dentro de un rectángulo de aire.
        val trazo = listOf(Offset(100f, 100f), Offset(150f, 120f), Offset(200f, 100f))
        val exportada = exportarFirma(listOf(trazo))

        assertNotNull(exportada)
        // Ancho de la tinta: 100 puntos más el margen del grosor a cada lado.
        assertTrue("Ancho inesperado: ${exportada!!.anchoPx}", exportada.anchoPx in 105..125)
        assertTrue("Alto inesperado: ${exportada.altoPx}", exportada.altoPx in 25..45)
        assertTrue("Tiene que ser un PNG con contenido", exportada.png.size > 100)
    }

    @Test
    fun un_lienzo_en_blanco_no_produce_firma() {
        assertNull(exportarFirma(emptyList()))
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val ESPERA_MS = 10_000L
        const val ANCHO_A4 = 595f
    }
}
