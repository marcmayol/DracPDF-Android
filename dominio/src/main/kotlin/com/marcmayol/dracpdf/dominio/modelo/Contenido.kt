package com.marcmayol.dracpdf.dominio.modelo

/**
 * Un punto de la página, en puntos PDF.
 *
 * El origen está **arriba a la izquierda**, como en [RectPt] y como en todo lo que
 * entrega el motor. No es donde lo pone el formato —dentro del PDF se cuenta desde
 * abajo— pero sí donde lo espera cualquiera que dibuje sobre la página, y tener dos
 * sistemas conviviendo sería la forma más fácil de colocar las cosas del revés.
 */
data class PuntoPt(
    val x: Float,
    val y: Float,
)

/**
 * Una coincidencia de la búsqueda.
 *
 * Lleva la página además del marco porque las coincidencias se enseñan en una lista
 * que salta entre páginas: sin ella, un resultado no sabría a dónde llevar.
 */
data class Coincidencia(
    val pagina: Int,
    val marco: RectPt,
)

/**
 * Lo que se lleva una selección: el texto que se va a copiar y los rectángulos que hay
 * que pintar encima.
 *
 * Los dos vienen del motor y no se deducen el uno del otro: el texto respeta el orden
 * de lectura del documento —que no es el orden visual— y los rectángulos son uno por
 * línea tocada, porque una selección de tres líneas no es un rectángulo.
 */
data class SeleccionTexto(
    val texto: String,
    val marcos: List<RectPt>,
) {
    val hayAlgo: Boolean get() = texto.isNotBlank()

    companion object {
        val NINGUNA = SeleccionTexto("", emptyList())
    }
}

/**
 * Una entrada del índice del documento.
 *
 * La jerarquía va en [nivel] y no en una lista de hijos: el índice se enseña como una
 * lista con sangría, y aplanarlo aquí evita que la interfaz tenga que recorrer un
 * árbol para dibujar algo que ya es una lista.
 */
data class EntradaIndice(
    val titulo: String,
    /** A qué página lleva. `null` si el destino no es una página de este documento. */
    val pagina: Int?,
    val nivel: Int,
)

/** A dónde lleva un enlace de una página. */
sealed interface DestinoEnlace {
    /** Otra página del mismo documento. */
    data class Pagina(
        val numero: Int,
    ) : DestinoEnlace

    /**
     * Fuera del documento. Se guarda tal cual viene —incluido el esquema— porque
     * decidir si se abre es de quien lo va a abrir, no de quien lo lee.
     */
    data class Fuera(
        val url: String,
    ) : DestinoEnlace
}

/** Un enlace dibujado en una página. */
data class EnlacePagina(
    val marco: RectPt,
    val destino: DestinoEnlace,
)

/**
 * Si el punto cae dentro del rectángulo.
 *
 * Vive en el dominio y no en la interfaz porque lo pregunta quien decide qué hacer con
 * un toque, y esa decisión —«esto es un enlace»— es del documento, no de la pantalla.
 */
fun RectPt.contiene(punto: PuntoPt): Boolean = punto.x in x0..x1 && punto.y in y0..y1

/**
 * Lo que el documento dice de sí mismo.
 *
 * Todo es opcional porque en un PDF todo lo es: la mitad de los documentos del mundo
 * no tienen título y muchos traen de productor el programa que los generó hace quince
 * años. Se enseña lo que haya, y lo que no haya no se rellena con inventos.
 */
data class PropiedadesDocumento(
    val titulo: String? = null,
    val autor: String? = null,
    val asunto: String? = null,
    val palabrasClave: String? = null,
    val creador: String? = null,
    val productor: String? = null,
    val creado: String? = null,
    val modificado: String? = null,
    val formato: String? = null,
    val paginas: Int = 0,
    /** Lo que ocupa el fichero, si se puede saber. */
    val bytes: Long? = null,
    val cifrado: Boolean = false,
)
