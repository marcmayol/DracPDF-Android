package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ConversorEntradas
import com.marcmayol.dracpdf.dominio.puertos.EntradaAConvertir
import com.marcmayol.dracpdf.dominio.puertos.Progreso

/**
 * Meter en un PDF lo que ya está en el teléfono: imágenes, texto, Markdown o HTML.
 *
 * **No mira si algo está firmado, y es a propósito.** Aquí no se reescribe ningún
 * documento existente: se crea uno nuevo a partir de ficheros que no son PDF, así que no
 * hay firma que romper. Es la misma razón por la que [ConvertirDocumento] tampoco lo
 * mira, y por el mismo motivo: negarse sería confundir «no romper la firma» con «no
 * dejar trabajar».
 *
 * Lo que sí comprueba es lo que ninguna operación de la casa puede saltarse —que el
 * resultado no pise una de sus entradas—, más lo único que tiene sentido preguntar
 * antes: que se haya elegido algo.
 */
class ConvertirAPdf(
    private val conversor: ConversorEntradas,
) {
    /**
     * @return cuántas páginas tiene el PDF resultante; 0 si no había nada que escribir,
     *   y entonces el destino queda intacto. La interfaz tiene que poder decirlo: un
     *   `.txt` vacío no es un fallo, pero entregar un documento en blanco sin avisar sí.
     */
    operator fun invoke(
        entradas: List<EntradaAConvertir>,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Int {
        require(entradas.isNotEmpty()) { "No se ha elegido nada que convertir" }
        require(entradas.none { it.origen.identificador == destino.identificador }) {
            "El resultado no puede ser uno de los ficheros de origen: se leería mientras se escribe"
        }
        return conversor.aPdf(entradas, destino, progreso)
    }
}
