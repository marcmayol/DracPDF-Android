package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.HojaHerramientas
import com.marcmayol.dracpdf.ui.herramientas.TAG_AVISO_FIRMADO
import com.marcmayol.dracpdf.ui.herramientas.TAG_HOJA_HERRAMIENTAS
import com.marcmayol.dracpdf.ui.herramientas.disponible
import com.marcmayol.dracpdf.ui.iconos.IconosLadon
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_HERRAMIENTAS
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Inventario de la Fase 6: la caja de herramientas.
 *
 * Además de que cada acción esté con su estado, aquí se vigilan las dos cosas que el
 * criterio nombra por escrito: que la conversión sea **una sola entrada** y que los
 * iconos sean los del paquete y no los prestados de la maqueta.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase6Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val grafos = mutableListOf<Grafo>()

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    @Test
    fun el_destino_de_herramientas_ya_se_puede_pulsar() {
        val modelo = visorCon("herramientas.pdf")
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitForIdle()

        // Estuvo apagado desde la Fase 1 esperando a esta.
        composicion.onNodeWithTag(TAG_DESTINO_HERRAMIENTAS).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DESTINO_HERRAMIENTAS).assertIsEnabled()

        composicion.onNodeWithTag(TAG_DESTINO_HERRAMIENTAS).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_HOJA_HERRAMIENTAS).assertIsDisplayed()
    }

    @Test
    fun la_rejilla_trae_las_nueve_con_su_estado() {
        composicion.setContent { TemaDracPDF { HojaHerramientas(alElegir = {}, alCerrar = {}) } }
        composicion.waitForIdle()

        val deEstaFase =
            listOf(
                Herramienta.ORGANIZAR,
                Herramienta.UNIR,
                Herramienta.DIVIDIR,
                Herramienta.CONVERTIR,
                Herramienta.COMPRIMIR,
                Herramienta.PROTEGER,
            )
        deEstaFase.forEach { herramienta ->
            composicion.onNodeWithTag(herramienta.tag).assertIsDisplayed()
            composicion.onNodeWithTag(herramienta.tag).assertIsEnabled()
        }

        // Las de fases posteriores se ven y no se pulsan, como el resto de la aplicación.
        listOf(Herramienta.COMPARTIR, Herramienta.IMPRIMIR, Herramienta.ANOTACIONES).forEach {
            composicion.onNodeWithTag(it.tag).assertIsDisplayed()
            composicion.onNodeWithTag(it.tag).assertIsNotEnabled()
        }
    }

    @Test
    fun hay_una_sola_entrada_de_conversion() {
        // El escritorio separaba «Exportar a texto» de «Convertir a Word» y lo corrigió
        // en la 0.4.0 porque se leían como operaciones distintas. Aquí no se
        // reintroduce, y esto lo vigila.
        val deConversion = Herramienta.entries.filter { "onvertir" in it.etiqueta || "xportar" in it.etiqueta }

        assertEquals("Hay más de una entrada de conversión: $deConversion", 1, deConversion.size)
        assertEquals(Herramienta.CONVERTIR, deConversion.single())
    }

    @Test
    fun los_iconos_son_los_del_paquete_y_no_los_prestados_de_la_maqueta() {
        // La maqueta está dibujada con iconos prestados porque se hizo antes de tener
        // el set completo. Estos tres son los que se confundían.
        assertEquals(IconosLadon.convertir, Herramienta.CONVERTIR.icono)
        assertEquals(IconosLadon.comprimir, Herramienta.COMPRIMIR.icono)
        assertEquals(IconosLadon.bloquear, Herramienta.PROTEGER.icono)

        // Y ninguna usa los prestados: rotar, guardar y verificar tienen otro trabajo.
        val prestados = setOf(IconosLadon.rotar, IconosLadon.guardar, IconosLadon.verificar)
        val coladas = Herramienta.entries.filter { it.icono in prestados }
        assertTrue("Siguen usándose iconos prestados en $coladas", coladas.isEmpty())
    }

    @Test
    fun con_un_documento_firmado_se_apaga_lo_que_reescribiria_el_pdf() {
        composicion.setContent {
            TemaDracPDF { HojaHerramientas(alElegir = {}, alCerrar = {}, documentoFirmado = true) }
        }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_AVISO_FIRMADO).assertIsDisplayed()
        listOf(Herramienta.ORGANIZAR, Herramienta.UNIR, Herramienta.DIVIDIR, Herramienta.COMPRIMIR)
            .forEach { composicion.onNodeWithTag(it.tag).assertIsNotEnabled() }

        // Convertir sigue viva: sólo lee. Negarla confundiría «no romper la firma» con
        // «no dejar mirar».
        composicion.onNodeWithTag(Herramienta.CONVERTIR.tag).assertIsEnabled()
        assertTrue(Herramienta.CONVERTIR.disponible(documentoFirmado = true))
    }

    private fun visorCon(nombre: String): VisorViewModel {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, nombre), paginas = 3)
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        return VisorViewModel(grafo.casosDelVisor, grafo.registro, CachePaginas(PRESUPUESTO_PRUEBA))
            .also { it.mostrar(estado.id) }
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
    }
}
