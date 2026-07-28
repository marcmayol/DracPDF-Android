package com.marcmayol.dracpdf.ui.visor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * La hoja de miniaturas, desde la interfaz real: se abre desde el destino «Índice»
 * de la barra inferior, y sus miniaturas se rasterizan sólo cuando entran en
 * pantalla.
 */
@RunWith(AndroidJUnit4::class)
class MiniaturasTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun la_hoja_de_miniaturas_se_abre_y_solo_dibuja_las_que_se_ven() {
        val fichero =
            GeneradorFixtures.documento(File(contexto.cacheDir, "miniaturas.pdf"), paginas = PAGINAS)
        val repositorio = MuPdfDocumentRepository(FuenteDocumentosAndroid(contexto.contentResolver))
        val registro = RegistroDocumentos()
        AbrirDocumento(repositorio, registro)(OrigenDocumento.Privado(fichero.absolutePath, "miniaturas.pdf"))
        val estado = registro.abiertos().first()

        val modelo =
            VisorViewModel(
                RenderizarPagina(repositorio, registro),
                registro,
                CachePaginas(CachePaginas.presupuestoPara(contexto)),
            )

        composicion.setContent {
            TemaDracPDF { PantallaVisor(modelo = modelo, alSalir = {}) }
        }
        modelo.mostrar(estado.id)
        composicion.waitUntil(ESPERA_MS) { repositorio.paginasRenderizadas > 0 }
        composicion.waitForIdle()

        val antesDeAbrir = repositorio.paginasRenderizadas

        composicion.onNodeWithTag(TAG_DESTINO_INDICE).performClick()
        composicion.waitForIdle()

        composicion.onNodeWithTag(TAG_HOJA_INDICE).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_TAB_MINIATURAS).assertIsDisplayed()
        // La pestaña de índice existe y está a la vista, aunque no se pueda usar
        // todavía: el outline es de la Fase 7.
        composicion.onNodeWithTag(TAG_TAB_INDICE).assertIsDisplayed()

        composicion.waitUntil(ESPERA_MS) { repositorio.paginasRenderizadas > antesDeAbrir }
        composicion.waitForIdle()

        composicion.onNodeWithTag(tagMiniatura(0)).assertIsDisplayed()

        val miniaturasHechas = repositorio.paginasRenderizadas - antesDeAbrir
        android.util.Log.i(
            "DracPDF-metricas",
            "miniaturas rasterizadas al abrir la hoja: $miniaturasHechas de $PAGINAS",
        )
        assertTrue(
            "Se han rasterizado $miniaturasHechas miniaturas de $PAGINAS: la hoja no es perezosa",
            miniaturasHechas < PAGINAS / 2,
        )

        repositorio.cerrarTodo()
    }

    private companion object {
        const val PAGINAS = 120
        const val ESPERA_MS = 15_000L
    }
}
