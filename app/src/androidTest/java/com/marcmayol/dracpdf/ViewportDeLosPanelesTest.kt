package com.marcmayol.dracpdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.marcmayol.dracpdf.ui.documentos.DocumentoEnLista
import com.marcmayol.dracpdf.ui.documentos.HojaDocumentos
import com.marcmayol.dracpdf.ui.documentos.TAG_ABRIR_OTRO
import com.marcmayol.dracpdf.ui.documentos.TAG_CERRAR_TODOS
import com.marcmayol.dracpdf.ui.documentos.TAG_HOJA_DOCUMENTOS
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.tema.TemaDracPDF
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Los paneles con scroll no esconden su parte de abajo tras la barra del sistema.
 *
 * Es la parte del criterio F5 que salió de un fallo real: con edge-to-edge, una hoja
 * que mide su aire inferior desde el borde físico —y no desde donde acaban los botones
 * de navegación— **parece** completa y tiene su última fila debajo de ellos, donde ni
 * se lee ni se toca. Aquí se comprueba sobre la hoja de documentos abiertos, que es
 * donde estaba el fallo: sus dos acciones viven al pie, después de la lista.
 */
@RunWith(AndroidJUnit4::class)
class ViewportDeLosPanelesTest {
    @get:Rule
    val composicion = createComposeRule()

    @Test
    fun las_acciones_del_pie_se_ven_con_la_lista_llena() {
        pintarHojaCon(DOCUMENTOS)

        composicion.onNodeWithTag(TAG_HOJA_DOCUMENTOS).assertIsDisplayed()
        // Las dos del pie son las que caían bajo la barra de navegación: si el aire de
        // abajo se midiera otra vez desde el borde físico, esto se pondría rojo.
        composicion.onNodeWithTag(TAG_ABRIR_OTRO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_CERRAR_TODOS).assertIsDisplayed()
    }

    @Test
    fun tambien_en_tema_claro() {
        // El tema no mueve el layout, pero sí el contraste con la barra del sistema: en
        // claro un desbordamiento se nota menos a ojo, y de eso se encarga el test.
        pintarHojaCon(DOCUMENTOS, PreferenciaTema.CLARO)

        composicion.onNodeWithTag(TAG_ABRIR_OTRO).assertIsDisplayed()
        composicion.onNodeWithTag(TAG_CERRAR_TODOS).assertIsDisplayed()
    }

    private fun pintarHojaCon(
        cuantos: Int,
        preferencia: PreferenciaTema = PreferenciaTema.OSCURO,
    ) {
        val documentos =
            (1..cuantos).map { numero ->
                DocumentoEnLista(
                    id = "doc-$numero",
                    nombre = "documento_largo_de_prueba_$numero.pdf",
                    paginaActual = 0,
                    paginas = 12,
                    abiertoEn = 0L,
                    activo = numero == 1,
                )
            }

        composicion.setContent {
            TemaDracPDF(preferencia = preferencia) {
                HojaDocumentos(
                    documentos = documentos,
                    alPedirMiniatura = {},
                    alElegir = {},
                    alCerrarDocumento = {},
                    alAbrirOtro = {},
                    alCerrarTodos = {},
                    alCerrar = {},
                    // Expandida del todo: a media altura —como se abre por defecto— el
                    // pie queda por debajo del borde de la hoja por diseño, y lo que
                    // aquí se comprueba es que estando abierta entera no se lo coma la
                    // barra de navegación.
                    expandidaDelTodo = true,
                )
            }
        }
        composicion.waitForIdle()
    }

    private companion object {
        /** Suficientes para que la lista empuje el pie hasta el borde de la hoja. */
        const val DOCUMENTOS = 8
    }
}
