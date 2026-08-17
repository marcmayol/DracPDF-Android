package com.marcmayol.dracpdf.dominio.modelo

/**
 * Lo que se puede marcar sobre un documento.
 *
 * Son las **anotaciones estándar del formato PDF**, no dibujos propios: eso es lo que
 * hace que un resaltado hecho aquí se vea en Adobe, en el visor del ordenador y en el
 * del que reciba el documento, y que se pueda borrar desde cualquiera de ellos. Pintar
 * un rectángulo amarillo encima del texto habría sido más fácil y habría producido un
 * documento que sólo esta aplicación entiende.
 */
enum class TipoAnotacion {
    RESALTADO,
    SUBRAYADO,
    TACHADO,

    /** La nota pegada: un icono que al tocarlo enseña lo que dice. */
    NOTA,

    /** Texto escrito directamente sobre la página. */
    TEXTO,
}

/**
 * El color de una marca.
 *
 * Es un puñado de colores con nombre y no un selector de millones: en un móvil, lo
 * que se hace con un resaltado es distinguir tres o cuatro cosas, y una rueda de color
 * sólo añade decisiones. Los valores son los del papel, no los del tema: una marca
 * amarilla lo es también de noche.
 */
@Suppress("MagicNumber") // Un color ES sus tres componentes: nombrarlas no explicaría nada.
enum class ColorAnotacion(
    val rojo: Float,
    val verde: Float,
    val azul: Float,
) {
    AMARILLO(0.85f, 0.71f, 0.36f),
    VERDE(0.44f, 0.75f, 0.53f),
    AZUL(0.36f, 0.53f, 0.74f),
    ROSA(0.88f, 0.48f, 0.44f),
}

/**
 * Una anotación que está en el documento.
 *
 * El identificador lo pone quien la lee y **no sobrevive a cerrar el documento**: el
 * PDF no numera sus anotaciones, así que es la posición dentro de la página. Sirve
 * para borrar la que se acaba de tocar, que es para lo único que hace falta.
 */
data class Anotacion(
    val pagina: Int,
    val posicion: Int,
    val tipo: TipoAnotacion,
    /** Uno por línea tocada: una marca de tres líneas no es un rectángulo. */
    val marcos: List<RectPt>,
    val contenido: String = "",
    val color: ColorAnotacion = ColorAnotacion.AMARILLO,
) {
    /** El marco que las abarca todas, para saber si el dedo ha caído encima. */
    val marco: RectPt?
        get() =
            if (marcos.isEmpty()) {
                null
            } else {
                RectPt(
                    x0 = marcos.minOf { it.x0 },
                    y0 = marcos.minOf { it.y0 },
                    x1 = marcos.maxOf { it.x1 },
                    y1 = marcos.maxOf { it.y1 },
                )
            }
}
