package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.EnlacePagina
import com.marcmayol.dracpdf.dominio.modelo.EntradaIndice
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.PropiedadesDocumento
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.SeleccionTexto

/**
 * El texto de un documento abierto: dónde está lo que se busca y qué dice lo que se
 * señala.
 *
 * Va **página a página** a propósito. Un puerto que devolviera «todas las
 * coincidencias del documento» escondería dentro un recorrido de quinientas páginas
 * que nadie podría cancelar ni contar; así, quien busca decide el ritmo y puede parar.
 */
interface TextoPdf {
    /** Dónde aparece [texto] en esa página. Vacío si no aparece. */
    fun buscar(
        id: IdDocumento,
        pagina: Int,
        texto: String,
    ): List<RectPt>

    /**
     * La palabra que hay bajo un punto, para arrancar una selección con el dedo.
     *
     * Devuelve `null` si ahí no hay texto: en un escaneado no lo hay en ninguna parte,
     * y es mejor no seleccionar nada que seleccionar un trozo de imagen.
     */
    fun palabraEn(
        id: IdDocumento,
        pagina: Int,
        punto: PuntoPt,
    ): SeleccionTexto?

    /** Lo que hay seleccionado entre dos puntos de la misma página. */
    fun seleccionEntre(
        id: IdDocumento,
        pagina: Int,
        desde: PuntoPt,
        hasta: PuntoPt,
    ): SeleccionTexto

    /** Todo el texto de una página, en su orden de lectura. */
    fun textoDe(
        id: IdDocumento,
        pagina: Int,
    ): String
}

/**
 * Lo que el documento dice de su propia forma: su índice, sus enlaces y su ficha.
 *
 * Está separado del texto porque son dos cosas que se piden en momentos distintos —el
 * índice al abrir un panel, el texto al buscar o al arrastrar el dedo— y juntarlas
 * habría dado un puerto que ningún adaptador implementa entero de una vez.
 */
interface EstructuraPdf {
    /**
     * El índice del documento, aplanado y con su nivel. Vacío si no trae ninguno, que
     * es lo normal en documentos escaneados o generados a la ligera.
     */
    fun indice(id: IdDocumento): List<EntradaIndice>

    /** Los enlaces de una página, internos y externos. */
    fun enlaces(
        id: IdDocumento,
        pagina: Int,
    ): List<EnlacePagina>

    fun propiedades(id: IdDocumento): PropiedadesDocumento
}
