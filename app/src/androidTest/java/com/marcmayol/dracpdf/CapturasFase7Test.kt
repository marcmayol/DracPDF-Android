package com.marcmayol.dracpdf

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.PantallaVisor
import com.marcmayol.dracpdf.ui.visor.TAG_BUSCAR
import com.marcmayol.dracpdf.ui.visor.TAG_BUSQUEDA_CAMPO
import com.marcmayol.dracpdf.ui.visor.TAG_DESTINO_INDICE
import com.marcmayol.dracpdf.ui.visor.TAG_HOJA_INDICE
import com.marcmayol.dracpdf.ui.visor.VisorViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Las capturas de la Fase 7, en los dos temas.
 *
 * No comprueba nada: enseña. Un cambio visual no está hecho hasta que se ha visto en
 * pantalla, y aquí hay dos pantallas nuevas —la barra de buscar y el índice del
 * documento— que conviven con el documento debajo, que es justo donde los contrastes
 * fallan.
 */
@RunWith(AndroidJUnit4::class)
class CapturasFase7Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private val grafos = mutableListOf<Grafo>()
    private val tema = mutableStateOf(PreferenciaTema.OSCURO)

    @After
    fun cerrarDocumentos() {
        grafos.forEach { it.alTerminar() }
        grafos.clear()
    }

    @Test
    fun buscar_y_el_indice_en_los_dos_temas() {
        val fichero = GeneradorFixtures.conIndiceYEnlaces(File(contexto.cacheDir, "capturas-fase7.pdf"))
        val grafo = Grafo(contexto).also(grafos::add)
        grafo.abrirDocumento(OrigenDocumento.Privado(fichero.absolutePath, fichero.name))
        val estado = grafo.registro.abiertos().first()
        val modelo =
            VisorViewModel(grafo.casosDelVisor, grafo.registro, CachePaginas(PRESUPUESTO_PRUEBA))
                .also { it.mostrar(estado.id) }

        composicion.setContent {
            TemaDracPDF(preferencia = tema.value) {
                PantallaVisor(modelo = modelo, alSalir = {}, alImprimir = {}, alCompartirDocumento = {})
            }
        }
        composicion.waitForIdle()

        listOf(PreferenciaTema.CLARO, PreferenciaTema.OSCURO).forEach { elegido ->
            tema.value = elegido
            composicion.waitForIdle()
            val sufijo = elegido.name.lowercase()

            composicion.onNodeWithTag(TAG_BUSCAR).performClick()
            composicion.onNodeWithTag(TAG_BUSQUEDA_CAMPO).performTextInput("Pagina")
            esperar()
            guardar("busqueda-$sufijo")

            // Se sale de buscar para que el índice se vea sobre el documento y no sobre
            // una pantalla ya ocupada por otra cosa.
            modelo.cerrarBusqueda()
            composicion.waitForIdle()

            composicion.onNodeWithTag(TAG_DESTINO_INDICE).performClick()
            esperar()
            // Por el tag y no por la raíz: la hoja vive en su propia ventana y
            // capturar «todo» no sabe cuál de las dos quiere.
            guardar("indice-$sufijo", TAG_HOJA_INDICE)
            composicion.onNodeWithTag(TAG_DESTINO_INDICE).performClick()
            composicion.waitForIdle()
        }
    }

    /** Deja tiempo a que el motor conteste y a que la hoja acabe de subir. */
    private fun esperar() {
        composicion.waitForIdle()
        composicion.mainClock.advanceTimeBy(MARGEN_ANIMACION)
        composicion.waitForIdle()
    }

    /**
     * Una captura de **toda la pantalla**, hecha por el sistema.
     *
     * No vale capturar el nodo de Compose: las hojas y los diálogos viven en su propia
     * ventana, y desde el árbol de la pantalla principal no se pueden fotografiar. Esto
     * es lo mismo que ve el usuario, que es de lo que va una captura.
     */
    private fun guardar(
        nombre: String,
        tag: String? = null,
    ) {
        composicion.waitForIdle()
        val mapa = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val destino = File(contexto.getExternalFilesDir(null), "$nombre.png")
        FileOutputStream(destino).use { salida ->
            mapa.compress(android.graphics.Bitmap.CompressFormat.PNG, CALIDAD_PNG, salida)
        }
        ordenar("mkdir -p $FUERA")
        ordenar("cp ${destino.absolutePath} $FUERA/${destino.name}")
    }

    private fun ordenar(orden: String) {
        val salida = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(orden)
        ParcelFileDescriptor.AutoCloseInputStream(salida).use { it.readBytes() }
    }

    private companion object {
        const val PRESUPUESTO_PRUEBA = 32 * 1024 * 1024
        const val CALIDAD_PNG = 100
        const val MARGEN_ANIMACION = 1_000L
        const val FUERA = "/data/local/tmp/dracpdf-capturas"
    }
}
