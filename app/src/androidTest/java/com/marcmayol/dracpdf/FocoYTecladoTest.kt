package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.Direccion
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_FORM_ANTERIOR
import com.marcmayol.dracpdf.ui.visor.TAG_FORM_SIGUIENTE
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import com.marcmayol.dracpdf.ui.visor.tagCampo
import com.marcmayol.dracpdf.ui.visor.tagEditor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El foco entre campos: la barra y el teclado tienen que llevar al mismo sitio.
 *
 * Dos caminos y un solo orden —el que declara el documento— es lo que hace que
 * rellenar un impreso no se convierta en un juego de adivinar dónde saltará el
 * cursor. Aquí se comprueba que coinciden, que los botones no mienten sobre si hay
 * adónde ir, y que el campo enfocado se trae a la vista.
 */
@RunWith(AndroidJUnit4::class)
class FocoYTecladoTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

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

    /** El modelo con el documento abierto y nada pintado todavía. */
    private fun soloModelo(nombre: String): VisorViewModel {
        val fichero = GeneradorFormularios.formulario(File(contexto.cacheDir, nombre))
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()
        return VisorViewModel(
            grafo.casosDelVisor,
            grafo.registro,
            CachePaginas(PRESUPUESTO_PRUEBA),
        ).also { it.mostrar(estado.id) }
    }

    private fun enFormulario(nombre: String): VisorViewModel {
        val modelo = soloModelo(nombre)

        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }
        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitUntil(ESPERA_MS) { modelo.campos.value.containsKey(0) }
        // Y al índice de páginas con campos, no sólo a la primera página. «Siguiente»
        // navega sobre ese índice: pulsarlo antes de tenerlo hecho no lleva a ningún
        // campo y la espera del test se agota. Con la máquina descansada el índice
        // llegaba a tiempo por los pelos, y por eso esto sólo fallaba en la tanda
        // larga.
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.indiceCompleto }
        composicion.waitForIdle()
        return modelo
    }

    private fun editables(modelo: VisorViewModel): List<CampoFormulario> =
        modelo.campos.value
            .getValue(0)
            .filter { it.esEditable }

    private fun esperarActivo(
        modelo: VisorViewModel,
        campo: CampoFormulario,
    ) = composicion.waitUntil(ESPERA_MS) { modelo.estado.value.campoActivo == campo.id }

    @Test
    fun el_boton_siguiente_recorre_los_campos_en_el_orden_del_documento() {
        val modelo = enFormulario("foco_orden.pdf")
        val orden = editables(modelo)

        composicion.onNodeWithTag(TAG_FORM_SIGUIENTE).performClick()
        esperarActivo(modelo, orden[0])
        assertEquals(orden[0].id, modelo.estado.value.campoActivo)

        composicion.onNodeWithTag(TAG_FORM_SIGUIENTE).performClick()
        esperarActivo(modelo, orden[1])
        assertEquals(orden[1].id, modelo.estado.value.campoActivo)

        composicion.onNodeWithTag(TAG_FORM_ANTERIOR).performClick()
        esperarActivo(modelo, orden[0])
        assertEquals(orden[0].id, modelo.estado.value.campoActivo)
    }

    @Test
    fun la_tecla_siguiente_del_teclado_lleva_al_mismo_campo_que_la_barra() {
        val modelo = enFormulario("foco_teclado.pdf")
        val orden = editables(modelo)
        val primero = orden.first { it.tipo == com.marcmayol.dracpdf.dominio.modelo.TipoCampo.TEXTO }

        composicion.onNodeWithTag(tagCampo(primero.id)).performClick()
        esperarActivo(modelo, primero)
        composicion.waitForIdle()

        // La acción del teclado es la misma que el botón de la barra: no hay dos
        // recorridos, hay uno.
        composicion.onNodeWithTag(tagEditor(primero.id)).performImeAction()
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.campoActivo != primero.id }

        val siguienteEsperado = orden[orden.indexOfFirst { it.id == primero.id } + 1]
        assertEquals(siguienteEsperado.id, modelo.estado.value.campoActivo)
    }

    @Test
    fun en_el_primer_campo_no_hay_anterior_y_en_el_ultimo_no_hay_siguiente() {
        val modelo = enFormulario("foco_extremos.pdf")
        val orden = editables(modelo)

        composicion.onNodeWithTag(tagCampo(orden.first().id)).performClick()
        esperarActivo(modelo, orden.first())
        composicion.waitForIdle()
        // El fixture tiene todos los campos en la primera página y ninguno en la
        // segunda, así que en el primero no hay nada detrás.
        composicion.onNodeWithTag(TAG_FORM_ANTERIOR).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_FORM_SIGUIENTE).assertIsEnabled()

        composicion.onNodeWithTag(tagCampo(orden.last().id)).performClick()
        esperarActivo(modelo, orden.last())
        // Se espera al índice: hasta que se sabe que las demás páginas no traen
        // campos, el botón no puede afirmar que no hay siguiente.
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.indiceCompleto }
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_FORM_ANTERIOR).assertIsEnabled()
        composicion.onNodeWithTag(TAG_FORM_SIGUIENTE).assertIsNotEnabled()
    }

    @Test
    fun el_indice_de_paginas_con_campos_solo_apunta_las_que_los_tienen() {
        val modelo = enFormulario("foco_indice.pdf")

        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.indiceCompleto }

        // El fixture tiene dos páginas y sólo la primera trae campos.
        assertEquals(listOf(0), modelo.estado.value.paginasConCampos)
    }

    @Test
    fun al_moverse_a_un_campo_se_pide_traerlo_a_la_vista() {
        // Sin pintar la pantalla: con ella, la propia lista atiende la solicitud y la
        // borra, y el test estaría corriendo una carrera contra el scroll. Lo que se
        // comprueba es que se pide, y con qué.
        val modelo = soloModelo("foco_scroll.pdf")
        // Sin esperar a saber que hay formulario, entrar al modo no haría nada: es el
        // propio modelo el que se niega a abrir un modo sin nada que rellenar.
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.hayFormulario }
        modelo.entrarEnFormulario()
        composicion.waitUntil(ESPERA_MS) { modelo.campos.value.containsKey(0) }

        modelo.irACampo(Direccion.SIGUIENTE)
        composicion.waitUntil(ESPERA_MS) { modelo.desplazarA.value != null }

        val peticion = modelo.desplazarA.value
        assertNotNull("Tenía que pedirse traer el campo a la vista", peticion)
        assertEquals(0, peticion!!.pagina)
        assertTrue(
            "La altura del campo va como fracción de la página y llegó ${peticion.fraccionY}",
            peticion.fraccionY in 0f..1f,
        )
    }

    @Test
    fun lo_escrito_sobrevive_a_que_el_editor_se_desmonte() {
        // Es lo que pasa al girar el teléfono con un campo a medio rellenar: Compose
        // desmonta el editor y lo vuelve a montar. El borrador se vuelca al documento
        // al desmontarse, así que no se pierde nada.
        val modelo = enFormulario("foco_rotacion.pdf")
        val destino = editables(modelo).first { it.tipo == com.marcmayol.dracpdf.dominio.modelo.TipoCampo.TEXTO }

        composicion.onNodeWithTag(tagCampo(destino.id)).performClick()
        esperarActivo(modelo, destino)
        composicion.waitForIdle()
        composicion.onNodeWithTag(tagEditor(destino.id)).performTextReplacement("A medio escribir")
        // Antes de desmontar hay que dejar que el texto llegue al editor. Sin esta
        // espera se desmonta mientras el evento va de camino, el borrador se vuelca
        // vacío y lo que el test acaba comprobando es una carrera suya, no la de la
        // aplicación.
        composicion.waitForIdle()

        // Salir del modo desmonta el overlay entero, igual que hace una rotación.
        modelo.salirDelFormulario()
        composicion.waitForIdle()
        composicion.waitUntil(ESPERA_MS) { modelo.estado.value.cambiosSinGuardar }

        composicion.onNodeWithTag(TAG_DESTINO_FORMULARIO).performClick()
        composicion.waitUntil(ESPERA_MS) { modelo.campos.value.containsKey(0) }
        composicion.waitUntil(ESPERA_MS) {
            modelo.campos.value
                .getValue(0)
                .any { it.nombre == destino.nombre && it.valor == "A medio escribir" }
        }

        assertEquals(
            "A medio escribir",
            modelo.campos.value
                .getValue(0)
                .first { it.nombre == destino.nombre }
                .valor,
        )
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val ESPERA_MS = 15_000L
    }
}
