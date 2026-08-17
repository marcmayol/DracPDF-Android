package com.marcmayol.dracpdf.dominio.modelo

/**
 * Un documento visto como contenido y no como papel: títulos, párrafos y tablas.
 *
 * Es la pieza que hace posible convertir sin escribir cada conversión desde cero. El
 * escritorio llegó a la misma conclusión y sacó la deducción de títulos a un módulo
 * común: da igual que el destino sea Markdown, HTML, ODT, RTF o Word, porque todos
 * necesitan lo mismo —qué es un título y qué es un párrafo— y sólo cambian las
 * etiquetas con las que lo escriben.
 *
 * **Un PDF no dice qué es un título.** No hay nada en el formato que lo marque: hay
 * texto con un tamaño de letra. Lo que se hace aquí es deducirlo, y por eso todo lo
 * que salga de aquí se etiqueta «reformateado» en la interfaz.
 */
data class DocumentoEstructurado(
    val bloques: List<BloqueDeTexto>,
) {
    /** Si no se ha sacado texto de ninguna parte: casi siempre, un escaneado. */
    val vacio: Boolean get() = bloques.none { it is BloqueDeTexto.Parrafo || it is BloqueDeTexto.Titulo }
}

/** Un trozo de documento con su papel dentro del texto. */
sealed interface BloqueDeTexto {
    /**
     * Un título, con su nivel deducido del tamaño de la letra.
     *
     * El nivel es relativo al propio documento: el texto más grande es nivel 1
     * aunque mida diez puntos, porque lo que hace un título no es su tamaño absoluto
     * sino ser más grande que lo que lo rodea.
     */
    data class Titulo(
        val texto: String,
        val nivel: Int,
    ) : BloqueDeTexto

    data class Parrafo(
        val texto: String,
    ) : BloqueDeTexto

    /**
     * Una tabla, por filas y celdas.
     *
     * Puede venir **marcada como aproximada**: detectar tablas en un PDF no es leer
     * una estructura, es adivinarla a partir de líneas y de alineaciones, y el
     * escritorio aprendió que ninguna estrategia acierta sola. Lo honesto es decir con
     * cuál se obtuvo.
     */
    data class Tabla(
        val filas: List<List<String>>,
        val pagina: Int,
        val aproximada: Boolean = false,
    ) : BloqueDeTexto {
        val columnas: Int get() = filas.maxOfOrNull { it.size } ?: 0
    }

    /** El corte entre páginas, que algunos formatos saben respetar y otros no. */
    data class SaltoDePagina(
        val pagina: Int,
    ) : BloqueDeTexto
}
