package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFirmas
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfStampService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Estampar una firma y comprobar que **sigue siendo transparente** después de pasar
 * por el PDF.
 *
 * Es el corazón del criterio de la fase. Que la firma se vea es fácil; lo que hay
 * que demostrar es que lo que hay a su alrededor **no** se ha tapado: una firma que
 * llega como recuadro opaco borra la línea del documento sobre la que se coloca, y
 * eso no se descubre mirando la firma, sino mirando lo de al lado.
 */
@RunWith(AndroidJUnit4::class)
class EstampadoFirmaTest {
    private lateinit var carpeta: File
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var sellos: MuPdfStampService

    @Before
    fun preparar() {
        carpeta = File(contexto().cacheDir, "firmas").apply { mkdirs() }
        montar()
    }

    private fun contexto() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun montar() {
        val fuente = FuenteDocumentosAndroid(contexto().contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        sellos = MuPdfStampService(sesiones)
    }

    @After
    fun recoger() {
        sesiones.cerrarTodo()
    }

    private fun abrir(
        fichero: File,
        id: String,
    ): IdDocumento =
        IdDocumento(id).also {
            repositorio.abrir(it, OrigenDocumento.Privado(fichero.absolutePath, fichero.name))
        }

    /**
     * Lee un píxel del render, que llega en RGBA.
     *
     * **El canal que importa es el alfa.** El motor rasteriza con transparencia, así
     * que el papel en blanco de un PDF no es blanco: es «nada pintado», alfa cero.
     * Medir por el canal rojo daría negro donde no hay nada, y las cuentas saldrían
     * al revés.
     */
    private fun pixel(
        render: PaginaRenderizada,
        x: Int,
        y: Int,
    ): Pixel {
        val posicion = (y * render.ancho + x) * BYTES_POR_PIXEL
        return Pixel(
            rojo = render.pixeles[posicion].toInt() and 0xFF,
            alfa = render.pixeles[posicion + 3].toInt() and 0xFF,
        )
    }

    private data class Pixel(
        val rojo: Int,
        val alfa: Int,
    ) {
        /** Hay algo dibujado y es oscuro. */
        val esTinta: Boolean get() = alfa > MEDIO_OPACO && rojo < UMBRAL_TINTA

        /** No hay absolutamente nada: por aquí se ve el papel. */
        val esVacio: Boolean get() = alfa == 0
    }

    private fun contar(
        render: PaginaRenderizada,
        marco: RectPt,
        escala: Float = 1f,
        cumple: (Pixel) -> Boolean,
    ): Int {
        var cuantos = 0
        for (y in (marco.y0 * escala).toInt() until (marco.y1 * escala).toInt()) {
            for (x in (marco.x0 * escala).toInt() until (marco.x1 * escala).toInt()) {
                if (cumple(pixel(render, x, y))) cuantos++
            }
        }
        return cuantos
    }

    private val marco = RectPt(x0 = 100f, y0 = 500f, x1 = 340f, y1 = 580f)

    @Test
    fun la_firma_estampada_deja_tinta_en_su_sitio() {
        val fichero = GeneradorFixtures.documento(File(carpeta, "sello.pdf"), paginas = 1)
        val id = abrir(fichero, "sello")

        val antes = repositorio.renderizar(id, 0, 1f)
        val estampado = sellos.estampar(id, 0, marco, GeneradorFirmas.png())
        val despues = repositorio.renderizar(id, 0, 1f)

        assertEquals(0, estampado.pagina)
        assertEquals(marco, estampado.marco)
        val tintaAntes = contar(antes, marco) { it.esTinta }
        val tintaDespues = contar(despues, marco) { it.esTinta }
        assertTrue(
            "Donde se estampó la firma había $tintaAntes píxeles de tinta y ahora hay $tintaDespues",
            tintaDespues > tintaAntes + MINIMO_PIXELES_DE_TINTA,
        )
    }

    @Test
    fun alrededor_del_trazo_el_papel_sigue_visible() {
        // Se estampa sobre el marco que el fixture dibuja en la página: si la firma
        // llegara con fondo opaco, ese marco desaparecería debajo.
        val fichero = GeneradorFixtures.documento(File(carpeta, "alfa.pdf"), paginas = 1)
        val id = abrir(fichero, "alfa")

        val ancho = 200f
        val alto = ancho * (GeneradorFirmas.ALTO.toFloat() / GeneradorFirmas.ANCHO)
        // La esquina superior izquierda del marco impreso está en (36, 36).
        val encimaDelMarco = RectPt(x0 = 20f, y0 = 20f, x1 = 20f + ancho, y1 = 20f + alto)

        val antes = repositorio.renderizar(id, 0, 1f)
        val papelAntes = contar(antes, encimaDelMarco) { it.esVacio }
        val tintaDelDocumentoAntes = contar(antes, encimaDelMarco) { it.esTinta }

        sellos.estampar(id, 0, encimaDelMarco, GeneradorFirmas.png())
        val despues = repositorio.renderizar(id, 0, 1f)

        // La mayor parte de la zona tiene que seguir sin pintar: el trazo ocupa poco y
        // el resto del PNG es transparente. Con el alfa perdido, aquí no quedaría ni un
        // píxel libre, porque el sello habría cubierto el rectángulo entero.
        val papelDespues = contar(despues, encimaDelMarco) { it.esVacio }
        assertTrue(
            "Tras estampar quedaron $papelDespues píxeles sin pintar de los $papelAntes que había",
            papelDespues > papelAntes * FRACCION_MINIMA_DE_PAPEL,
        )
        // Y lo que ya estaba dibujado debajo —el marco impreso de la página— sigue ahí.
        val tintaDespues = contar(despues, encimaDelMarco) { it.esTinta }
        assertTrue(
            "El dibujo que había debajo ($tintaDelDocumentoAntes px) tenía que seguir viéndose",
            tintaDespues >= tintaDelDocumentoAntes,
        )
    }

    @Test
    fun la_firma_sobrevive_a_guardar_cerrar_y_reabrir() {
        val fichero = GeneradorFixtures.documento(File(carpeta, "persistente.pdf"), paginas = 2)
        val id = abrir(fichero, "persistente")

        sellos.estampar(id, 1, marco, GeneradorFirmas.png())
        assertTrue("Estampar cambia el documento", repositorio.tieneCambiosSinGuardar(id))
        repositorio.guardarIncremental(id)

        sesiones.cerrarTodo()
        montar()
        val reabierto = abrir(fichero, "persistente-2")

        val render = repositorio.renderizar(reabierto, 1, 1f)
        assertTrue(
            "La firma tenía que seguir en la página 2 al reabrir el fichero",
            contar(render, marco) { it.esTinta } > MINIMO_PIXELES_DE_TINTA,
        )
        // Y sigue siendo transparente después del viaje de ida y vuelta: dentro del
        // marco de la firma queda sitio sin pintar.
        assertTrue(
            "La transparencia tenía que sobrevivir al guardado",
            contar(render, marco) { it.esVacio } > 0,
        )
    }

    @Test
    fun la_firma_se_ve_igual_de_bien_a_otra_escala() {
        val fichero = GeneradorFixtures.documento(File(carpeta, "escalas.pdf"), paginas = 1)
        val id = abrir(fichero, "escalas")

        sellos.estampar(id, 0, marco, GeneradorFirmas.png())

        val aUno = repositorio.renderizar(id, 0, 1f)
        val aDos = repositorio.renderizar(id, 0, 2f)

        val tintaAUno = contar(aUno, marco) { it.esTinta }
        val tintaADos = contar(aDos, marco, escala = 2f) { it.esTinta }
        // Al doble de escala hay aproximadamente cuatro veces más píxeles de tinta.
        // No se pide exactitud: se pide que la firma no desaparezca ni se cuadruplique
        // de sitio, que es lo que pasaría con un marco mal transformado.
        assertTrue(
            "A escala 2 había $tintaADos píxeles de tinta y a escala 1, $tintaAUno",
            tintaADos > tintaAUno * 2,
        )
    }

    private companion object {
        const val BYTES_POR_PIXEL = 4
        const val UMBRAL_TINTA = 128
        const val MEDIO_OPACO = 128
        const val MINIMO_PIXELES_DE_TINTA = 200
        const val FRACCION_MINIMA_DE_PAPEL = 0.5f
    }
}
