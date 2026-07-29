package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
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

    /**
     * Escribe el valor de un campo de texto y devuelve cómo queda.
     *
     * Devuelve el campo releído del documento en vez del valor que se le pasó, y no
     * es lo mismo: el PDF puede recortar por `MaxLen` o reformatear un número, y lo
     * que hay que enseñar en pantalla es lo que quedó guardado, no lo que se tecleó.
     */
    fun escribirTexto(
        id: IdDocumento,
        campo: IdCampo,
        valor: String,
    ): CampoFormulario

    /**
     * Cambia el estado de una casilla o de un botón de radio.
     *
     * Es el motor quien lo hace y no esta aplicación, porque marcar un radio significa
     * desmarcar a sus hermanos de grupo, y el grupo lo conoce el documento.
     */
    fun alternar(
        id: IdDocumento,
        campo: IdCampo,
    ): CampoFormulario

    /** Elige una opción de un combo o de una lista. */
    fun elegirOpcion(
        id: IdDocumento,
        campo: IdCampo,
        opcion: String,
    ): CampoFormulario
}
