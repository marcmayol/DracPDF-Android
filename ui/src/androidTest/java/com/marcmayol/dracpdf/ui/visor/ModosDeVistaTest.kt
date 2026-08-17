package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firmas.AlmacenFirmasFichero
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfAnotaciones
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfContenido
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfEdicion
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfStampService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.BuscarEnDocumento
import com.marcmayol.dracpdf.dominio.casos.EditarContenido
import com.marcmayol.dracpdf.dominio.casos.EstamparFirma
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.MarcarDocumento
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Los modos de vista, medidos sobre la página de verdad.
 *
 * Lo que se comprueba no es que el estado cambie —eso lo diría cualquier test del
 * modelo— sino que **cambia lo que se ve**: cuánto mide la página en la pantalla, si
 * hay una o dos por fila, y hacia dónde queda tumbada. Un modo de vista que no altera
 * ni un píxel es exactamente el fallo que este test tiene que cazar.
 *
 * El ancho de la pantalla se impone con `requiredSize` en vez de girar el emulador: lo
 * que decide si caben dos páginas es el ancho disponible, así que la manera honesta de
 * probar las dos respuestas es darle dos anchos.
 */
@RunWith(AndroidJUnit4::class)
class ModosDeVistaTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private var repositorio: MuPdfDocumentRepository? = null

    @After
    fun cerrarElDocumento() {
        repositorio?.cerrarTodo()
    }

    /** El visor con un documento abierto, en una pantalla del ancho que se pida. */
    private fun visorDe(
        ancho: Dp,
        alto: Dp = ALTO_DE_PRUEBA,
    ): VisorViewModel {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "vista.pdf"), paginas = PAGINAS)
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        val sesiones = SesionesMuPdf(fuente)
        val repo = MuPdfDocumentRepository(sesiones, fuente)
        repositorio = repo
        val contenido = MuPdfContenido(sesiones, fuente)
        val registro = RegistroDocumentos()
        AbrirDocumento(repo, registro)(OrigenDocumento.Privado(fichero.absolutePath, "vista.pdf"))
        val abierto = registro.abiertos().first()

        val formularios = MuPdfFormService(sesiones)
        val modelo =
            VisorViewModel(
                CasosDelVisor(
                    RenderizarPagina(repo, registro),
                    ListarCampos(formularios, registro),
                    RellenarCampo(formularios, registro),
                    GuardarDocumento(repo, registro),
                    EstamparFirma(
                        MuPdfStampService(sesiones),
                        AlmacenFirmasFichero(File(contexto.cacheDir, "firmas-vista")),
                        repo,
                        registro,
                    ),
                    BuscarEnDocumento(contenido),
                    contenido,
                    contenido,
                    MarcarDocumento(MuPdfAnotaciones(sesiones), registro),
                    EditarContenido(MuPdfEdicion(sesiones), registro),
                ),
                registro,
                CachePaginas(CachePaginas.presupuestoPara(contexto)),
            )

        composicion.setContent {
            TemaDracPDF {
                Box(modifier = Modifier.requiredSize(width = ancho, height = alto)) {
                    PantallaVisor(modelo = modelo, alSalir = {})
                }
            }
        }
        modelo.mostrar(abierto.id)
        composicion.waitUntil(ESPERA_MS) { hayNodo(tagPagina(0)) }
        composicion.waitForIdle()
        return modelo
    }

    private fun hayNodo(tag: String): Boolean = composicion.onAllNodesWithTagSeguro(tag)

    /** El hueco que ocupa una página en la pantalla, en píxeles. */
    private fun caja(indice: Int): Caja {
        val nodo = composicion.onNodeWithTag(tagPagina(indice)).fetchSemanticsNode()
        return Caja(
            arriba = nodo.positionInRoot.y,
            izquierda = nodo.positionInRoot.x,
            ancho = nodo.size.width.toFloat(),
            alto = nodo.size.height.toFloat(),
        )
    }

    private data class Caja(
        val arriba: Float,
        val izquierda: Float,
        val ancho: Float,
        val alto: Float,
    )

    @Test
    fun a_pagina_completa_la_pagina_entera_cabe_en_la_pantalla() {
        val modelo = visorDe(ancho = ANCHO_ESTRECHO)

        val aAncho = caja(0)
        val lista = composicion.onNodeWithTag(TAG_LISTA).fetchSemanticsNode()
        // De partida se ajusta al ancho, y en un A4 vertical eso deja la página más
        // alta que la pantalla: es lo normal, y por eso se desplaza con el pulgar.
        assertTrue("a ancho la página debería llenar el ancho: ${aAncho.ancho}", aAncho.ancho > lista.size.width * 0.9f)
        assertTrue("a ancho un A4 no cabe entero de alto", aAncho.alto > lista.size.height)

        modelo.ajustarLaVista(AjusteDeVista.PAGINA)
        composicion.waitForIdle()

        val aPagina = caja(0)
        assertTrue("a página completa la página tiene que encoger", aPagina.ancho < aAncho.ancho)
        assertTrue(
            "a página completa el alto (${aPagina.alto}) tiene que caber en ${lista.size.height}",
            aPagina.alto <= lista.size.height.toFloat(),
        )
        // Y sigue siendo un A4: encoger no puede deformarla.
        assertEquals(PROPORCION_A4, aPagina.alto / aPagina.ancho, TOLERANCIA_PROPORCION)
    }

    @Test
    fun en_una_pantalla_estrecha_la_doble_pagina_no_se_ofrece() {
        val modelo = visorDe(ancho = ANCHO_ESTRECHO)
        val antes = caja(0)

        // El lector la pide igualmente —desde una tablet, o desde este mismo móvil
        // tumbado— y la preferencia se guarda; lo que no puede es aplicarse aquí.
        modelo.alternarDoblePagina()
        composicion.waitForIdle()

        val despues = caja(0)
        val lista = composicion.onNodeWithTag(TAG_LISTA).fetchSemanticsNode()
        assertTrue("la preferencia sí queda pedida", modelo.estado.value.vista.doblePagina)
        assertEquals("la página no puede encoger si no caben dos", antes.ancho, despues.ancho, TOLERANCIA_PX)
        // Y sigue ocupando la fila entera, que es lo que dice que no hay segunda columna:
        // partida en dos mediría la mitad, y la página 1 estaría a su lado y no debajo.
        assertTrue(
            "la página tiene que seguir llenando la fila: ${despues.ancho} de ${lista.size.width}",
            despues.ancho > lista.size.width * 0.9f,
        )
    }

    @Test
    fun en_una_pantalla_ancha_la_doble_pagina_pone_dos_lado_a_lado() {
        val modelo = visorDe(ancho = ANCHO_APAISADO)
        val antes = caja(0)

        modelo.alternarDoblePagina()
        composicion.waitForIdle()

        val primera = caja(0)
        val segunda = caja(1)
        assertTrue("cada página ocupa ahora la mitad: ${primera.ancho} de ${antes.ancho}", primera.ancho < antes.ancho)
        assertEquals("las dos páginas van a la misma altura", primera.arriba, segunda.arriba, TOLERANCIA_PX)
        assertTrue("la segunda va a la derecha de la primera", segunda.izquierda > primera.izquierda)
    }

    @Test
    fun girar_la_vista_pone_la_pagina_de_lado() {
        val modelo = visorDe(ancho = ANCHO_ESTRECHO)
        val derecha = caja(0)
        assertEquals(PROPORCION_A4, derecha.alto / derecha.ancho, TOLERANCIA_PROPORCION)

        modelo.girarLaVista()
        composicion.waitForIdle()

        val tumbada = caja(0)
        assertEquals(GiroDeVista.UN_CUARTO, modelo.estado.value.vista.giro)
        // El hueco que ocupa ha invertido su forma: lo que era alto ahora es ancho.
        assertEquals(1f / PROPORCION_A4, tumbada.alto / tumbada.ancho, TOLERANCIA_PROPORCION)

        // Y media vuelta más la deja de pie otra vez, mirando al revés.
        modelo.girarLaVista()
        composicion.waitForIdle()
        val delReves = caja(0)
        assertEquals(GiroDeVista.MEDIA, modelo.estado.value.vista.giro)
        assertEquals(PROPORCION_A4, delReves.alto / delReves.ancho, TOLERANCIA_PROPORCION)
    }

    @Test
    fun la_hoja_de_vista_apaga_la_doble_pagina_cuando_no_cabe() {
        composicion.setContent {
            TemaDracPDF {
                HojaVista(
                    vista = VistaDelVisor(),
                    cabenDosPaginas = false,
                    alAjustar = {},
                    alAlternarDoblePagina = {},
                    alGirar = {},
                    alCerrar = {},
                )
            }
        }

        composicion.onNodeWithTag(TAG_HOJA_VISTA).assertIsDisplayed()
        composicion.onNodeWithTag(tagAjusteDeVista(AjusteDeVista.ANCHO)).assertIsEnabled()
        composicion.onNodeWithTag(tagAjusteDeVista(AjusteDeVista.PAGINA)).assertIsEnabled()
        // Apagada y con el motivo escrito: desaparecer dejaría al lector buscándola.
        composicion.onNodeWithTag(TAG_VISTA_DOBLE).assertIsNotEnabled()
        composicion.onNodeWithTag(TAG_VISTA_DOBLE_NO_CABE).assertIsDisplayed()
        // Girar no depende del ancho: se puede siempre.
        composicion.onNodeWithTag(TAG_VISTA_GIRAR).assertIsEnabled()
    }

    @Test
    fun la_hoja_de_vista_ofrece_la_doble_pagina_cuando_cabe() {
        composicion.setContent {
            TemaDracPDF {
                HojaVista(
                    vista = VistaDelVisor(),
                    cabenDosPaginas = true,
                    alAjustar = {},
                    alAlternarDoblePagina = {},
                    alGirar = {},
                    alCerrar = {},
                )
            }
        }

        composicion.onNodeWithTag(TAG_VISTA_DOBLE).assertIsEnabled()
        composicion.onAllNodesWithTagSeguro(TAG_VISTA_DOBLE_NO_CABE).let { existe ->
            assertTrue("cuando caben dos no hay nada que explicar", !existe)
        }
    }

    private companion object {
        const val PAGINAS = 8
        const val ESPERA_MS = 15_000L

        /** Un móvil de pie: aquí no caben dos páginas. */
        val ANCHO_ESTRECHO = 400.dp

        /** El mismo móvil tumbado, o una tablet: aquí sí. */
        val ANCHO_APAISADO = 900.dp
        val ALTO_DE_PRUEBA = 700.dp

        const val PROPORCION_A4 = 842f / 595f
        const val TOLERANCIA_PROPORCION = 0.05f
        const val TOLERANCIA_PX = 2f
    }
}

/** Si hay al menos un nodo con esa etiqueta, sin que la ausencia sea un fallo. */
private fun ComposeContentTestRule.onAllNodesWithTagSeguro(tag: String): Boolean =
    onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
