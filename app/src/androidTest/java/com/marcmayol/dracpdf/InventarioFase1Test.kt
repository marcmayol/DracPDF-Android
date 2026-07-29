package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.inicio.HojaContrasena
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.TAG_ABRIR
import com.marcmayol.dracpdf.ui.inicio.TAG_ACEPTAR_CONTRASENA
import com.marcmayol.dracpdf.ui.inicio.TAG_CAMPO_CONTRASENA
import com.marcmayol.dracpdf.ui.inicio.TAG_CANCELAR_CONTRASENA
import com.marcmayol.dracpdf.ui.inicio.TAG_DRAGON
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_INICIO
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_ATRAS
import com.marcmayol.dracpdf.ui.visor.TAG_BUSCAR
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FIRMAS
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_FORMULARIO
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_HERRAMIENTAS
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_HOJA_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_MENU
import com.marcmayol.dracpdf.ui.visor.TAG_PILDORA
import com.marcmayol.dracpdf.ui.visor.TAG_TAB_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_TAB_MINIATURAS
import com.marcmayol.dracpdf.ui.visor.TAG_TITULO
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import com.marcmayol.dracpdf.ui.visor.tagMiniatura
import com.marcmayol.dracpdf.ui.visor.tagPagina
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Inventario de acciones de la Fase 1.
 *
 * Es la red de seguridad contra la fase «cumplida» con la integración a medias: cada
 * acción declarada aquí tiene que existir **en la interfaz de verdad**, con el estado
 * y la condición que dice la tabla. Nada se comprueba llamando a métodos internos: si
 * una acción no está en pantalla, este test no la encuentra y la fase no está hecha.
 *
 * Las acciones de fases posteriores también están inventariadas, y **deshabilitadas**:
 * una acción que desaparece deja al usuario buscándola, así que se ve y no se pulsa.
 */
@RunWith(AndroidJUnit4::class)
class InventarioFase1Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    /** Una acción de la interfaz, con su destino y la condición que la gobierna. */
    private data class Accion(
        val tag: String,
        val nombre: String,
        val destino: String,
        val condicion: String,
        val habilitada: Boolean = true,
    )

    // ---------------------------------------------------------------- pantalla inicio

    private val inicio =
        listOf(
            Accion(TAG_ABRIR, "Abrir PDF", "selector del sistema (OPEN_DOCUMENT)", "siempre"),
            Accion(TAG_MENU_INICIO, "⋮", "menú de inicio", "contenido pedido al diseño", habilitada = false),
        )

    @Test
    fun inventario_de_la_pantalla_de_inicio() {
        composicion.setContent { TemaDracPDF { PantallaInicio(alAbrirPdf = {}) } }

        inicio.forEach { accion ->
            composicion.onNodeWithTag(accion.tag).assertExisteCon(accion)
        }
        // La marca, que no es una acción pero sí parte de la pantalla vacía.
        composicion.onNodeWithTag(TAG_DRAGON).assertIsDisplayed()
    }

    // ----------------------------------------------------------------- pantalla visor

    private val visor =
        listOf(
            Accion(TAG_ATRAS, "Atrás", "cierra el documento y vuelve a inicio", "siempre"),
            Accion(TAG_BUSCAR, "Buscar", "Fase 7", "deshabilitada en la Fase 1", habilitada = false),
            Accion(TAG_MENU, "Documentos abiertos", "hoja de documentos abiertos", "siempre"),
            Accion(TAG_DESTINO_INDICE, "Índice", "hoja de índice y miniaturas", "siempre"),
            Accion(
                TAG_DESTINO_FORMULARIO,
                "Formulario",
                "Fase 2",
                "deshabilitada; y siempre con FIRMADO",
                habilitada = false,
            ),
            Accion(
                TAG_DESTINO_HERRAMIENTAS,
                "Herramientas",
                "Fase 6",
                "deshabilitada en la Fase 1",
                habilitada = false,
            ),
            Accion(TAG_DESTINO_FIRMAS, "Firmas", "Fase 4", "deshabilitada en la Fase 1", habilitada = false),
        )

    @Test
    fun inventario_del_visor_con_un_documento_abierto() {
        val modelo = visorConDocumento("inventario.pdf", paginas = 6)

        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitForIdle()

        visor.forEach { accion ->
            composicion.onNodeWithTag(accion.tag).assertExisteCon(accion)
        }

        // Lo que no es pulsable pero tiene que estar: nombre del documento, píldora de
        // página y la primera página dibujada.
        composicion.onNodeWithTag(TAG_TITULO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_PILDORA).assertIsDisplayed()
        composicion.onNodeWithTag(tagPagina(0)).assertIsDisplayed()
    }

    // ------------------------------------------------------------------- hoja índice

    private val hojaIndice =
        listOf(
            Accion(TAG_TAB_MINIATURAS, "Miniaturas", "rejilla de páginas", "siempre; activa por defecto"),
            Accion(
                TAG_TAB_INDICE,
                "Índice",
                "Fase 7 (outline del documento)",
                "deshabilitada en la Fase 1",
                habilitada = false,
            ),
        )

    @Test
    fun inventario_de_la_hoja_de_indice() {
        val modelo = visorConDocumento("inventario_hoja.pdf", paginas = 8)

        composicion.setContent { TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) } }
        composicion.waitForIdle()
        composicion.onNodeWithTag(TAG_DESTINO_INDICE).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_INDICE).assertIsDisplayed()
        hojaIndice.forEach { composicion.onNodeWithTag(it.tag).assertIsDisplayed() }
        // Las pestañas se dibujan siempre; la de índice sin poder usarse todavía.
        composicion.onNodeWithTag(tagMiniatura(0)).assertIsDisplayed()
    }

    // --------------------------------------------------------------- hoja contraseña

    private val hojaContrasena =
        listOf(
            Accion(TAG_CAMPO_CONTRASENA, "Contraseña", "abre el documento cifrado", "al abrir un PDF con contraseña"),
            Accion(TAG_CANCELAR_CONTRASENA, "Cancelar", "vuelve a inicio", "siempre"),
            Accion(
                TAG_ACEPTAR_CONTRASENA,
                "Abrir",
                "reintenta la apertura",
                "sólo con contraseña escrita",
                habilitada = false,
            ),
        )

    @Test
    fun inventario_de_la_hoja_de_contrasena() {
        composicion.setContent {
            TemaDracPDF {
                HojaContrasena(
                    nombreDocumento = "cifrado.pdf",
                    huboError = false,
                    alAceptar = {},
                    alCancelar = {},
                )
            }
        }

        hojaContrasena.forEach { accion ->
            composicion.onNodeWithTag(accion.tag).assertExisteCon(accion)
        }
    }

    // ------------------------------------------------------------------------ apoyo

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExisteCon(accion: Accion) {
        assertIsDisplayed()
        if (accion.habilitada) {
            assertIsEnabled()
        } else {
            // Se ve y no se pulsa. Es la mitad del inventario: comprobar que lo que
            // aún no existe no se puede usar, en vez de haberlo escondido.
            assertIsNotEnabled()
        }
    }

    private val grafos = mutableListOf<Grafo>()

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    private fun visorConDocumento(
        nombre: String,
        paginas: Int,
    ): VisorViewModel {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, nombre), paginas)
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, nombre))
        val estado = grafo.registro.abiertos().first()

        return VisorViewModel(
            grafo.casosDelVisor,
            grafo.registro,
            CachePaginas(PRESUPUESTO_PRUEBA),
        ).also { it.mostrar(estado.id) }
    }

    private companion object {
        /** Presupuesto fijo para que el test no dependa de la memoria del aparato. */
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
    }
}
