package com.marcmayol.dracpdf.ui.visor

import android.app.UiAutomation
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
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
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Las cuatro vistas, en los dos temas, para mirarlas.
 *
 * No comprueba nada: **enseña**, igual que las capturas de la Fase 6. Un modo de vista
 * es un cambio visual, y un cambio visual no está hecho hasta que se ha visto; las
 * medidas de [ModosDeVistaTest] dicen que la página mide lo que debe, pero no dicen si
 * el resultado se puede mirar.
 *
 * Son dos pasadas porque la doble página no cabe de pie: la segunda tumba el aparato,
 * que es justo lo que le pide la hoja al lector cuando no caben dos.
 */
@RunWith(AndroidJUnit4::class)
class CapturasVistaTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private var repositorio: MuPdfDocumentRepository? = null

    private val tema = mutableStateOf(PreferenciaTema.CLARO)

    @After
    fun cerrarElDocumento() {
        repositorio?.cerrarTodo()
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    @Test
    fun de_pie_el_ajuste_y_el_giro_en_los_dos_temas() {
        val modelo = visorEnPantalla()

        enLosDosTemas { sufijo ->
            modelo.ajustarLaVista(AjusteDeVista.ANCHO)
            guardar("vista-ancho-$sufijo")

            modelo.ajustarLaVista(AjusteDeVista.PAGINA)
            guardar("vista-pagina-$sufijo")

            modelo.ajustarLaVista(AjusteDeVista.ANCHO)
            modelo.girarLaVista()
            guardar("vista-girada-$sufijo")
            // Tres cuartos más para dejarla como estaba: el giro es cíclico.
            repeat(GIROS_QUE_QUEDAN) { modelo.girarLaVista() }
        }
    }

    /**
     * La doble página hay que fotografiarla tumbando el aparato, no hay atajo: se
     * ofrece por el ancho real de la ventana, así que la única forma de verla es tener
     * la ventana ancha. Se congela la orientación **antes** de montar la pantalla y se
     * suelta al terminar.
     */
    @Test
    fun tumbado_aparecen_las_dos_paginas_en_los_dos_temas() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_90)
        // Girar la pantalla no es instantáneo y recrea la actividad: montar la
        // composición a media vuelta la deja huérfana y no se dibuja nada.
        Thread.sleep(ESPERA_AL_GIRAR_MS)
        val modelo = visorEnPantalla()
        // En el hilo de la interfaz y con el árbol quieto. Cambiar el modo desde el hilo
        // del test mientras Compose está midiendo revienta con «performMeasureAndLayout
        // called during measure layout», y con el giro de por medio pasa a menudo.
        composicion.runOnIdle { modelo.alternarDoblePagina() }

        enLosDosTemas { sufijo -> guardar("vista-doble-$sufijo") }
    }

    private fun enLosDosTemas(capturar: (String) -> Unit) {
        listOf(PreferenciaTema.CLARO, PreferenciaTema.OSCURO).forEach { elegido ->
            tema.value = elegido
            composicion.waitForIdle()
            capturar(elegido.name.lowercase())
        }
    }

    private fun visorEnPantalla(): VisorViewModel {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "capturas-vista.pdf"), paginas = PAGINAS)
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        val sesiones = SesionesMuPdf(fuente)
        val repo = MuPdfDocumentRepository(sesiones, fuente)
        repositorio = repo
        val contenido = MuPdfContenido(sesiones, fuente)
        val registro = RegistroDocumentos()
        AbrirDocumento(repo, registro)(OrigenDocumento.Privado(fichero.absolutePath, "capturas-vista.pdf"))
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
                        AlmacenFirmasFichero(File(contexto.cacheDir, "firmas-capturas")),
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
            TemaDracPDF(preferencia = tema.value) {
                PantallaVisor(modelo = modelo, alSalir = {})
            }
        }
        composicion.runOnIdle { modelo.mostrar(abierto.id) }
        composicion.waitUntil(ESPERA_MS) { repo.paginasRenderizadas > 0 }
        composicion.waitForIdle()
        return modelo
    }

    private fun guardar(nombre: String) {
        composicion.waitForIdle()
        // Un respiro para que llegue el bitmap a la escala nueva: la captura de una
        // página en blanco no enseñaría nada de lo que hay que mirar.
        Thread.sleep(ESPERA_AL_RENDER_MS)
        composicion.waitForIdle()

        // Del sistema y no del nodo: lo que hay que ver es la pantalla entera, con sus
        // barras, y las hojas y diálogos viven en ventanas que el árbol de Compose de
        // la pantalla de debajo no alcanza.
        val mapa = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val destino = File(contexto.getExternalFilesDir(null), "$nombre.png")
        FileOutputStream(destino).use { salida -> mapa.compress(Bitmap.CompressFormat.PNG, CALIDAD_PNG, salida) }
        sacarDelSandbox(destino)
    }

    /** Deja la captura fuera del sandbox, que se borra al desinstalar la de pruebas. */
    private fun sacarDelSandbox(fichero: File) {
        ordenar("mkdir -p $FUERA")
        ordenar("cp ${fichero.absolutePath} $FUERA/${fichero.name}")
    }

    private fun ordenar(orden: String) {
        val salida = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(orden)
        ParcelFileDescriptor.AutoCloseInputStream(salida).use { it.readBytes() }
    }

    private companion object {
        const val PAGINAS = 6
        const val ESPERA_MS = 15_000L
        const val ESPERA_AL_RENDER_MS = 1_200L
        const val ESPERA_AL_GIRAR_MS = 2_000L
        const val CALIDAD_PNG = 100
        const val GIROS_QUE_QUEDAN = 3
        const val FUERA = "/data/local/tmp/dracpdf-capturas"
    }
}
