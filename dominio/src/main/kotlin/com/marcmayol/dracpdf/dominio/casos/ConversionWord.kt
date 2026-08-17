package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ComponedorDePdf
import com.marcmayol.dracpdf.dominio.puertos.ConversorWord
import com.marcmayol.dracpdf.dominio.puertos.Progreso

/**
 * PDF→Word: escribe como `.docx` lo que se haya podido entender del documento.
 *
 * **No recibe el PDF, recibe lo que se sacó de él**, y es deliberado: deducir títulos y
 * tablas de un PDF es un trabajo que ya hace otro —el mismo para Markdown, HTML, ODT y
 * RTF— y repetirlo aquí daría dos criterios distintos de qué es un título según el
 * formato al que se exporte. Este caso de uso sólo se ocupa de la parte que es de Word.
 *
 * Tampoco comprueba si el PDF de origen estaba firmado, por lo mismo que
 * [ConvertirDocumento]: sacar el texto de un contrato firmado para leerlo en otro sitio
 * es legítimo; lo que no se puede es reescribir el PDF.
 */
class ConvertirAWord(
    private val word: ConversorWord,
) {
    /**
     * @return `false` si no había nada que escribir, que en un PDF escaneado es lo
     *   normal. Un `.docx` con cero letras es un fichero que el usuario abriría en
     *   blanco sin saber por qué; mejor decirlo antes de escribirlo.
     */
    operator fun invoke(
        documento: DocumentoEstructurado,
        destino: OrigenDocumento,
    ): Boolean {
        if (documento.vacio) return false
        word.escribir(documento, destino)
        return true
    }
}

/**
 * Word→PDF: lee el `.docx` y compone un PDF con su contenido.
 *
 * Son dos pasos y dos puertos porque son dos problemas distintos —entender el XML de
 * Word y repartir texto en hojas—, y el segundo sirve igual para las demás entrantes.
 */
class ConvertirWordAPdf(
    private val word: ConversorWord,
    private val componedor: ComponedorDePdf,
) {
    /**
     * @return cuántas páginas tiene el PDF resultante; 0 si el `.docx` no tenía texto
     *   —y entonces no se escribe nada, en vez de dejar un PDF con una hoja en blanco—.
     */
    operator fun invoke(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Int {
        require(origen.identificador != destino.identificador) {
            "El PDF no puede sobrescribir el documento de Word del que sale"
        }
        val documento = word.leer(origen)
        if (documento.vacio) return 0
        return componedor.componer(documento, destino, progreso)
    }
}
