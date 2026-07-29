package com.marcmayol.dracpdf.dominio.modelo

/** Identidad de una firma guardada en la biblioteca. */
@JvmInline
value class IdFirma(
    val valor: String,
)

/**
 * Una firma dibujada a mano y guardada para volver a usarla.
 *
 * Aquí no viven los píxeles: viven en el almacén, y esto es su ficha. Una lista de
 * firmas en pantalla no tiene por qué cargar en memoria todos los trazos de todas
 * ellas, y quien decide cuándo leer los bytes es quien los va a dibujar.
 */
data class Firma(
    val id: IdFirma,
    val nombre: String,
    /** Cuándo se dibujó, en milisegundos del reloj del sistema. */
    val creadaEn: Long,
    val anchoPx: Int,
    val altoPx: Int,
) {
    init {
        require(anchoPx > 0 && altoPx > 0) { "Una firma mide más que cero: $anchoPx x $altoPx" }
    }

    /**
     * Alto entre ancho. Es lo que mantiene la firma con su forma al colocarla: se
     * elige el ancho sobre la página y el alto sale de aquí, nunca al revés ni por
     * separado, o la letra queda estirada.
     */
    val proporcion: Float get() = altoPx.toFloat() / anchoPx.toFloat()
}

/**
 * Una firma ya estampada sobre una página.
 *
 * Se devuelve al estampar para poder deshacerlo y para que la interfaz sepa dónde
 * quedó sin tener que releer el documento.
 */
data class Estampado(
    val pagina: Int,
    val marco: RectPt,
)
