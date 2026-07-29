package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento

/**
 * El puerto de los formularios. Como [DocumentRepository], no sabe que debajo hay
 * MuPDF, es síncrono, y quien lo implementa serializa las llamadas al hilo del
 * documento.
 *
 * **Se lee por páginas, no de golpe.** Sacar la lista completa de campos obligaría a
 * cargar las 500 páginas de un documento largo para pintar la primera, que es
 * exactamente lo que la Fase 1 se cuidó de no hacer. El overlay pide los campos de
 * las páginas que están en pantalla, igual que pide sus píxeles.
 */
interface FormService {
    /**
     * Qué formulario trae el documento y cuántos campos tiene. Recorre el árbol de
     * campos del catálogo, que es barato: no carga páginas ni dibuja nada.
     */
    fun formulario(id: IdDocumento): Formulario

    /**
     * Los campos que caen en una página, en el orden en que el PDF los declara. Ese
     * orden es el que fija el [com.marcmayol.dracpdf.dominio.modelo.IdCampo], así que
     * tiene que ser estable entre llamadas.
     */
    fun camposDePagina(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario>
}
