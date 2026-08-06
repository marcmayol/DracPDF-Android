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
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_AVISO_DESCARTAR
import com.marcmayol.dracpdf.ui.visor.TAG_AVISO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_BARRA_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_FORM_ANTERIOR
import com.marcmayol.dracpdf.ui.visor.TAG_FORM_CONTADOR
import com.marcmayol.dracpdf.ui.visor.TAG_FORM_SIGUIENTE
import com.marcmayol.dracpdf.ui.visor.TAG_MODO_CERRAR
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import com.marcmayol.dracpdf.ui.visor.tagCampo
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Inventario de la Fase 2: el modo de formulario.
 *
 * Además de comprobar que cada acción existe con su estado, este inventario verifica
 * lo contrario, que es la mitad que se olvida: **los controles del modo no existen
 * fuera del modo**. En el escritorio esa clase de bug —los botones de colocar una
 * firma visibles cuando no se está colocando ninguna— costó una corrección; aquí la
 * barra sale de un `when` sobre el modo, así que no puede pasar, y esto lo demuestra
 * desde la interfaz de verdad en lugar de fiarse del razonamiento.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase2Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    private fun visorCon(
        nombre: String,
        xfa: GeneradorFormularios.Xfa = GeneradorFormularios.Xfa.NO,
    ): VisorViewModel {
        val fichero = GeneradorFormularios.formulario(File(contexto.cacheDir, nombre), xfa)
        return visorDe(fichero, nombre)
    }

    /**
     * Los grafos que ha creado el test. Se cierran al terminar: un documento de MuPDF
     * que se queda abierto acaba en manos del recolector de basura, que lo destruye
     * desde su propio hilo y se lleva el proceso por delante.
     */
    private val grafos = mutableListOf<Grafo>()

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    private fun visorDe(
        fichero: File,
        nombre: String,
    ): VisorViewModel {
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        return VisorViewModel(
            grafo.casosDelVisor,
            grafo.registro,
            CachePaginas(PRESUPUESTO_PRUEBA),
        ).also { it.mostrar(estado.id) }
    }

    private fun pintar(modelo: VisorViewModel) {
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitForIdle()
    }

    private fun entrarEnFormulario(modelo: VisorViewModel) {
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitForIdle()
    }

    // ------------------------------------------------- el modo se abre cuando toca

    @Test
    fun con_formulario_el_destino_se_puede_pulsar() {
        val modelo = visorCon("inv2_acroform.pdf")
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }

        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsEnabled()
    }

    @Test
    fun sin_formulario_el_destino_se_ve_y_no_se_pulsa() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "inv2_liso.pdf"), paginas = 3)
        val modelo = visorDe(fichero, "inv2_liso.pdf")
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.formulario != null }

        // Se ve, y no se puede entrar: dentro no habría nada que rellenar.
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsNotEnabled()
    }

    @Test
    fun un_xfa_puro_avisa_y_no_deja_entrar() {
        val modelo = visorCon("inv2_xfa_puro.pdf", GeneradorFormularios.Xfa.PURO)
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.aviso != null }

        composicion.onNodeWithTag(TAG_AVISO_FORMULARIO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsNotEnabled()
    }

    @Test
    fun un_xfa_hibrido_avisa_pero_si_deja_entrar() {
        val modelo = visorCon("inv2_xfa_hibrido.pdf", GeneradorFormularios.Xfa.HIBRIDO)
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.aviso != null }

        composicion.onNodeWithTag(TAG_AVISO_FORMULARIO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsEnabled()

        // Y el aviso se puede quitar de en medio, que si no deja de leerse.
        composicion.onNodeWithTag(TAG_AVISO_DESCARTAR).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_AVISO_FORMULARIO).assertDoesNotExist()
    }

    @Test
    fun un_acroform_normal_no_avisa_de_nada() {
        val modelo = visorCon("inv2_sin_aviso.pdf")
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.formulario != null }

        composicion.onNodeWithTag(TAG_AVISO_FORMULARIO).assertDoesNotExist()
    }

    // ------------------------------------------- los controles son del modo, y sólo

    @Test
    fun fuera_del_modo_sus_controles_no_existen() {
        val modelo = visorCon("inv2_fuera.pdf")
        pintar(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }

        // No es que estén deshabilitados: es que no se han compuesto siquiera.
        composicion.onNodeWithTag(TAG_BARRA_FORMULARIO).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_MODO_CERRAR).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_FORM_CONTADOR).assertDoesNotExist()
        composicion.onNodeWithTag(tagCampo(IdCampo(0, 0))).assertDoesNotExist()
    }

    @Test
    fun dentro_del_modo_estan_la_barra_y_los_campos() {
        val modelo = visorCon("inv2_dentro.pdf")
        entrarEnFormulario(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.campos.value.containsKey(0) }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_BARRA_FORMULARIO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_FORM_CONTADOR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MODO_CERRAR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_MODO_CERRAR).assertIsEnabled()
        // Sin campo activo todavía no hay ninguno detrás, pero sí delante: «siguiente»
        // lleva al primero, que es lo que espera quien acaba de entrar al modo.
        composicion.onNodeWithTag(TAG_FORM_ANTERIOR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_FORM_SIGUIENTE).assertIsEnabled()
        // Y el overlay, que es lo que la tarea 2 viene a poner en pantalla.
        composicion.onNodeWithTag(tagCampo(IdCampo(0, 0))).assertIsDisplayed()
    }

    @Test
    fun al_salir_del_modo_se_van_con_el_sus_controles() {
        val modelo = visorCon("inv2_salir.pdf")
        entrarEnFormulario(modelo)
        composicion.onNodeWithTag(TAG_BARRA_FORMULARIO).assertIsDisplayed()

        composicion.onNodeWithTag(TAG_MODO_CERRAR).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_BARRA_FORMULARIO).assertDoesNotExist()
        composicion.onNodeWithTag(TAG_MODO_CERRAR).assertDoesNotExist()
        composicion.onNodeWithTag(tagCampo(IdCampo(0, 0))).assertDoesNotExist()
        // Y vuelve la barra de lectura, con su destino de formulario otra vez.
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).assertIsDisplayed()
    }

    @Test
    fun tocar_un_campo_lo_deja_activo() {
        val modelo = visorCon("inv2_activo.pdf")
        entrarEnFormulario(modelo)
        composicion.waitUntil(ESPERA_MS) { modelo.campos.value.containsKey(0) }
        composicion.waitForIdle()

        composicion.onNodeWithTag(tagCampo(IdCampo(0, 0))).performClick()
        composicion.waitForIdle()

        org.junit.Assert.assertEquals(IdCampo(0, 0), modelo.estado.value.campoActivo)
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val ESPERA_MS = 10_000L
    }
}
