package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ListarCamposTest {
    private val origen = OrigenDocumento.Externo("content://prueba/1", "solicitud.pdf")

    private fun escenario(
        servicio: FormServiceFalso,
        paginas: Int = 12,
    ): Pair<IdDocumento, ListarCampos> {
        val registro = RegistroDocumentos()
        AbrirDocumento(RepositorioFalso(paginas = paginas), registro)(origen)
        return registro.abiertos().first().id to ListarCampos(servicio, registro)
    }

    @Test
    fun `devuelve los campos de la pagina pedida`() {
        val servicio =
            FormServiceFalso(
                camposPorPagina =
                    mapOf(
                        0 to listOf(FormServiceFalso.campoTexto(0, 0, "nombre", "Marc")),
                        1 to listOf(FormServiceFalso.campoTexto(1, 0), FormServiceFalso.campoTexto(1, 1)),
                    ),
            )
        val (id, listar) = escenario(servicio)

        assertEquals(listOf("nombre"), listar(id, 0).map { it.nombre })
        assertEquals(2, listar(id, 1).size)
        assertTrue(listar(id, 2).isEmpty())
    }

    @Test
    fun `la identidad de un campo es su pagina y su posicion, no su nombre`() {
        // Dos radios del mismo grupo comparten nombre a propósito: el nombre es el
        // grupo, y confundirlo con la identidad haría que marcar uno marcara el otro.
        val servicio =
            FormServiceFalso(
                camposPorPagina =
                    mapOf(
                        0 to
                            listOf(
                                FormServiceFalso.campoTexto(0, 0, nombre = "sexo").copy(tipo = TipoCampo.RADIO),
                                FormServiceFalso.campoTexto(0, 1, nombre = "sexo").copy(tipo = TipoCampo.RADIO),
                            ),
                    ),
            )
        val (id, listar) = escenario(servicio)

        val campos = listar(id, 0)
        assertEquals(listOf("sexo", "sexo"), campos.map { it.nombre })
        assertEquals(listOf(IdCampo(0, 0), IdCampo(0, 1)), campos.map { it.id })
    }

    @Test
    fun `una pagina fuera de rango se rechaza sin llegar al motor`() {
        val servicio = FormServiceFalso()
        val (id, listar) = escenario(servicio, paginas = 12)

        assertThrows(IllegalArgumentException::class.java) { listar(id, 12) }
        assertThrows(IllegalArgumentException::class.java) { listar(id, -1) }
        assertTrue(servicio.paginasConsultadas.isEmpty())
    }

    @Test
    fun `un documento sin formulario no tiene nada que rellenar`() {
        val servicio = FormServiceFalso(tipo = TipoFormulario.NINGUNO, camposPorPagina = emptyMap())
        val (id, listar) = escenario(servicio)

        assertFalse(listar.formulario(id).esRellenable)
        assertEquals(0, listar.formulario(id).campos)
    }

    @Test
    fun `un XFA puro no se recorre pagina a pagina`() {
        val servicio = FormServiceFalso(tipo = TipoFormulario.XFA_PURO)
        val (id, listar) = escenario(servicio)

        assertTrue(listar(id, 0).isEmpty())
        // Lo que importa: no se ha ido a buscar campos que no se pueden rellenar, así
        // que la interfaz puede avisar en vez de enseñar un formulario mudo.
        assertTrue(servicio.paginasConsultadas.isEmpty())
        assertFalse(listar.formulario(id).esRellenable)
    }

    @Test
    fun `un XFA hibrido si se rellena, porque el AcroForm esta debajo`() {
        val servicio = FormServiceFalso(tipo = TipoFormulario.XFA_HIBRIDO)
        val (id, listar) = escenario(servicio)

        assertEquals(1, listar(id, 0).size)
        assertTrue(listar.formulario(id).esRellenable)
    }

    @Test
    fun `un documento que no esta abierto no tiene formulario`() {
        val (_, listar) = escenario(FormServiceFalso())

        assertThrows(com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento.NoEstaAbierto::class.java) {
            listar.formulario(IdDocumento("doc-inventado"))
        }
    }
}
