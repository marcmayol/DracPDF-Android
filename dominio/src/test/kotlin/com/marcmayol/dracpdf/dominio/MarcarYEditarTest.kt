package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.EditarContenido
import com.marcmayol.dracpdf.dominio.casos.MarcarDocumento
import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import com.marcmayol.dracpdf.dominio.puertos.AnotacionesPdf
import com.marcmayol.dracpdf.dominio.puertos.EdicionPdf
import com.marcmayol.dracpdf.dominio.puertos.ImagenEnPagina
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las reglas que valen para todo lo que escribe en el documento.
 *
 * Son las mismas desde la Fase 2 y se prueban juntas porque juntas se rompen: **un
 * documento firmado no se toca** y **nada guarda por su cuenta**. Cada caso de uso
 * nuevo que escriba en el PDF tiene que pasar por aquí.
 */
class MarcarYEditarTest {
    private val marco = RectPt(10f, 10f, 100f, 30f)

    @Test
    fun marcar_deja_el_documento_con_cambios_sin_guardar() {
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = false)
        val motor = AnotacionesFalsas()

        MarcarDocumento(motor, registro).marcar(id, 0, TipoAnotacion.RESALTADO, listOf(marco))

        assertTrue(registro.estado(id).documento.tieneCambiosSinGuardar)
        assertEquals(1, motor.creadas.size)
    }

    @Test
    fun un_documento_firmado_no_se_marca() {
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = true)
        val motor = AnotacionesFalsas()

        assertThrows(ErrorDocumento.DocumentoFirmado::class.java) {
            MarcarDocumento(motor, registro).marcar(id, 0, TipoAnotacion.RESALTADO, listOf(marco))
        }
        assertTrue("Se llegó a tocar el documento firmado", motor.creadas.isEmpty())
    }

    @Test
    fun borrar_una_marca_de_un_firmado_tampoco() {
        // Quitarle una anotación a un PDF firmado le rompe la firma igual que ponérsela.
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = true)

        assertThrows(ErrorDocumento.DocumentoFirmado::class.java) {
            MarcarDocumento(AnotacionesFalsas(), registro).borrar(id, 0, 0)
        }
    }

    @Test
    fun las_notas_y_el_texto_no_se_marcan_sobre_una_seleccion() {
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = false)

        // Tienen su propia acción porque piden algo más que un rectángulo: piden qué
        // dicen.
        assertThrows(IllegalArgumentException::class.java) {
            MarcarDocumento(AnotacionesFalsas(), registro).marcar(id, 0, TipoAnotacion.NOTA, listOf(marco))
        }
    }

    @Test
    fun una_pagina_que_no_existe_se_rechaza_antes_de_llegar_al_motor() {
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = false)
        val motor = AnotacionesFalsas()

        assertThrows(IllegalArgumentException::class.java) {
            MarcarDocumento(motor, registro).marcar(id, 99, TipoAnotacion.RESALTADO, listOf(marco))
        }
        assertTrue(motor.creadas.isEmpty())
    }

    @Test
    fun editar_el_contenido_de_un_firmado_tampoco_se_deja() {
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = true)
        val motor = EdicionFalsa()

        assertThrows(ErrorDocumento.DocumentoFirmado::class.java) {
            EditarContenido(motor, registro).corregirTexto(id, 0, marco, "otro")
        }
        assertTrue(motor.corregidos.isEmpty())
    }

    @Test
    fun una_correccion_que_no_cabe_no_marca_cambios() {
        val registro = RegistroDocumentos()
        val id = registrar(registro, firmado = false)
        val motor = EdicionFalsa(cabe = false)

        val hecho = EditarContenido(motor, registro).corregirTexto(id, 0, marco, "no cabe")

        assertFalse(hecho)
        // Si no se hizo nada, el documento no tiene cambios que guardar: decir lo
        // contrario dejaría al usuario guardando la nada.
        assertFalse(registro.estado(id).documento.tieneCambiosSinGuardar)
    }

    private fun registrar(
        registro: RegistroDocumentos,
        firmado: Boolean,
    ): IdDocumento {
        val id = registro.nuevoId()
        registro.registrar(DocumentoAbierto(id, "documento.pdf", paginas = 3))
        if (firmado) registro.marcar(id, Marca.FIRMADO)
        return id
    }

    private class AnotacionesFalsas : AnotacionesPdf {
        val creadas = mutableListOf<Anotacion>()

        override fun listar(
            id: IdDocumento,
            pagina: Int,
        ): List<Anotacion> = creadas

        override fun marcar(
            id: IdDocumento,
            pagina: Int,
            tipo: TipoAnotacion,
            marcos: List<RectPt>,
            color: ColorAnotacion,
        ): Anotacion = Anotacion(pagina, creadas.size, tipo, marcos, color = color).also { creadas += it }

        override fun anotar(
            id: IdDocumento,
            pagina: Int,
            marco: RectPt,
            texto: String,
            color: ColorAnotacion,
        ): Anotacion =
            Anotacion(pagina, creadas.size, TipoAnotacion.NOTA, listOf(marco), texto, color).also { creadas += it }

        override fun escribir(
            id: IdDocumento,
            pagina: Int,
            marco: RectPt,
            texto: String,
            tamano: Float,
        ): Anotacion = Anotacion(pagina, creadas.size, TipoAnotacion.TEXTO, listOf(marco), texto).also { creadas += it }

        override fun borrar(
            id: IdDocumento,
            pagina: Int,
            posicion: Int,
        ) {
            creadas.removeAt(posicion)
        }
    }

    private class EdicionFalsa(
        private val cabe: Boolean = true,
    ) : EdicionPdf {
        val corregidos = mutableListOf<String>()

        override fun imagenesDe(
            id: IdDocumento,
            pagina: Int,
        ): List<ImagenEnPagina> = emptyList()

        override fun anadirImagen(
            id: IdDocumento,
            pagina: Int,
            marco: RectPt,
            imagen: ByteArray,
        ) = Unit

        override fun quitarImagen(
            id: IdDocumento,
            pagina: Int,
            marco: RectPt,
        ) = Unit

        override fun corregirTexto(
            id: IdDocumento,
            pagina: Int,
            marco: RectPt,
            nuevo: String,
            tamano: Float,
        ): Boolean {
            if (cabe) corregidos += nuevo
            return cabe
        }
    }
}
