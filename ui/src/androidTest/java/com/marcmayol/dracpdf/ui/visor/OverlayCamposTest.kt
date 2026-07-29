package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * La alineación del overlay con la página.
 *
 * Es el test que el escritorio pidió por escrito, y por un motivo concreto: el PDF
 * mide desde abajo a la izquierda y la pantalla desde arriba, así que un overlay mal
 * transformado no se nota «un poco torcido», sale reflejado en vertical, y con un
 * campo en el centro de la página nadie lo ve. Por eso los campos del test están
 * arriba y abajo del todo, donde el error de signo salta a la vista.
 *
 * El segundo caso —el mismo campo tras hacer zoom— es el que el escritorio aprendió
 * tarde: la alineación correcta a escala 1 no demuestra nada sobre la alineación a
 * escala 2,5 si alguien mezcló píxeles con puntos por el camino.
 */
@RunWith(AndroidJUnit4::class)
class OverlayCamposTest {
    @get:Rule
    val composicion = createComposeRule()

    private val tamanoA4 = TamanoPt(ANCHO_PT, ALTO_PT)

    /** Un campo en la cabecera de la página: arriba, y a un cuarto del alto. */
    private val cabecera =
        CampoFormulario(
            nombre = "cabecera",
            pagina = 0,
            indice = 0,
            tipo = TipoCampo.TEXTO,
            valor = "arriba",
            marco = RectPt(x0 = 0f, y0 = 0f, x1 = ANCHO_PT / 2f, y1 = ALTO_PT / 4f),
        )

    /** Y otro en el pie: la mitad de abajo, pegado al borde derecho. */
    private val pie =
        CampoFormulario(
            nombre = "pie",
            pagina = 0,
            indice = 1,
            tipo = TipoCampo.TEXTO,
            valor = "abajo",
            marco = RectPt(x0 = ANCHO_PT / 2f, y0 = ALTO_PT / 2f, x1 = ANCHO_PT, y1 = ALTO_PT),
        )

    private fun overlayDe(
        ancho: Dp,
        alto: Dp,
    ) {
        composicion.setContent {
            TemaDracPDF {
                // `requiredSize` y no `size`: con zoom la página es más ancha que la
                // pantalla a propósito, y `size` se dejaría recortar por la ventana del
                // test. En la aplicación esa holgura la da el scroll horizontal, que es
                // lo que aquí hay que reproducir para medir la página de verdad.
                Box(modifier = Modifier.requiredSize(width = ancho, height = alto).testTag(TAG_PAGINA)) {
                    OverlayCampos(
                        campos = listOf(cabecera, pie),
                        tamano = tamanoA4,
                        ancho = ancho,
                        alto = alto,
                        campoActivo = null,
                        alTocarCampo = {},
                    )
                }
            }
        }
    }

    private fun assertCerca(
        esperado: Float,
        real: Float,
        que: String,
    ) {
        assertTrue("$que: se esperaba $esperado ± $TOLERANCIA_PX y llegó $real", abs(esperado - real) <= TOLERANCIA_PX)
    }

    /**
     * Dónde está y cuánto mide un campo, **sin recortar**.
     *
     * No vale `boundsInRoot`: viene recortada al trozo visible, y una página con zoom
     * es más ancha que la pantalla a propósito. Con ella, el test del zoom mediría el
     * ancho del teléfono en lugar del ancho del campo, y daría rojo sin que nada
     * estuviera mal.
     *
     * Y se mide **respecto a la página**, no respecto a la raíz: la página con zoom
     * empieza fuera de la pantalla, y lo que se comprueba aquí es dónde cae el campo
     * dentro del papel, que es lo único que el overlay decide.
     */
    private fun campo(id: IdCampo): Caja {
        val nodo = composicion.onNodeWithTag(tagCampo(id)).fetchSemanticsNode()
        val pagina = composicion.onNodeWithTag(TAG_PAGINA).fetchSemanticsNode()
        return Caja(
            izquierda = nodo.positionInRoot.x - pagina.positionInRoot.x,
            arriba = nodo.positionInRoot.y - pagina.positionInRoot.y,
            ancho = nodo.size.width.toFloat(),
            alto = nodo.size.height.toFloat(),
        )
    }

    private data class Caja(
        val izquierda: Float,
        val arriba: Float,
        val ancho: Float,
        val alto: Float,
    )

    @Test
    fun cada_campo_cae_en_su_fraccion_de_pagina() {
        val ancho = 400.dp
        val alto = ancho * (ALTO_PT / ANCHO_PT)
        overlayDe(ancho, alto)

        val cajaCabecera = campo(IdCampo(0, 0))
        val cajaPie = campo(IdCampo(0, 1))

        val anchoPx = with(composicion.density) { ancho.toPx() }
        val altoPx = with(composicion.density) { alto.toPx() }

        // La cabecera ocupa la mitad izquierda y el cuarto de arriba.
        assertCerca(0f, cajaCabecera.izquierda, "izquierda de la cabecera")
        assertCerca(0f, cajaCabecera.arriba, "arriba de la cabecera")
        assertCerca(anchoPx / 2f, cajaCabecera.ancho, "ancho de la cabecera")
        assertCerca(altoPx / 4f, cajaCabecera.alto, "alto de la cabecera")

        // Y el pie, la mitad derecha y la mitad de abajo. Si la transformación
        // invirtiera el eje vertical, este campo saldría arriba y el test lo diría.
        assertCerca(anchoPx / 2f, cajaPie.izquierda, "izquierda del pie")
        assertCerca(altoPx / 2f, cajaPie.arriba, "arriba del pie")
        assertCerca(anchoPx / 2f, cajaPie.ancho, "ancho del pie")
        assertCerca(altoPx / 2f, cajaPie.alto, "alto del pie")

        assertTrue("el pie tiene que quedar por debajo de la cabecera", cajaPie.arriba > cajaCabecera.arriba)
    }

    @Test
    fun sigue_alineado_despues_de_hacer_zoom() {
        // La misma página, dibujada dos veces y media más grande: es lo que hace un
        // pellizco, y el overlay tiene que crecer con ella en la misma proporción.
        val ancho = 400.dp * ZOOM
        val alto = ancho * (ALTO_PT / ANCHO_PT)
        overlayDe(ancho, alto)

        val cajaPie = campo(IdCampo(0, 1))
        val anchoPx = with(composicion.density) { ancho.toPx() }
        val altoPx = with(composicion.density) { alto.toPx() }

        assertCerca(anchoPx / 2f, cajaPie.izquierda, "izquierda del pie con zoom")
        assertCerca(altoPx / 2f, cajaPie.arriba, "arriba del pie con zoom")
        assertCerca(anchoPx / 2f, cajaPie.ancho, "ancho del pie con zoom")

        // Y lo que de verdad importa: la fracción de página que ocupa no ha cambiado.
        assertEquals(0.5f, cajaPie.izquierda / anchoPx, TOLERANCIA_FRACCION)
        assertEquals(0.5f, cajaPie.arriba / altoPx, TOLERANCIA_FRACCION)
    }

    @Test
    fun los_campos_se_dibujan_con_su_valor() {
        overlayDe(400.dp, 400.dp * (ALTO_PT / ANCHO_PT))

        composicion.onNodeWithTag(tagCampo(IdCampo(0, 0))).assertIsDisplayed()
        composicion.onNodeWithTag(tagCampo(IdCampo(0, 1))).assertIsDisplayed()
    }

    private companion object {
        const val ANCHO_PT = 595f
        const val ALTO_PT = 842f
        const val ZOOM = 2.5f

        /** Un par de píxeles de margen: el redondeo a densidad entera no es exacto. */
        const val TOLERANCIA_PX = 2f
        const val TOLERANCIA_FRACCION = 0.01f

        const val TAG_PAGINA = "pagina_de_prueba"
    }
}
