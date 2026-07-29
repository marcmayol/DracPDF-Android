package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RellenarCampoTest {
    private val origen = OrigenDocumento.Externo("content://prueba/1", "solicitud.pdf")

    private val texto = FormServiceFalso.campoTexto(0, 0, nombre = "nombre")
    private val bloqueado =
        FormServiceFalso.campoTexto(0, 1, nombre = "referencia", valor = "ABC-123").copy(soloLectura = true)
    private val casilla = FormServiceFalso.campoTexto(0, 2, nombre = "acepta").copy(tipo = TipoCampo.CASILLA)
    private val combo =
        FormServiceFalso
            .campoTexto(0, 3, nombre = "provincia", valor = "Baleares")
            .copy(tipo = TipoCampo.COMBO, opciones = listOf("Baleares", "Madrid"))

    private class Escenario(
        val id: IdDocumento,
        val registro: RegistroDocumentos,
        val servicio: FormServiceFalso,
        val rellenar: RellenarCampo,
        val repositorio: RepositorioFalso,
    )

    private fun escenario(firmado: Boolean = false): Escenario {
        val servicio = FormServiceFalso(camposPorPagina = mapOf(0 to listOf(texto, bloqueado, casilla, combo)))
        val registro = RegistroDocumentos()
        val repositorio = RepositorioFalso(firmado = firmado)
        AbrirDocumento(repositorio, registro)(origen)
        val id = registro.abiertos().first().id
        return Escenario(id, registro, servicio, RellenarCampo(servicio, registro), repositorio)
    }

    private fun marcado(e: Escenario) =
        Marca.CAMBIOS_SIN_GUARDAR in
            e.registro
                .estado(e.id)
                .documento.marcas

    @Test
    fun escribir_un_campo_de_texto_devuelve_lo_que_quedo_guardado() {
        val e = escenario()

        val despues = e.rellenar.texto(e.id, IdCampo(0, 0), "Marc")

        assertEquals("Marc", despues.valor)
        assertEquals(listOf(IdCampo(0, 0) to "Marc"), e.servicio.escrituras)
    }

    @Test
    fun escribir_marca_el_documento_pero_no_lo_guarda() {
        val e = escenario()
        assertFalse(marcado(e))

        e.rellenar.texto(e.id, IdCampo(0, 0), "Marc")

        assertTrue("El documento tiene que quedar marcado como sin guardar", marcado(e))
        // Y nadie ha escrito en el fichero: guardar es una decisión aparte.
        assertTrue(e.repositorio.guardados.isEmpty())
    }

    @Test
    fun un_campo_de_solo_lectura_no_se_deja_escribir() {
        val e = escenario()

        assertThrows(IllegalArgumentException::class.java) {
            e.rellenar.texto(e.id, IdCampo(0, 1), "otra cosa")
        }
        assertTrue("No debería haber llegado nada al motor", e.servicio.escrituras.isEmpty())
        assertFalse(marcado(e))
    }

    @Test
    fun un_documento_firmado_rechaza_la_edicion() {
        val e = escenario(firmado = true)

        // Y lo rechaza con un error propio, no con uno genérico: quien lo capture
        // tiene que poder ofrecer «guardar una copia editable».
        assertThrows(ErrorDocumento.DocumentoFirmado::class.java) {
            e.rellenar.texto(e.id, IdCampo(0, 0), "Marc")
        }
        assertTrue(e.servicio.escrituras.isEmpty())
    }

    @Test
    fun alternar_una_casilla_la_marca_y_la_desmarca() {
        val e = escenario()

        val marcada = e.rellenar.alternar(e.id, IdCampo(0, 2))
        assertTrue(marcada.marcado)

        val desmarcada = e.rellenar.alternar(e.id, IdCampo(0, 2))
        assertFalse(desmarcada.marcado)
        assertEquals(CampoFormulario.APAGADO, desmarcada.valor)
    }

    @Test
    fun no_se_puede_alternar_lo_que_no_es_una_marca() {
        val e = escenario()

        assertThrows(IllegalArgumentException::class.java) { e.rellenar.alternar(e.id, IdCampo(0, 0)) }
        assertThrows(IllegalArgumentException::class.java) { e.rellenar.texto(e.id, IdCampo(0, 2), "x") }
        assertTrue(e.servicio.escrituras.isEmpty())
    }

    @Test
    fun elegir_una_opcion_que_el_documento_no_ofrece_se_rechaza() {
        val e = escenario()

        assertEquals("Madrid", e.rellenar.elegir(e.id, IdCampo(0, 3), "Madrid").valor)
        assertThrows(IllegalArgumentException::class.java) {
            e.rellenar.elegir(e.id, IdCampo(0, 3), "Teruel")
        }
        assertEquals(1, e.servicio.escrituras.size)
    }

    @Test
    fun un_campo_que_ya_no_esta_se_rechaza_sin_llegar_al_motor() {
        val e = escenario()

        assertThrows(IllegalArgumentException::class.java) { e.rellenar.texto(e.id, IdCampo(0, 99), "x") }
        assertTrue(e.servicio.escrituras.isEmpty())
    }

    // ------------------------------------------------------------------- guardar

    @Test
    fun guardar_escribe_y_limpia_la_marca() {
        val e = escenario()
        e.rellenar.texto(e.id, IdCampo(0, 0), "Marc")
        e.repositorio.cambiosPendientes = true

        val guardo = GuardarDocumento(e.repositorio, e.registro)(e.id)

        assertTrue(guardo)
        assertEquals(listOf(e.id), e.repositorio.guardados)
        assertFalse(marcado(e))
    }

    @Test
    fun si_el_guardado_falla_el_documento_sigue_marcado() {
        val e = escenario()
        e.rellenar.texto(e.id, IdCampo(0, 0), "Marc")
        e.repositorio.cambiosPendientes = true
        e.repositorio.fallaAlGuardar = true

        assertThrows(ErrorDocumento::class.java) { GuardarDocumento(e.repositorio, e.registro)(e.id) }

        // Lo que importa: nadie ha creído que estaba guardado. Limpiar la marca antes
        // de que el motor escriba es la forma más limpia de perder el trabajo de otro.
        assertTrue(marcado(e))
    }

    @Test
    fun guardar_sin_cambios_no_escribe_nada() {
        val e = escenario()
        e.repositorio.cambiosPendientes = false

        val guardo = GuardarDocumento(e.repositorio, e.registro)(e.id)

        assertFalse("No había nada que guardar", guardo)
        assertTrue(e.repositorio.guardados.isEmpty())
    }
}
