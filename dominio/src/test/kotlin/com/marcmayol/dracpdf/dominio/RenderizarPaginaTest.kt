package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderizarPaginaTest {
    private val origen = OrigenDocumento.Externo("content://prueba/1", "contrato.pdf")

    private fun escenario(paginas: Int = 12): Triple<RepositorioFalso, RegistroDocumentos, RenderizarPagina> {
        val repositorio = RepositorioFalso(paginas = paginas)
        val registro = RegistroDocumentos()
        AbrirDocumento(repositorio, registro)(origen)
        return Triple(repositorio, registro, RenderizarPagina(repositorio, registro))
    }

    @Test
    fun `renderiza la pagina pedida a la escala pedida`() {
        val (repositorio, registro, renderizar) = escenario()
        val id = registro.abiertos().first().id

        val pagina = renderizar(id, pagina = 2, escala = 2f)

        assertEquals(2, pagina.pagina)
        assertEquals(2f, pagina.escala, 0f)
        assertEquals(listOf(2 to 2f), repositorio.renderizadas)
        assertEquals(pagina.ancho * pagina.alto * 4, pagina.bytes)
    }

    @Test
    fun `una pagina fuera de rango se rechaza sin llegar al motor`() {
        val (repositorio, registro, renderizar) = escenario(paginas = 12)
        val id = registro.abiertos().first().id

        assertThrows(IllegalArgumentException::class.java) { renderizar(id, 12, 1f) }
        assertThrows(IllegalArgumentException::class.java) { renderizar(id, 900, 1f) }
        assertThrows(IllegalArgumentException::class.java) { renderizar(id, -1, 1f) }

        // Lo que importa: la biblioteca nativa no ha visto ninguno de esos índices.
        assertTrue(repositorio.renderizadas.isEmpty())
    }

    @Test
    fun `la primera y la ultima pagina si estan dentro de rango`() {
        val (_, registro, renderizar) = escenario(paginas = 12)
        val id = registro.abiertos().first().id

        renderizar(id, 0, 1f)
        renderizar(id, 11, 1f)
    }

    @Test
    fun `una escala no positiva se rechaza`() {
        val (repositorio, registro, renderizar) = escenario()
        val id = registro.abiertos().first().id

        assertThrows(IllegalArgumentException::class.java) { renderizar(id, 0, 0f) }
        assertThrows(IllegalArgumentException::class.java) { renderizar(id, 0, -2f) }
        assertTrue(repositorio.renderizadas.isEmpty())
    }

    @Test
    fun `una escala desmedida se rechaza antes de quedarse sin memoria`() {
        val (repositorio, registro, renderizar) = escenario()
        val id = registro.abiertos().first().id

        assertThrows(IllegalArgumentException::class.java) {
            renderizar(id, 0, RenderizarPagina.ESCALA_MAXIMA + 0.1f)
        }
        assertTrue(repositorio.renderizadas.isEmpty())
    }

    @Test
    fun `el tamano de pagina tambien valida el rango`() {
        val (_, registro, renderizar) = escenario(paginas = 3)
        val id = registro.abiertos().first().id

        assertEquals(595f, renderizar.tamano(id, 0).ancho, 0f)
        assertThrows(IllegalArgumentException::class.java) { renderizar.tamano(id, 3) }
    }
}
