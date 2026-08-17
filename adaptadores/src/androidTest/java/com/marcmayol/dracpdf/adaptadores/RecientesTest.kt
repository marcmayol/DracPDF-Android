package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.recientes.RecientesEnFichero
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.DocumentoReciente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Los recientes, escritos y releídos de verdad.
 *
 * Es la mitad del criterio F7 que habla de persistencia: lo que importa no es que la
 * lista exista en memoria, sino que **siga ahí mañana**, con la página por la que se
 * iba. Por eso cada prueba vuelve a construir el almacén desde el mismo fichero, como
 * haría la aplicación al arrancar.
 */
@RunWith(AndroidJUnit4::class)
class RecientesTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var fichero: File

    @Before
    fun limpiar() {
        fichero = File(contexto.cacheDir, "recientes-prueba.json")
        fichero.delete()
    }

    @Test
    fun lo_apuntado_sigue_ahi_al_volver_a_abrir_la_aplicacion() {
        RecientesEnFichero(fichero).anotar(reciente("contrato.pdf", visto = 100))

        val alArrancar = RecientesEnFichero(fichero).listar()

        assertEquals(1, alArrancar.size)
        assertEquals("contrato.pdf", alArrancar.single().nombre)
    }

    @Test
    fun el_ultimo_visto_va_primero() {
        val almacen = RecientesEnFichero(fichero)
        almacen.anotar(reciente("viejo.pdf", visto = 100))
        almacen.anotar(reciente("nuevo.pdf", visto = 300))
        almacen.anotar(reciente("medio.pdf", visto = 200))

        assertEquals(listOf("nuevo.pdf", "medio.pdf", "viejo.pdf"), almacen.listar().map { it.nombre })
    }

    @Test
    fun abrir_dos_veces_el_mismo_no_lo_duplica() {
        val almacen = RecientesEnFichero(fichero)
        almacen.anotar(reciente("contrato.pdf", visto = 100))
        almacen.anotar(reciente("contrato.pdf", visto = 500))

        assertEquals(1, almacen.listar().size)
        assertEquals(500L, almacen.listar().single().visto)
    }

    @Test
    fun se_recuerda_por_donde_iba_cada_documento() {
        val almacen = RecientesEnFichero(fichero)
        almacen.anotar(reciente("manual.pdf", visto = 100))

        almacen.anotarPosicion(origenDe("manual.pdf"), pagina = 41, zoom = 2.5f)

        val guardado = RecientesEnFichero(fichero).listar().single()
        assertEquals(41, guardado.pagina)
        assertEquals(2.5f, guardado.zoom, 0.001f)
    }

    @Test
    fun anotar_la_posicion_de_uno_que_no_esta_no_lo_inventa() {
        val almacen = RecientesEnFichero(fichero)

        almacen.anotarPosicion(origenDe("fantasma.pdf"), pagina = 3, zoom = 1f)

        assertTrue(almacen.listar().isEmpty())
    }

    @Test
    fun la_lista_no_crece_sin_fin() {
        val almacen = RecientesEnFichero(fichero, tope = 3)
        (1..5).forEach { numero -> almacen.anotar(reciente("doc$numero.pdf", visto = numero.toLong())) }

        // Se quedan los tres últimos: un reciente que hace veinte documentos que no se
        // mira ya no es reciente.
        assertEquals(listOf("doc5.pdf", "doc4.pdf", "doc3.pdf"), almacen.listar().map { it.nombre })
    }

    @Test
    fun olvidar_saca_uno_de_la_lista_sin_tocar_los_demas() {
        val almacen = RecientesEnFichero(fichero)
        almacen.anotar(reciente("uno.pdf", visto = 100))
        almacen.anotar(reciente("dos.pdf", visto = 200))

        almacen.olvidar(origenDe("uno.pdf"))

        assertEquals(listOf("dos.pdf"), RecientesEnFichero(fichero).listar().map { it.nombre })
    }

    @Test
    fun un_fichero_corrupto_no_tira_la_aplicacion_al_arrancar() {
        // Es lo primero que se lee al abrir: perder la lista es una molestia, caerse
        // por un JSON a medias sería un fallo de arranque.
        fichero.writeText("{esto no es json")

        assertTrue(RecientesEnFichero(fichero).listar().isEmpty())
    }

    private fun reciente(
        nombre: String,
        visto: Long,
    ) = DocumentoReciente(origen = origenDe(nombre), nombre = nombre, visto = visto, permisoPersistido = true)

    private fun origenDe(nombre: String) = OrigenDocumento.Externo("content://prueba/$nombre", nombre)
}
