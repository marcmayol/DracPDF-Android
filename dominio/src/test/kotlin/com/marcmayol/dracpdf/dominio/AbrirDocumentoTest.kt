package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.CerrarDocumento
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AbrirDocumentoTest {
    private val origen = OrigenDocumento.Externo("content://prueba/1", "contrato_arrendamiento.pdf")

    @Test
    fun `abrir da de alta el documento en el registro`() {
        val registro = RegistroDocumentos()
        val estado = AbrirDocumento(RepositorioFalso(paginas = 12), registro)(origen)

        assertEquals("contrato_arrendamiento.pdf", estado.documento.nombre)
        assertEquals(12, estado.documento.paginas)
        assertTrue(registro.estaAbierto(estado.id))
        assertEquals(0, estado.paginaActual)
        assertEquals(1f, estado.zoom, 0f)
    }

    @Test
    fun `cada documento abierto recibe un identificador de sesion distinto`() {
        val registro = RegistroDocumentos()
        val abrir = AbrirDocumento(RepositorioFalso(), registro)

        // El mismo fichero abierto dos veces son dos documentos para el registro.
        val uno = abrir(origen)
        val otro = abrir(origen)

        assertNotEquals(uno.id, otro.id)
        assertEquals(2, registro.abiertos().size)
    }

    @Test
    fun `un documento ya firmado nace marcado, aunque lo firmara otro`() {
        val registro = RegistroDocumentos()
        val estado = AbrirDocumento(RepositorioFalso(firmado = true), registro)(origen)

        assertTrue(estado.documento.estaFirmado)
        assertTrue(Marca.FIRMADO in registro.estado(estado.id).documento.marcas)
    }

    @Test
    fun `un documento cifrado sin contrasena pide la contrasena`() {
        val registro = RegistroDocumentos()
        val abrir = AbrirDocumento(RepositorioFalso(contrasenaCorrecta = "abrete"), registro)

        assertThrows(ErrorDocumento.NecesitaContrasena::class.java) { abrir(origen) }
        // Y no deja un documento a medio registrar.
        assertTrue(registro.abiertos().isEmpty())
    }

    @Test
    fun `una contrasena incorrecta no abre el documento`() {
        val registro = RegistroDocumentos()
        val abrir = AbrirDocumento(RepositorioFalso(contrasenaCorrecta = "abrete"), registro)

        assertThrows(ErrorDocumento.ContrasenaIncorrecta::class.java) { abrir(origen, "sesamo") }
        assertTrue(registro.abiertos().isEmpty())
    }

    @Test
    fun `con la contrasena correcta el documento abre`() {
        val registro = RegistroDocumentos()
        val abrir = AbrirDocumento(RepositorioFalso(contrasenaCorrecta = "abrete"), registro)

        val estado = abrir(origen, "abrete")

        assertTrue(registro.estaAbierto(estado.id))
    }

    @Test
    fun `cerrar suelta el motor antes que el registro`() {
        val registro = RegistroDocumentos()
        val repositorio = RepositorioFalso()
        val estado = AbrirDocumento(repositorio, registro)(origen)

        CerrarDocumento(repositorio, registro)(estado.id)

        assertEquals(listOf(estado.id), repositorio.cerrados)
        assertFalse(registro.estaAbierto(estado.id))
    }

    @Test
    fun `cerrar un documento que no esta abierto es un error de dominio`() {
        val registro = RegistroDocumentos()
        val repositorio = RepositorioFalso()
        val cerrar = CerrarDocumento(repositorio, registro)

        assertThrows(ErrorDocumento.NoEstaAbierto::class.java) { cerrar(registro.nuevoId()) }
        assertTrue(repositorio.cerrados.isEmpty())
    }
}
