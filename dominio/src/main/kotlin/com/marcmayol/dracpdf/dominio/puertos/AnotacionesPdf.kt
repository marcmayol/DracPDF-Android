package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion

/**
 * Marcar el documento: resaltar, subrayar, tachar y pegar notas.
 *
 * Todo lo que entra por aquí **cambia el documento en memoria** y necesita guardarse
 * después, como el relleno de un formulario: el puerto no guarda por su cuenta porque
 * marcar cinco párrafos seguidos dejaría cinco revisiones en el fichero.
 */
interface AnotacionesPdf {
    /** Las que ya tiene esa página, vengan de donde vengan. */
    fun listar(
        id: IdDocumento,
        pagina: Int,
    ): List<Anotacion>

    /**
     * Marca los rectángulos indicados, que son los de una selección de texto.
     *
     * @return la anotación creada, ya con su posición dentro de la página.
     */
    fun marcar(
        id: IdDocumento,
        pagina: Int,
        tipo: TipoAnotacion,
        marcos: List<RectPt>,
        color: ColorAnotacion,
    ): Anotacion

    /** Pega una nota en un punto de la página. */
    fun anotar(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        texto: String,
        color: ColorAnotacion,
    ): Anotacion

    /**
     * Escribe texto sobre la página.
     *
     * La fuente va **embebida**: un PDF que dependa de que el aparato de quien lo abra
     * tenga instalada la misma tipografía es un PDF que se verá distinto en cada
     * pantalla, y en un documento que alguien va a firmar eso no vale.
     */
    fun escribir(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        texto: String,
        tamano: Float,
    ): Anotacion

    /** Quita una anotación por su posición en la página. */
    fun borrar(
        id: IdDocumento,
        pagina: Int,
        posicion: Int,
    )
}
