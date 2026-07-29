package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firmas.AlmacenFirmasFichero
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFirmas
import com.marcmayol.dracpdf.dominio.modelo.IdFirma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/**
 * La biblioteca de firmas en disco.
 *
 * Lo que se prueba aquí no es que guarde —eso sería difícil de fallar—, sino cómo se
 * comporta cuando algo ha ido mal antes: un JSON a medias, un PNG sin ficha, una
 * ficha sin PNG. En un móvil el proceso muere sin avisar, así que ese estado no es
 * hipotético: es el que se encuentra al abrir la aplicación la vez siguiente.
 */
@RunWith(AndroidJUnit4::class)
class AlmacenFirmasTest {
    private lateinit var carpeta: File
    private lateinit var almacen: AlmacenFirmasFichero

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        carpeta = File(contexto.cacheDir, "firmas-${System.nanoTime()}")
        carpeta.deleteRecursively()
        almacen = AlmacenFirmasFichero(carpeta)
    }

    @Test
    fun una_firma_guardada_se_lee_igual_que_se_escribio() {
        val png = GeneradorFirmas.png()

        val firma = almacen.guardar(png, GeneradorFirmas.ANCHO, GeneradorFirmas.ALTO, "La mía")

        assertEquals("La mía", firma.nombre)
        assertEquals(GeneradorFirmas.ANCHO, firma.anchoPx)
        assertTrue(almacen.leer(firma.id).contentEquals(png))
        assertEquals(listOf(firma.id), almacen.listar().map { it.id })
    }

    @Test
    fun la_proporcion_es_la_del_dibujo_y_no_la_del_lienzo() {
        val firma = almacen.guardar(GeneradorFirmas.png(), 240, 80, "")

        // Un tercio: es lo que hace que al colocarla no salga estirada.
        assertEquals(80f / 240f, firma.proporcion, 0.001f)
    }

    @Test
    fun las_firmas_salen_de_la_mas_nueva_a_la_mas_vieja() {
        val primera = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Primera")
        Thread.sleep(RESOLUCION_DEL_RELOJ_MS)
        val segunda = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Segunda")

        assertEquals(listOf(segunda.id, primera.id), almacen.listar().map { it.id })
    }

    @Test
    fun cada_firma_tiene_su_propia_identidad() {
        val una = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Igual")
        val otra = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Igual")

        // Mismo dibujo y mismo nombre, y aun así son dos: borrar una no puede llevarse
        // la otra por delante.
        assertNotEquals(una.id, otra.id)
        assertEquals(2, almacen.listar().size)
    }

    @Test
    fun una_ficha_sin_su_imagen_se_ignora() {
        val firma = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Huérfana")
        File(carpeta, "${firma.id.valor}.png").delete()

        // No se enseña lo que no se puede dibujar, y no se lanza ningún error por
        // algo que el usuario no ha hecho.
        assertTrue(almacen.listar().isEmpty())
    }

    @Test
    fun una_imagen_sin_ficha_tampoco_cuenta() {
        val firma = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Sin ficha")
        File(carpeta, "${firma.id.valor}.json").delete()

        // Es justo lo que queda si el proceso muere entre los dos ficheros: la firma
        // no existe todavía, y el PNG suelto no molesta a nadie.
        assertTrue(almacen.listar().isEmpty())
    }

    @Test
    fun una_ficha_corrupta_no_tira_la_biblioteca_abajo() {
        val buena = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Buena")
        val rota = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Rota")
        File(carpeta, "${rota.id.valor}.json").writeText("{esto no es json")

        assertEquals(listOf(buena.id), almacen.listar().map { it.id })
    }

    @Test
    fun borrar_una_firma_la_quita_de_verdad() {
        val firma = almacen.guardar(GeneradorFirmas.png(), 240, 80, "Temporal")

        almacen.borrar(firma.id)

        assertTrue(almacen.listar().isEmpty())
        assertThrows(IOException::class.java) { almacen.leer(firma.id) }
        assertTrue("No puede quedar basura en disco", carpeta.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun leer_una_firma_que_no_existe_es_un_error_claro() {
        assertThrows(IOException::class.java) { almacen.leer(IdFirma("no-existe")) }
    }

    @Test
    fun una_firma_sin_pixeles_se_rechaza() {
        assertThrows(IllegalArgumentException::class.java) {
            almacen.guardar(ByteArray(0), 240, 80, "Vacía")
        }
    }

    private companion object {
        /** Lo justo para que dos altas seguidas no compartan milisegundo. */
        const val RESOLUCION_DEL_RELOJ_MS = 5L
    }
}
