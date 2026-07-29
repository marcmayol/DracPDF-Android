package com.marcmayol.dracpdf.ui.visor

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firmas.AlmacenFirmasFichero
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfStampService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.EstamparFirma
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.system.measureTimeMillis

/**
 * El criterio de la Fase 1, medido: un PDF de 500 páginas abre sin bloquear, sólo se
 * rasteriza lo que se ve, y la caché no se pasa de su presupuesto.
 *
 * Se hace desde la interfaz de verdad, no llamando por dentro al modelo: lo que hay
 * que demostrar es lo que ocurre cuando alguien abre el documento y mira, no lo que
 * ocurre cuando un test llama a un método.
 */
@RunWith(AndroidJUnit4::class)
class VisorRendimientoTest {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun un_documento_de_500_paginas_abre_rapido_y_solo_dibuja_lo_que_se_ve() {
        val fichero =
            GeneradorFixtures.documento(
                File(contexto.cacheDir, "quinientas.pdf"),
                paginas = PAGINAS,
            )

        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        val sesiones = SesionesMuPdf(fuente)
        val repositorio = MuPdfDocumentRepository(sesiones, fuente)
        val registro = RegistroDocumentos()
        val abrir = AbrirDocumento(repositorio, registro)

        val msAbrir =
            measureTimeMillis {
                abrir(OrigenDocumento.Privado(fichero.absolutePath, "quinientas.pdf"))
            }
        val estado = registro.abiertos().first()
        assertTrue("El documento tiene ${estado.documento.paginas} páginas", estado.documento.paginas == PAGINAS)

        val cache = CachePaginas(CachePaginas.presupuestoPara(contexto))
        val modelo =
            VisorViewModel(
                CasosDelVisor(
                    RenderizarPagina(repositorio, registro),
                    ListarCampos(MuPdfFormService(sesiones), registro),
                    RellenarCampo(MuPdfFormService(sesiones), registro),
                    GuardarDocumento(repositorio, registro),
                    EstamparFirma(
                        MuPdfStampService(sesiones),
                        AlmacenFirmasFichero(File(contexto.cacheDir, "firmas-test")),
                        repositorio,
                        registro,
                    ),
                ),
                registro,
                cache,
            )

        val msPrimerRender =
            measureTimeMillis {
                composicion.setContent {
                    TemaDracPDF {
                        PantallaVisor(modelo = modelo, alSalir = {})
                    }
                }
                modelo.mostrar(estado.id)
                composicion.waitUntil(TIEMPO_MAXIMO_MS) { repositorio.paginasRenderizadas > 0 }
            }

        composicion.onNodeWithTag(tagPagina(0)).assertExists()

        val total = msAbrir + msPrimerRender
        android.util.Log.i(
            ETIQUETA_METRICAS,
            "paginas=$PAGINAS abrir=${msAbrir}ms primerRender=${msPrimerRender}ms total=${total}ms " +
                "presupuestoCache=${cache.presupuesto / 1024 / 1024}MB",
        )
        assertTrue(
            "El documento tardó $total ms en mostrar la primera página (abrir $msAbrir + render $msPrimerRender)",
            total < TIEMPO_MAXIMO_MS,
        )

        // Lo que de verdad separa un visor de una bomba de memoria: de 500 páginas,
        // sólo se han rasterizado las que caben en pantalla y una a cada lado.
        composicion.waitForIdle()
        val renderizadas = repositorio.paginasRenderizadas
        android.util.Log.i(
            ETIQUETA_METRICAS,
            "renderizadas=$renderizadas de $PAGINAS · cacheOcupada=${cache.ocupado / 1024}KB",
        )
        assertTrue(
            "Se han rasterizado $renderizadas páginas de $PAGINAS: el render perezoso no está funcionando",
            renderizadas <= MAXIMO_RENDERS_RAZONABLE,
        )

        assertTrue(
            "La caché ocupa ${cache.ocupado} bytes y su presupuesto es ${cache.presupuesto}",
            cache.ocupado <= cache.presupuesto,
        )

        repositorio.cerrarTodo()
    }

    private companion object {
        /** Por aquí salen las métricas del criterio: `adb logcat -s DracPDF-metricas`. */
        const val ETIQUETA_METRICAS = "DracPDF-metricas"

        const val PAGINAS = 500

        /** El criterio de la fase: primera página a la vista en menos de dos segundos. */
        const val TIEMPO_MAXIMO_MS = 2_000L

        /**
         * En un teléfono caben una o dos páginas a la vez; con la página de precarga a
         * cada lado, cuatro es holgado. Si esto se dispara, es que se está rasterizando
         * el documento entero.
         */
        const val MAXIMO_RENDERS_RAZONABLE = 8
    }
}
