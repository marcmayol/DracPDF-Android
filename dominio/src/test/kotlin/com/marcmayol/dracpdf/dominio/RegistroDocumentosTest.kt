package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RegistroDocumentosTest {
    private fun registroCon(vararg nombres: String): Pair<RegistroDocumentos, List<DocumentoAbierto>> {
        val registro = RegistroDocumentos()
        val documentos =
            nombres.map { nombre ->
                DocumentoAbierto(registro.nuevoId(), nombre, paginas = 10).also { registro.registrar(it) }
            }
        return registro to documentos
    }

    @Test
    fun `los documentos abiertos salen en el orden en que se abrieron`() {
        val (registro, documentos) = registroCon("uno.pdf", "dos.pdf", "tres.pdf")

        assertEquals(documentos.map { it.id }, registro.abiertos().map { it.id })
    }

    @Test
    fun `recuerda por donde iba cada documento y a que zoom`() {
        val (registro, documentos) = registroCon("uno.pdf", "dos.pdf")

        registro.anotarPagina(documentos[0].id, 7)
        registro.anotarZoom(documentos[0].id, 2.5f)

        assertEquals(7, registro.estado(documentos[0].id).paginaActual)
        assertEquals(2.5f, registro.estado(documentos[0].id).zoom, 0f)
        // Y no contamina al otro documento.
        assertEquals(0, registro.estado(documentos[1].id).paginaActual)
        assertEquals(1f, registro.estado(documentos[1].id).zoom, 0f)
    }

    @Test
    fun `marcar y desmarcar cambia solo el documento indicado`() {
        val (registro, documentos) = registroCon("uno.pdf", "dos.pdf")

        registro.marcar(documentos[0].id, Marca.CAMBIOS_SIN_GUARDAR)

        assertTrue(registro.hayCambiosSinGuardar())
        assertFalse(registro.estado(documentos[1].id).documento.tieneCambiosSinGuardar)

        registro.desmarcar(documentos[0].id, Marca.CAMBIOS_SIN_GUARDAR)
        assertFalse(registro.hayCambiosSinGuardar())
    }

    @Test
    fun `preguntar por un documento que no esta abierto es un error de dominio`() {
        val registro = RegistroDocumentos()

        assertThrows(ErrorDocumento.NoEstaAbierto::class.java) { registro.estado(registro.nuevoId()) }
    }

    @Test
    fun `aguanta que varios hilos lo toquen a la vez`() {
        // La interfaz lee desde el hilo principal mientras los casos de uso escriben
        // desde hilos de trabajo: si el registro no fuera seguro, aquí saldría un
        // ConcurrentModificationException o un contador con huecos.
        val registro = RegistroDocumentos()
        val hilos = 8
        val porHilo = 50
        val pistoletazo = CountDownLatch(1)
        val ejecutor = Executors.newFixedThreadPool(hilos)

        repeat(hilos) {
            ejecutor.submit {
                pistoletazo.await()
                repeat(porHilo) {
                    val documento = DocumentoAbierto(registro.nuevoId(), "x.pdf", paginas = 3)
                    registro.registrar(documento)
                    registro.anotarPagina(documento.id, 1)
                    registro.abiertos()
                }
            }
        }
        pistoletazo.countDown()
        ejecutor.shutdown()
        assertTrue(ejecutor.awaitTermination(30, TimeUnit.SECONDS))

        assertEquals(hilos * porHilo, registro.abiertos().size)
    }
}
