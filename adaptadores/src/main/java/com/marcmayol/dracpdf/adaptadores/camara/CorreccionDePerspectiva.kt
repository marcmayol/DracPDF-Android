package com.marcmayol.dracpdf.adaptadores.camara

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Un punto de la foto, en píxeles y contando desde la esquina de arriba a la izquierda.
 *
 * Es un tipo propio y no el `PointF` de Android por un motivo práctico: esta cuenta se
 * prueba en el ordenador, sin emulador ni dispositivo, y con un tipo de la plataforma no
 * se podría. La aritmética de un cuadrilátero no necesita saber que existe Android.
 */
data class PuntoDeHoja(
    val x: Float,
    val y: Float,
) {
    fun escalado(
        ancho: Float,
        alto: Float,
    ) = PuntoDeHoja(x * ancho, y * alto)
}

/**
 * Las cuatro esquinas de la hoja dentro de la foto, **siempre en el mismo orden**.
 *
 * El orden es la mitad del problema. El usuario arrastra los tiradores por donde quiere y
 * puede acabar con el de «arriba a la izquierda» abajo del todo; si se toman tal cual, la
 * hoja sale girada, del revés o retorcida en un reloj de arena. Por eso [de] los ordena
 * antes de dejarlos entrar, y a partir de ahí nadie más tiene que preocuparse.
 */
data class EsquinasDeHoja(
    val superiorIzquierda: PuntoDeHoja,
    val superiorDerecha: PuntoDeHoja,
    val inferiorDerecha: PuntoDeHoja,
    val inferiorIzquierda: PuntoDeHoja,
) {
    /** Las cuatro en el sentido de las agujas del reloj, empezando por la de arriba a la izquierda. */
    fun enLista(): List<PuntoDeHoja> = listOf(superiorIzquierda, superiorDerecha, inferiorDerecha, inferiorIzquierda)

    /**
     * Las mismas esquinas en otra escala.
     *
     * Existe porque las esquinas se guardan **en fracciones de la foto**, de cero a uno, y
     * no en píxeles. La razón es de memoria: durante una sesión de escaneo hay diez hojas
     * apuntadas y una sola descomprimida a la vez, y cada vez que se descomprime puede
     * salir a un tamaño distinto —depende de la memoria que haya—. Con las esquinas en
     * píxeles de una descompresión concreta, el recorte se movería de sitio entre la
     * previsualización y el montaje final.
     */
    fun escalada(
        ancho: Float,
        alto: Float,
    ): EsquinasDeHoja =
        EsquinasDeHoja(
            superiorIzquierda = superiorIzquierda.escalado(ancho, alto),
            superiorDerecha = superiorDerecha.escalado(ancho, alto),
            inferiorDerecha = inferiorDerecha.escalado(ancho, alto),
            inferiorIzquierda = inferiorIzquierda.escalado(ancho, alto),
        )

    /**
     * Cuánto mide la hoja ya enderezada, en píxeles.
     *
     * Se toma **el lado más largo de cada pareja**, no el promedio. La perspectiva hace
     * que el borde lejano salga más corto que el cercano, y quedarse con el corto
     * comprimiría el texto de esa mitad: es más honesto estirar un poco lo que estaba
     * lejos que encoger lo que estaba cerca, porque los píxeles que faltan se pueden
     * interpolar y los que se tiran no vuelven.
     */
    val anchoCorregido: Int
        get() =
            maxOf(
                distancia(superiorIzquierda, superiorDerecha),
                distancia(inferiorIzquierda, inferiorDerecha),
            ).roundToInt().coerceAtLeast(1)

    val altoCorregido: Int
        get() =
            maxOf(
                distancia(superiorIzquierda, inferiorIzquierda),
                distancia(superiorDerecha, inferiorDerecha),
            ).roundToInt().coerceAtLeast(1)

    companion object {
        /**
         * Ordena cuatro puntos sueltos en las cuatro esquinas que son.
         *
         * La regla es de las que se explican en una línea y funcionan siempre que el
         * cuadrilátero no esté volcado: **la suma de las coordenadas** crece hacia abajo y
         * a la derecha, así que la menor es la esquina de arriba a la izquierda y la mayor
         * la de abajo a la derecha; **la resta** crece hacia arriba y a la derecha, y sus
         * extremos son las otras dos. Nada de ángulos ni de centroides: con cuatro puntos,
         * dos comparaciones bastan y no hay funciones trigonométricas que puedan devolver
         * el mismo ángulo para dos esquinas distintas.
         */
        fun de(puntos: List<PuntoDeHoja>): EsquinasDeHoja {
            require(puntos.size == ESQUINAS) { "Una hoja tiene cuatro esquinas, y llegaron ${puntos.size}" }
            val ordenadas =
                EsquinasDeHoja(
                    superiorIzquierda = puntos.minBy { it.x + it.y },
                    superiorDerecha = puntos.maxBy { it.x - it.y },
                    inferiorDerecha = puntos.maxBy { it.x + it.y },
                    inferiorIzquierda = puntos.minBy { it.x - it.y },
                )
            require(ordenadas.enLista().distinct().size == ESQUINAS) {
                "Esos cuatro puntos no forman un cuadrilátero: hay esquinas repetidas"
            }
            return ordenadas
        }

        /**
         * La foto entera, que es por donde empieza cada hoja capturada.
         *
         * Se parte de las cuatro esquinas del fotograma en vez de intentar adivinar dónde
         * está el papel: una detección automática que falla mueve los tiradores a un sitio
         * peor que el borde, y corregirla cuesta más que ponerlos desde cero.
         */
        fun deTodaLaFoto(
            ancho: Int,
            alto: Int,
        ): EsquinasDeHoja =
            EsquinasDeHoja(
                superiorIzquierda = PuntoDeHoja(0f, 0f),
                superiorDerecha = PuntoDeHoja(ancho.toFloat(), 0f),
                inferiorDerecha = PuntoDeHoja(ancho.toFloat(), alto.toFloat()),
                inferiorIzquierda = PuntoDeHoja(0f, alto.toFloat()),
            )

        private fun distancia(
            uno: PuntoDeHoja,
            otro: PuntoDeHoja,
        ) = hypot(otro.x - uno.x, otro.y - uno.y)

        private const val ESQUINAS = 4
    }
}

/**
 * La matriz que endereza la hoja: lleva las cuatro esquinas de la foto a las cuatro
 * esquinas de un rectángulo de [ancho] por [alto].
 *
 * Es una **homografía**, no un giro ni un recorte: una hoja fotografiada de lado no está
 * girada, está en perspectiva, y ninguna combinación de girar, escalar y desplazar puede
 * arreglar eso. Hacen falta los dos coeficientes de perspectiva —los que dividen— y con
 * ellos las líneas rectas siguen saliendo rectas pero los lados que se juntaban a lo
 * lejos vuelven a ser paralelos.
 *
 * Ocho incógnitas y ocho ecuaciones, dos por esquina. La novena entrada de la matriz se
 * fija a 1 porque una homografía sólo está definida salvo un factor: multiplicar las
 * nueve por dos describe exactamente la misma transformación, así que hay que anclar una.
 *
 * El resultado sale en el orden que espera `android.graphics.Matrix.setValues`, que es
 * también el orden natural de leer una matriz de tres por tres. No es una coincidencia
 * afortunada: es quien la va a aplicar.
 */
fun homografiaDe(
    esquinas: EsquinasDeHoja,
    ancho: Int,
    alto: Int,
): FloatArray {
    require(ancho > 0 && alto > 0) { "La hoja enderezada tiene que medir algo, y se pidió $ancho x $alto" }
    val origen = esquinas.enLista()
    val destino =
        listOf(
            PuntoDeHoja(0f, 0f),
            PuntoDeHoja(ancho.toFloat(), 0f),
            PuntoDeHoja(ancho.toFloat(), alto.toFloat()),
            PuntoDeHoja(0f, alto.toFloat()),
        )

    val sistema = Array(INCOGNITAS) { DoubleArray(INCOGNITAS) }
    val terminos = DoubleArray(INCOGNITAS)
    origen.indices.forEach { esquina ->
        val (x, y) = origen[esquina].comoDobles()
        val (u, v) = destino[esquina].comoDobles()
        // u · (perspectivaX·x + perspectivaY·y + 1) = escalaX·x + sesgoX·y + traslaciónX,
        // reordenado para dejar las incógnitas a la izquierda y el término conocido solo.
        sistema[2 * esquina] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -x * u, -y * u)
        terminos[2 * esquina] = u
        sistema[2 * esquina + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -x * v, -y * v)
        terminos[2 * esquina + 1] = v
    }

    val solucion = resolver(sistema, terminos)
    return FloatArray(VALORES_DE_MATRIZ) { posicion ->
        if (posicion == PESO) 1f else solucion[posicion].toFloat()
    }
}

/**
 * Adónde va un punto al aplicarle la matriz.
 *
 * La división por el peso es lo que distingue una perspectiva de una transformación
 * corriente, y es también por lo que esto no puede escribirse como una multiplicación y
 * ya está.
 */
fun aplicar(
    matriz: FloatArray,
    punto: PuntoDeHoja,
): PuntoDeHoja {
    require(matriz.size == VALORES_DE_MATRIZ) { "Una matriz de 3x3 tiene nueve valores, no ${matriz.size}" }
    val peso = matriz[PERSPECTIVA_X] * punto.x + matriz[PERSPECTIVA_Y] * punto.y + matriz[PESO]
    require(abs(peso) > CASI_CERO) { "Ese punto queda en el horizonte de la perspectiva y no tiene destino" }
    return PuntoDeHoja(
        x = (matriz[ESCALA_X] * punto.x + matriz[SESGO_X] * punto.y + matriz[TRASLACION_X]) / peso,
        y = (matriz[SESGO_Y] * punto.x + matriz[ESCALA_Y] * punto.y + matriz[TRASLACION_Y]) / peso,
    )
}

/**
 * Gauss-Jordan con pivoteo parcial: el método de toda la vida para un sistema de ocho.
 *
 * El pivoteo no es un adorno académico. Sin él, una hoja fotografiada de frente —donde
 * los coeficientes de perspectiva salen cero— pone un cero en la diagonal y la división
 * siguiente devuelve infinito. Y ése es justo el caso más común de todos.
 */
private fun resolver(
    sistema: Array<DoubleArray>,
    terminos: DoubleArray,
): DoubleArray {
    val orden = terminos.size
    for (columna in 0 until orden) {
        llevarElMejorPivoteArriba(sistema, terminos, columna)
        eliminarLaColumna(sistema, terminos, columna)
    }
    return DoubleArray(orden) { fila -> terminos[fila] / sistema[fila][fila] }
}

private fun PuntoDeHoja.comoDobles() = x.toDouble() to y.toDouble()

/**
 * Los nombres de las nueve casillas, los mismos que `android.graphics.Matrix`.
 *
 * Están bautizados porque un `matriz[6]` suelto no se puede revisar: hay que ir a contar
 * casillas para saber si es el coeficiente de perspectiva o la traslación.
 */
private const val ESCALA_X = 0
private const val SESGO_X = 1
private const val TRASLACION_X = 2
private const val SESGO_Y = 3
private const val ESCALA_Y = 4
private const val TRASLACION_Y = 5
private const val PERSPECTIVA_X = 6
private const val PERSPECTIVA_Y = 7
private const val PESO = 8

private const val VALORES_DE_MATRIZ = 9
private const val INCOGNITAS = 8

/** Por debajo de esto, un divisor es cero con ruido de coma flotante encima. */
private const val CASI_CERO = 1e-9

/**
 * Sube a la diagonal la fila con el coeficiente más grande de esta columna.
 *
 * Es el pivoteo parcial: sin él, una hoja fotografiada de frente deja un cero en la
 * diagonal y la división siguiente devuelve infinito.
 */
private fun llevarElMejorPivoteArriba(
    sistema: Array<DoubleArray>,
    terminos: DoubleArray,
    columna: Int,
) {
    var pivote = columna
    for (fila in columna + 1 until terminos.size) {
        if (abs(sistema[fila][columna]) > abs(sistema[pivote][columna])) pivote = fila
    }
    require(abs(sistema[pivote][columna]) > CASI_CERO) {
        "Esas cuatro esquinas no definen una hoja: tres de ellas están en línea recta"
    }

    val filaPivote = sistema[columna]
    sistema[columna] = sistema[pivote]
    sistema[pivote] = filaPivote
    val terminoPivote = terminos[columna]
    terminos[columna] = terminos[pivote]
    terminos[pivote] = terminoPivote
}

/**
 * Deja a cero el resto de la columna restando la fila del pivote.
 *
 * Las filas que ya traen un cero se saltan: restarles cero no cambia nada y evita
 * arrastrar redondeos.
 */
private fun eliminarLaColumna(
    sistema: Array<DoubleArray>,
    terminos: DoubleArray,
    columna: Int,
) {
    val orden = terminos.size
    for (fila in (0 until orden).filter { it != columna }) {
        val factor = sistema[fila][columna] / sistema[columna][columna]
        if (factor != 0.0) {
            for (celda in columna until orden) {
                sistema[fila][celda] -= factor * sistema[columna][celda]
            }
            terminos[fila] -= factor * terminos[columna]
        }
    }
}
