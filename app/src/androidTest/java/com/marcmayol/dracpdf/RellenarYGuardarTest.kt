package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_FORM_GUARDAR
import com.marcmayol.dracpdf.ui.visor.TAG_HOJA_OPCIONES
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import com.marcmayol.dracpdf.ui.visor.tagCampo
import com.marcmayol.dracpdf.ui.visor.tagEditor
import com.marcmayol.dracpdf.ui.visor.tagOpcion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Rellenar desde la interfaz de verdad y guardar.
 *
 * Los tests del adaptador ya demuestran que un valor escrito sobrevive al fichero;
 * lo que se comprueba aquí es lo otro: que **tocar** el campo en pantalla llega hasta
 * ahí. Entre una cosa y otra caben todos los cables mal conectados del mundo.
 */
@RunWith(AndroidJUnit4::class)
class RellenarYGuardarTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    private class Montaje(
        val modelo: VisorViewModel,
        val grafo: Grafo,
        val fichero: File,
    )

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

    private fun montar(nombre: String): Montaje {
        val fichero = GeneradorFormularios.formulario(File(contexto.cacheDir, nombre))
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        val modelo =
            VisorViewModel(
                grafo.casosDelVisor,
                grafo.registro,
                CachePaginas(PRESUPUESTO_PRUEBA),
            ).also { it.mostrar(estado.id) }
        return Montaje(modelo, grafo, fichero)
    }

    private fun enFormulario(nombre: String): Montaje {
        val montaje = montar(nombre)
        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = montaje.modelo, alSalir = {}) } }
        composicion.waitUntil(ESPERA_MS) { montaje.modelo.estado.value.hayFormulario }
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitUntil(ESPERA_MS) {
            montaje.modelo.campos.value
                .containsKey(0)
        }
        composicion.waitForIdle()
        return montaje
    }

    private fun campo(
        montaje: Montaje,
        nombre: String,
    ): CampoFormulario =
        montaje.modelo.campos.value
            .getValue(0)
            .first { it.nombre == nombre }

    @Test
    fun escribir_en_un_campo_y_salir_de_el_lo_guarda_en_el_documento() {
        val montaje = enFormulario("ui_texto.pdf")
        val destino = campo(montaje, GeneradorFormularios.CAMPO_NOMBRE)

        composicion.onNodeWithTag(tagCampo(destino.id)).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(tagEditor(destino.id)).performTextReplacement("Marc Mayol")

        // Salir del campo es lo que dispara la escritura: se activa otro.
        val otro = campo(montaje, GeneradorFormularios.CAMPO_DIRECCION)
        composicion.onNodeWithTag(tagCampo(otro.id)).performClick()
        composicion.waitUntil(ESPERA_MS) {
            campo(montaje, GeneradorFormularios.CAMPO_NOMBRE).valor == "Marc Mayol"
        }

        assertEquals("Marc Mayol", campo(montaje, GeneradorFormularios.CAMPO_NOMBRE).valor)
        assertTrue("El documento tiene que quedar marcado", montaje.modelo.estado.value.cambiosSinGuardar)
    }

    @Test
    fun tocar_una_casilla_la_marca_sin_mas_pasos() {
        val montaje = enFormulario("ui_casilla.pdf")
        val casilla = campo(montaje, GeneradorFormularios.CAMPO_ACEPTA)

        composicion.onNodeWithTag(tagCampo(casilla.id)).performClick()
        composicion.waitUntil(ESPERA_MS) { campo(montaje, GeneradorFormularios.CAMPO_ACEPTA).marcado }

        assertTrue(campo(montaje, GeneradorFormularios.CAMPO_ACEPTA).marcado)
    }

    @Test
    fun tocar_un_radio_elige_ese_y_solo_ese() {
        val montaje = enFormulario("ui_radio.pdf")
        val botones =
            montaje.modelo.campos.value
                .getValue(0)
                .filter { it.tipo == TipoCampo.RADIO }

        composicion.onNodeWithTag(tagCampo(botones.last().id)).performClick()
        composicion.waitUntil(ESPERA_MS) {
            montaje.modelo.campos.value
                .getValue(0)
                .any { it.tipo == TipoCampo.RADIO && it.marcado }
        }

        val despues =
            montaje.modelo.campos.value
                .getValue(0)
                .filter { it.tipo == TipoCampo.RADIO }
        assertEquals(1, despues.count { it.marcado })
        assertTrue("El elegido tiene que ser el que se tocó", despues.last().marcado)
    }

    @Test
    fun un_combo_abre_su_lista_y_guarda_la_opcion() {
        val montaje = enFormulario("ui_combo.pdf")
        val combo = campo(montaje, GeneradorFormularios.CAMPO_PROVINCIA)
        val elegida = GeneradorFormularios.OPCIONES_PROVINCIA.last()

        composicion.onNodeWithTag(tagCampo(combo.id)).performClick()
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_HOJA_OPCIONES).assertExists()

        composicion.onNodeWithTag(tagOpcion(elegida)).performClick()
        composicion.waitUntil(ESPERA_MS) {
            campo(montaje, GeneradorFormularios.CAMPO_PROVINCIA).valor == elegida
        }

        assertEquals(elegida, campo(montaje, GeneradorFormularios.CAMPO_PROVINCIA).valor)
    }

    @Test
    fun un_campo_bloqueado_no_se_deja_tocar() {
        val montaje = enFormulario("ui_bloqueado.pdf")
        val referencia = campo(montaje, GeneradorFormularios.CAMPO_REFERENCIA)

        composicion.onNodeWithTag(tagCampo(referencia.id)).assertIsNotEnabled()
        // Y no se le pone editor encima aunque el toque llegara por otro camino.
        composicion.onNodeWithTag(tagEditor(referencia.id)).assertDoesNotExist()
    }

    @Test
    fun guardar_escribe_en_el_fichero_y_deja_de_ofrecerse() {
        val montaje = enFormulario("ui_guardar.pdf")
        val tamanoAntes = montaje.fichero.length()

        composicion.onNodeWithTag(TAG_FORM_GUARDAR).assertIsNotEnabled()

        val casilla = campo(montaje, GeneradorFormularios.CAMPO_ACEPTA)
        composicion.onNodeWithTag(tagCampo(casilla.id)).performClick()
        composicion.waitUntil(ESPERA_MS) { montaje.modelo.estado.value.cambiosSinGuardar }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_FORM_GUARDAR).assertIsEnabled()
        composicion.onNodeWithTag(TAG_FORM_GUARDAR).performClick()
        composicion.waitUntil(ESPERA_MS) { !montaje.modelo.estado.value.cambiosSinGuardar }
        composicion.waitForIdle()

        assertTrue(
            "El fichero tenía que crecer con la revisión nueva: ${montaje.fichero.length()} vs $tamanoAntes",
            montaje.fichero.length() > tamanoAntes,
        )
        // Sin cambios pendientes, guardar deja de ofrecerse: repetirlo sólo dejaría
        // una revisión vacía en el fichero.
        composicion.onNodeWithTag(TAG_FORM_GUARDAR).assertIsNotEnabled()
    }

    @Test
    fun al_salir_del_modo_el_campo_activo_se_suelta() {
        val montaje = enFormulario("ui_salir.pdf")
        val destino = campo(montaje, GeneradorFormularios.CAMPO_NOMBRE)

        composicion.onNodeWithTag(tagCampo(destino.id)).performClick()
        composicion.waitForIdle()
        assertEquals(IdCampo(0, destino.indice), montaje.modelo.estado.value.campoActivo)

        montaje.modelo.salirDelFormulario()
        composicion.waitForIdle()

        assertEquals(null, montaje.modelo.estado.value.campoActivo)
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val ESPERA_MS = 10_000L
    }
}
