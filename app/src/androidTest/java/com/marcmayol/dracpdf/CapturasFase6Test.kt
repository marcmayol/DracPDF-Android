package com.marcmayol.dracpdf

import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.ui.herramientas.DialogoContrasena
import com.marcmayol.dracpdf.ui.herramientas.DialogoConvertir
import com.marcmayol.dracpdf.ui.herramientas.HojaOrganizar
import com.marcmayol.dracpdf.ui.herramientas.TAG_CONVERTIR_IMAGENES
import com.marcmayol.dracpdf.ui.herramientas.TAG_DIALOGO_CLAVE
import com.marcmayol.dracpdf.ui.herramientas.TAG_DIALOGO_CONVERTIR
import com.marcmayol.dracpdf.ui.herramientas.TAG_HOJA_ORGANIZAR
import com.marcmayol.dracpdf.ui.herramientas.tagPaginaOrganizar
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Las capturas de las pantallas nuevas de la Fase 6, en los dos temas.
 *
 * No comprueba nada: **enseña**. Un cambio visual no está hecho hasta que se ha visto
 * en pantalla, y las dos veces que se ha mirado sólo el tema claro el fallo estaba en
 * el oscuro. Los ficheros salen en el almacenamiento de la aplicación y de ahí van a
 * `capturas/`.
 */
@RunWith(AndroidJUnit4::class)
class CapturasFase6Test {
    @get:Rule
    val composicion = createComposeRule()

    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    /** Qué se está enseñando. El contenido se monta una vez y cambia con esto. */
    private enum class Pantalla {
        ORGANIZAR,
        CONVERTIR,
        CONTRASENA,
    }

    private val tema = mutableStateOf(PreferenciaTema.CLARO)
    private val pantalla = mutableStateOf(Pantalla.ORGANIZAR)

    @Test
    fun las_pantallas_nuevas_en_los_dos_temas() {
        // Un solo `setContent` para todas: la regla no deja montar dos veces sobre la
        // misma actividad, así que lo que cambia es el estado y no el contenido.
        composicion.setContent {
            TemaDracPDF(preferencia = tema.value) {
                when (pantalla.value) {
                    Pantalla.ORGANIZAR ->
                        HojaOrganizar(
                            paginas = 7,
                            miniaturas = emptyMap(),
                            alPedirMiniatura = {},
                            alGuardar = {},
                            alCerrar = {},
                        )

                    Pantalla.CONVERTIR ->
                        DialogoConvertir(
                            paginas = 7,
                            alElegirTexto = {},
                            alElegirImagenes = { _, _ -> },
                            alCancelar = {},
                        )

                    Pantalla.CONTRASENA ->
                        DialogoContrasena(quitandoAlEmpezar = false, alAceptar = { _, _ -> }, alCancelar = {})
                }
            }
        }

        losDosTemas { sufijo ->
            enseñar(Pantalla.ORGANIZAR)
            // Con dos páginas cogidas se ven a la vez el contorno de selección y las
            // acciones encendidas, que es el estado que hay que mirar.
            composicion.onNodeWithTag(tagPaginaOrganizar(1)).performClick()
            composicion.onNodeWithTag(tagPaginaOrganizar(4)).performClick()
            composicion.waitForIdle()
            guardar(TAG_HOJA_ORGANIZAR, "organizar-$sufijo")

            enseñar(Pantalla.CONVERTIR)
            // El diálogo enseña lo suyo cuando se piden imágenes: formato, calidad,
            // tamaño y páginas. Es la versión larga, la que puede no caber.
            composicion.onNodeWithTag(TAG_CONVERTIR_IMAGENES).performClick()
            composicion.waitForIdle()
            guardar(TAG_DIALOGO_CONVERTIR, "convertir-$sufijo")

            enseñar(Pantalla.CONTRASENA)
            guardar(TAG_DIALOGO_CLAVE, "contrasena-$sufijo")
        }
    }

    private fun losDosTemas(capturar: (String) -> Unit) {
        listOf(PreferenciaTema.CLARO, PreferenciaTema.OSCURO).forEach { elegido ->
            tema.value = elegido
            composicion.waitForIdle()
            capturar(elegido.name.lowercase())
        }
    }

    private fun enseñar(cual: Pantalla) {
        pantalla.value = cual
        composicion.waitForIdle()
    }

    private fun guardar(
        tag: String,
        nombre: String,
    ) {
        val mapa = composicion.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        // En el almacenamiento externo de la aplicación y no en su caché privada: de
        // la caché no puede leer nadie más, ni siquiera quien va a sacar la captura.
        val destino = File(contexto.getExternalFilesDir(null), "$nombre.png")
        FileOutputStream(destino).use { salida ->
            mapa.compress(android.graphics.Bitmap.CompressFormat.PNG, CALIDAD_PNG, salida)
        }
        sacarDelSandbox(destino)
    }

    /**
     * Deja la captura donde sobreviva al final del test.
     *
     * Todo lo que escribe la aplicación se va con ella: al terminar, la instalación de
     * prueba se desinstala y su almacenamiento desaparece con las capturas dentro. El
     * intérprete de órdenes de la instrumentación no se desinstala, y por eso las saca
     * él.
     */
    private fun sacarDelSandbox(fichero: File) {
        ordenar("mkdir -p $FUERA")
        ordenar("cp ${fichero.absolutePath} $FUERA/${fichero.name}")
    }

    private fun ordenar(orden: String) {
        val salida = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(orden)
        // Se lee hasta el final a propósito: la orden va en otro proceso y cerrar sin
        // leer devolvería antes de que el fichero exista.
        ParcelFileDescriptor.AutoCloseInputStream(salida).use { it.readBytes() }
    }

    private companion object {
        /** El PNG no tiene pérdida: el número da igual, pero el método lo pide. */
        const val CALIDAD_PNG = 100

        /** Fuera del alcance del desinstalador, y accesible desde `adb pull`. */
        const val FUERA = "/data/local/tmp/dracpdf-capturas"
    }
}
