package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.casos.RecordarDocumentos
import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AlmacenRecientes
import com.marcmayol.dracpdf.dominio.puertos.DocumentoReciente
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Volver a donde lo dejaste.
 *
 * Lo que se prueba aquí es la promesa entera de los recientes: que al reabrir un
 * documento el registro ya sabe por qué página iba, y que una página que ya no existe
 * —porque el documento ha cambiado de tamaño desde la última vez— no se restaura.
 */
class RecordarDocumentosTest {
    private val origen = OrigenDocumento.Externo("content://prueba/manual.pdf", "manual.pdf")

    @Test
    fun al_reabrir_se_recupera_la_pagina_y_el_zoom_de_la_ultima_vez() {
        val registro = RegistroDocumentos()
        val almacen = AlmacenFalso(mutableListOf(guardado(pagina = 12, zoom = 1.8f)))
        val id = abrirEnElRegistro(registro, paginas = 40)

        RecordarDocumentos(almacen, registro, motorCon(origen)).alAbrir(id, origen, "manual.pdf", true)

        assertEquals(12, registro.estado(id).paginaActual)
        assertEquals(1.8f, registro.estado(id).zoom, 0.001f)
    }

    @Test
    fun una_pagina_que_ya_no_existe_no_se_restaura() {
        val registro = RegistroDocumentos()
        // El documento tenía 40 páginas y ahora tiene 3: lo han organizado o dividido
        // por el camino. Abrir en la 12 sería peor que abrir por el principio.
        val almacen = AlmacenFalso(mutableListOf(guardado(pagina = 12, zoom = 2f)))
        val id = abrirEnElRegistro(registro, paginas = 3)

        RecordarDocumentos(almacen, registro, motorCon(origen)).alAbrir(id, origen, "manual.pdf", true)

        assertEquals(0, registro.estado(id).paginaActual)
    }

    @Test
    fun abrir_apunta_el_documento_aunque_sea_la_primera_vez() {
        val registro = RegistroDocumentos()
        val almacen = AlmacenFalso(mutableListOf())
        val id = abrirEnElRegistro(registro, paginas = 5)

        RecordarDocumentos(almacen, registro, motorCon(origen)).alAbrir(id, origen, "manual.pdf", false)

        assertEquals(listOf("manual.pdf"), almacen.guardados.map { it.nombre })
        // Sin permiso permanente se apunta igual, pero diciendo que puede no abrir.
        assertEquals(false, almacen.guardados.single().permisoPersistido)
    }

    @Test
    fun al_cerrar_se_guarda_por_donde_iba() {
        val registro = RegistroDocumentos()
        val almacen = AlmacenFalso(mutableListOf(guardado(pagina = 0, zoom = 1f)))
        val id = abrirEnElRegistro(registro, paginas = 30)
        registro.anotarPagina(id, 17)
        registro.anotarZoom(id, 2.5f)

        RecordarDocumentos(almacen, registro, motorCon(origen)).alCerrar(id)

        assertEquals(17, almacen.guardados.single().pagina)
        assertEquals(2.5f, almacen.guardados.single().zoom, 0.001f)
    }

    /**
     * El motor, con el documento ya abierto: es de ahí de donde el caso de uso saca el
     * origen al cerrar, igual que en la aplicación de verdad.
     */
    private fun motorCon(origen: OrigenDocumento) =
        RepositorioFalso().also { it.abrir(IdDocumento("motor"), origen, null) }

    private fun abrirEnElRegistro(
        registro: RegistroDocumentos,
        paginas: Int,
    ): IdDocumento {
        val id = registro.nuevoId()
        registro.registrar(DocumentoAbierto(id, "manual.pdf", paginas))
        return id
    }

    private fun guardado(
        pagina: Int,
        zoom: Float,
    ) = DocumentoReciente(origen, "manual.pdf", visto = 1L, pagina = pagina, zoom = zoom, permisoPersistido = true)

    /** Un almacén en memoria, con las mismas reglas que el de verdad. */
    private class AlmacenFalso(
        val guardados: MutableList<DocumentoReciente>,
    ) : AlmacenRecientes {
        override fun listar(): List<DocumentoReciente> = guardados.sortedByDescending { it.visto }

        override fun anotar(reciente: DocumentoReciente) {
            guardados.removeAll { it.origen.identificador == reciente.origen.identificador }
            guardados.add(reciente)
        }

        override fun anotarPosicion(
            origen: OrigenDocumento,
            pagina: Int,
            zoom: Float,
        ) {
            val donde = guardados.indexOfFirst { it.origen.identificador == origen.identificador }
            if (donde >= 0) guardados[donde] = guardados[donde].copy(pagina = pagina, zoom = zoom)
        }

        override fun olvidar(origen: OrigenDocumento) {
            guardados.removeAll { it.origen.identificador == origen.identificador }
        }

        override fun olvidarTodos() = guardados.clear()
    }
}
