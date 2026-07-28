package com.marcmayol.dracpdf

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.saf.OrigenesDelSistema
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.inicio.HojaContrasena
import com.marcmayol.dracpdf.ui.inicio.PantallaInicio
import com.marcmayol.dracpdf.ui.inicio.TAG_ABRIR
import com.marcmayol.dracpdf.ui.inicio.TAG_ACEPTAR_CONTRASENA
import com.marcmayol.dracpdf.ui.inicio.TAG_CAMPO_CONTRASENA
import com.marcmayol.dracpdf.ui.inicio.TAG_DRAGON
import com.marcmayol.dracpdf.ui.inicio.TAG_ERROR_CONTRASENA
import com.marcmayol.dracpdf.ui.inicio.TAG_MENU_INICIO
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * La apertura de documentos: la pantalla de inicio, la traducción de los intents que
 * llegan de otras aplicaciones, y el camino de un PDF cifrado.
 */
@RunWith(AndroidJUnit4::class)
class AperturaTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun la_pantalla_de_inicio_ofrece_abrir_y_ensena_la_marca() {
        composicion.setContent {
            TemaDracPDF { PantallaInicio(alAbrirPdf = {}) }
        }

        composicion.onNodeWithTag(TAG_ABRIR).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_DRAGON).assertIsDisplayed()
        // El menú de la pantalla de inicio se ve y no se puede usar: su contenido está
        // pedido al diseño y llega con las fases que lo llenan.
        composicion.onNodeWithTag(TAG_MENU_INICIO).assertIsNotEnabled()
    }

    @Test
    fun un_intent_de_ver_trae_un_documento() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "porintent.pdf"), paginas = 3)
        val intent =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(fichero), "application/pdf")
            }

        val origen = OrigenesDelSistema.delIntent(contexto.contentResolver, intent)

        assertTrue("El intent de ver no ha traído documento", origen is OrigenDocumento.Externo)
        assertEquals("porintent.pdf", (origen as OrigenDocumento.Externo).nombre)
    }

    @Test
    fun un_envio_desde_otra_aplicacion_trae_un_documento() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "compartido.pdf"), paginas = 2)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, Uri.fromFile(fichero))
            }

        val origen = OrigenesDelSistema.delIntent(contexto.contentResolver, intent)

        assertEquals("compartido.pdf", (origen as OrigenDocumento.Externo).nombre)
    }

    @Test
    fun un_intent_que_no_trae_documento_no_inventa_ninguno() {
        val intent = Intent(Intent.ACTION_MAIN)

        assertNull(OrigenesDelSistema.delIntent(contexto.contentResolver, intent))
        assertNull(OrigenesDelSistema.delIntent(contexto.contentResolver, null))
    }

    @Test
    fun un_pdf_cifrado_pide_contrasena_y_con_la_correcta_se_abre() {
        val fichero =
            GeneradorFixtures.documento(
                File(contexto.cacheDir, "conclave.pdf"),
                paginas = 4,
                contrasena = "abrete",
            )
        val grafo = Grafo(contexto)
        val modelo = AppViewModel(grafo.abrirDocumento, grafo.cerrarDocumento, grafo.registro)
        val origen = OrigenDocumento.Privado(fichero.absolutePath, "conclave.pdf")

        // Sin contraseña: no es un error, es un documento que espera.
        modelo.abrir(origen)
        esperarA { modelo.estado.value is EstadoApp.PidiendoContrasena }
        assertTrue(!(modelo.estado.value as EstadoApp.PidiendoContrasena).fallo)

        // Con una equivocada: se vuelve a pedir, marcada como fallida.
        modelo.abrir(origen, "sesamo")
        esperarA { (modelo.estado.value as? EstadoApp.PidiendoContrasena)?.fallo == true }

        // Con la correcta: se abre.
        modelo.abrir(origen, "abrete")
        esperarA { modelo.estado.value is EstadoApp.Viendo }

        val id = (modelo.estado.value as EstadoApp.Viendo).id
        assertEquals(
            4,
            grafo.registro
                .estado(id)
                .documento.paginas,
        )

        grafo.alTerminar()
    }

    @Test
    fun la_hoja_de_contrasena_no_deja_aceptar_en_blanco_y_ensena_el_fallo() {
        composicion.setContent {
            TemaDracPDF {
                HojaContrasena(
                    nombreDocumento = "conclave.pdf",
                    huboError = true,
                    alAceptar = {},
                    alCancelar = {},
                )
            }
        }

        composicion.onNodeWithTag(TAG_ERROR_CONTRASENA).assertIsDisplayed()
        // Sin contraseña escrita no hay nada que probar, así que el botón espera.
        composicion.onNodeWithTag(TAG_ACEPTAR_CONTRASENA).assertIsNotEnabled()

        composicion.onNodeWithTag(TAG_CAMPO_CONTRASENA).performTextInput("abrete")
        composicion.onNodeWithTag(TAG_ACEPTAR_CONTRASENA).performClick()
    }

    private fun esperarA(condicion: () -> Boolean) {
        runBlocking {
            val limite = System.currentTimeMillis() + ESPERA_MS
            while (!condicion() && System.currentTimeMillis() < limite) {
                kotlinx.coroutines.delay(POLL_MS)
            }
        }
        assertTrue("La condición no se cumplió en $ESPERA_MS ms", condicion())
    }

    private companion object {
        const val ESPERA_MS = 10_000L
        const val POLL_MS = 25L
    }
}
