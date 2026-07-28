package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.documentos.DocumentoEnLista
import com.marcmayol.dracpdf.ui.documentos.HojaDocumentos
import com.marcmayol.dracpdf.ui.documentos.TAG_ABRIR_OTRO
import com.marcmayol.dracpdf.ui.documentos.TAG_CERRAR_TODOS
import com.marcmayol.dracpdf.ui.documentos.TAG_HOJA_DOCUMENTOS
import com.marcmayol.dracpdf.ui.documentos.tagCerrarDocumento
import com.marcmayol.dracpdf.ui.documentos.tagDocumento
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Varios documentos abiertos a la vez, sobre el registro.
 *
 * Lo que se prueba aquí no es que la lista se dibuje, sino que **el registro es el
 * dueño de la verdad**: abrir no cierra, cambiar no descarta, y cerrar uno deja los
 * demás donde estaban, por la página por la que iban.
 */
@RunWith(AndroidJUnit4::class)
class MultiDocumentoTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    private fun modeloCon(vararg documentos: Pair<String, Int>): Pair<AppViewModel, Grafo> {
        val grafo = Grafo(contexto)
        val modelo = AppViewModel(grafo.abrirDocumento, grafo.cerrarDocumento, grafo.registro)
        documentos.forEach { (nombre, paginas) ->
            val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, nombre), paginas)
            modelo.abrir(OrigenDocumento.Privado(fichero.absolutePath, nombre))
            esperarA { grafo.registro.abiertos().any { it.documento.nombre == nombre } }
        }
        return modelo to grafo
    }

    @Test
    fun abrir_un_segundo_documento_no_cierra_el_primero() {
        val (modelo, grafo) = modeloCon("uno.pdf" to 3, "dos.pdf" to 7)

        esperarA { modelo.abiertos.value.size == 2 }
        assertEquals(listOf("uno.pdf", "dos.pdf"), modelo.abiertos.value.map { it.documento.nombre })
        // El que se está mirando es el último abierto.
        assertEquals(
            grafo.registro
                .abiertos()
                .last()
                .id,
            (modelo.estado.value as EstadoApp.Viendo).id,
        )

        grafo.alTerminar()
    }

    @Test
    fun cambiar_de_documento_conserva_por_donde_iba_cada_uno() {
        val (modelo, grafo) = modeloCon("largo.pdf" to 20, "corto.pdf" to 4)
        val largo = modelo.abiertos.value.first { it.documento.nombre == "largo.pdf" }
        val corto = modelo.abiertos.value.first { it.documento.nombre == "corto.pdf" }

        grafo.registro.anotarPagina(largo.id, 11)
        grafo.registro.anotarPagina(corto.id, 2)

        modelo.cambiarA(largo.id)
        assertEquals(largo.id, (modelo.estado.value as EstadoApp.Viendo).id)
        assertEquals(11, grafo.registro.estado(largo.id).paginaActual)

        modelo.cambiarA(corto.id)
        assertEquals(2, grafo.registro.estado(corto.id).paginaActual)
        // Y el otro sigue por donde estaba: cambiar no es reabrir.
        assertEquals(11, grafo.registro.estado(largo.id).paginaActual)

        grafo.alTerminar()
    }

    @Test
    fun cerrar_el_documento_activo_salta_al_siguiente_y_no_echa_al_inicio() {
        val (modelo, grafo) = modeloCon("primero.pdf" to 3, "segundo.pdf" to 5)
        val activo = (modelo.estado.value as EstadoApp.Viendo).id

        modelo.cerrar(activo)

        esperarA { modelo.abiertos.value.size == 1 }
        assertTrue("Cerrar uno no debería echar al inicio", modelo.estado.value is EstadoApp.Viendo)
        assertEquals(
            "primero.pdf",
            modelo.abiertos.value
                .single()
                .documento.nombre,
        )

        grafo.alTerminar()
    }

    @Test
    fun cerrar_el_ultimo_documento_lleva_al_inicio() {
        val (modelo, grafo) = modeloCon("solo.pdf" to 2)

        modelo.cerrar((modelo.estado.value as EstadoApp.Viendo).id)

        esperarA { modelo.estado.value is EstadoApp.Inicio }
        assertTrue(modelo.abiertos.value.isEmpty())

        grafo.alTerminar()
    }

    @Test
    fun cerrar_todos_vacia_el_registro() {
        val (modelo, grafo) = modeloCon("a.pdf" to 2, "b.pdf" to 3, "c.pdf" to 4)
        esperarA { modelo.abiertos.value.size == 3 }

        modelo.cerrarTodos()

        esperarA { modelo.abiertos.value.isEmpty() }
        assertTrue(grafo.registro.abiertos().isEmpty())
        assertTrue(modelo.estado.value is EstadoApp.Inicio)

        grafo.alTerminar()
    }

    @Test
    fun la_hoja_lista_los_documentos_y_marca_el_activo() {
        var elegido: String? = null
        var cerrado: String? = null
        val documentos =
            listOf(
                DocumentoEnLista("doc-1", "contrato.pdf", 2, 12, System.currentTimeMillis(), activo = false),
                DocumentoEnLista("doc-2", "nominas.pdf", 0, 3, System.currentTimeMillis(), activo = true),
            )

        composicion.setContent {
            TemaDracPDF {
                HojaDocumentos(
                    documentos = documentos,
                    alPedirMiniatura = {},
                    alElegir = { elegido = it },
                    alCerrarDocumento = { cerrado = it },
                    alAbrirOtro = {},
                    alCerrarTodos = {},
                    alCerrar = {},
                )
            }
        }

        composicion.onNodeWithTag(TAG_HOJA_DOCUMENTOS).assertIsDisplayed()
        composicion.onNodeWithTag(tagDocumento("doc-1")).assertIsDisplayed()
        composicion.onNodeWithTag(tagDocumento("doc-2")).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_ABRIR_OTRO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_CERRAR_TODOS).assertIsDisplayed()

        composicion.onNodeWithTag(tagDocumento("doc-1")).performClick()
        assertEquals("doc-1", elegido)

        composicion.onNodeWithTag(tagCerrarDocumento("doc-2")).performClick()
        assertEquals("doc-2", cerrado)
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
