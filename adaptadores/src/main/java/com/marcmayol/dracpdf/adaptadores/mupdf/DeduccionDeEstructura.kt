package com.marcmayol.dracpdf.adaptadores.mupdf

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Una palabra con dónde empieza y dónde acaba, en puntos PDF. */
internal data class PalabraLeida(
    val texto: String,
    val x0: Float,
    val x1: Float,
)

/**
 * Una línea de la página, ya sin nada nativo dentro.
 *
 * Se copia a datos propios antes de salir del hilo del documento porque un `TextLine` de
 * MuPDF sostiene memoria del motor; guardarlo para procesarlo luego sería justo el
 * objeto escapado que se lleva el proceso por delante.
 */
internal data class LineaLeida(
    /** En qué bloque del motor venía: es la pista de qué líneas son el mismo párrafo. */
    val bloque: Int,
    val palabras: List<PalabraLeida>,
    /** El alto de sus letras, que es lo más cerca del cuerpo de la fuente que hay. */
    val tamano: Float,
    val y0: Float,
    val y1: Float,
) {
    val texto: String get() = palabras.joinToString(" ") { it.texto }
    val letras: Int get() = palabras.sumOf { it.texto.length }
}

/**
 * De líneas con coordenadas a títulos, párrafos y tablas.
 *
 * **Aquí está toda la adivinación, y está separada del motor a propósito.** Un PDF no
 * dice qué es un título ni dónde hay una tabla: dice que hay unas letras de tal tamaño en
 * tal sitio. Teniendo la deducción aparte de la lectura se puede razonar sobre ella —y
 * cambiarla— sin tocar nada que hable con MuPDF, y se prueba dándole líneas inventadas.
 *
 * El criterio de los títulos es el mismo que el escritorio sacó a un módulo común: el
 * tamaño más frecuente **ponderado por letras** es el cuerpo, y lo bastante mayor que el
 * cuerpo es un título. Ponderar por letras y no por líneas importa: un documento con
 * treinta títulos cortos y diez párrafos largos tiene más líneas de título que de cuerpo,
 * y sin ponderar el «cuerpo» saldría siendo el tamaño de los títulos.
 *
 * Son doce funciones pequeñas y no una grande a propósito: cada paso de la deducción
 * —el cuerpo, los títulos, las columnas, las filas— se lee y se prueba por separado.
 * Juntarlas para bajar la cuenta daría exactamente el método ilegible que este objeto
 * evita.
 */
@Suppress("TooManyFunctions")
internal object DeduccionDeEstructura {
    /**
     * @param paginas una lista de líneas por página, en el orden del documento.
     */
    fun documentoDe(paginas: List<List<LineaLeida>>): DocumentoEstructurado {
        val cuerpo = tamanoDelCuerpo(paginas)
        val bloques = mutableListOf<BloqueDeTexto>()

        paginas.forEachIndexed { numero, lineas ->
            val deLaPagina = bloquesDe(lineas, cuerpo, numero)
            if (deLaPagina.isEmpty()) return@forEachIndexed
            // El salto va **entre** páginas con contenido: uno al principio, uno al final
            // o uno por cada hoja en blanco de un escaneado serían cortes que no separan
            // nada, y en un documento sin texto dejarían un fichero de puras rayas.
            if (bloques.isNotEmpty()) bloques += BloqueDeTexto.SaltoDePagina(numero)
            bloques += deLaPagina
        }
        return DocumentoEstructurado(bloques)
    }

    /** El tamaño de letra más usado del documento, contando por letras y no por líneas. */
    private fun tamanoDelCuerpo(paginas: List<List<LineaLeida>>): Float {
        val cuenta = LinkedHashMap<Float, Int>()
        paginas.forEach { pagina ->
            pagina.forEach { linea ->
                val clave = (linea.tamano * DECIMAL).roundToInt() / DECIMAL
                cuenta[clave] = (cuenta[clave] ?: 0) + linea.letras
            }
        }
        return cuenta.maxByOrNull { it.value }?.key ?: TAMANO_HABITUAL
    }

    /**
     * El nivel de un título es **relativo al documento**: importa cuánto mayor es que el
     * cuerpo, no cuánto mide. Un folleto compuesto en 8 puntos tiene títulos de 14 y un
     * libro los tiene de 24, y los dos son títulos de primer nivel en el suyo.
     */
    private fun nivelDeTitulo(
        tamano: Float,
        cuerpo: Float,
    ): Int {
        if (cuerpo <= 0f) return 0
        val razon = tamano / cuerpo
        return when {
            razon >= RAZON_PRIMER_NIVEL -> 1
            razon >= RAZON_SEGUNDO_NIVEL -> 2
            razon >= RAZON_TERCER_NIVEL -> NIVEL_TERCERO
            else -> 0
        }
    }

    private const val NIVEL_TERCERO = 3

    /** Un bloque con la altura a la que empieza, para poder devolverlos en orden. */
    private class Situado(
        val y: Float,
        val bloque: BloqueDeTexto,
    )

    private fun bloquesDe(
        lineas: List<LineaLeida>,
        cuerpo: Float,
        pagina: Int,
    ): List<BloqueDeTexto> {
        val ordenadas = fusionarFilas(lineas.sortedBy { it.y0 })
        val consumidas = mutableSetOf<Int>()
        val tablas = tablasDe(ordenadas, cuerpo, pagina, consumidas)
        // Se mezclan y se ordenan por altura: una tabla en medio de la página tiene que
        // salir entre el párrafo de encima y el de debajo, no al final.
        return (tablas + textosDe(ordenadas, consumidas, cuerpo)).sortedBy { it.y }.map { it.bloque }
    }

    /**
     * Junta en una sola las líneas que están a la misma altura dentro de un bloque.
     *
     * **Sin esto no hay tablas que valgan.** Una fila de tabla no se dibuja como una
     * cadena con espacios: se dibuja celda a celda, cada una con su orden de texto en su
     * coordenada, y MuPDF entrega cada celda como una línea distinta del mismo bloque. Lo
     * que aquí es una fila con tres columnas llegaría como tres líneas de una palabra, sin
     * un solo hueco que medir.
     *
     * Se exige **el mismo bloque** a propósito, y ahí está la diferencia entre esto y una
     * fusión por altura a secas: en un texto a dos columnas, la primera línea de cada
     * columna también está a la misma altura, y juntarlas convertiría un artículo entero en
     * una tabla de dos columnas. El motor ya ha separado esas dos columnas en bloques
     * distintos, así que respetar sus bloques es respetar esa decisión.
     */
    private fun fusionarFilas(lineas: List<LineaLeida>): List<LineaLeida> {
        val fusionadas = mutableListOf<LineaLeida>()
        lineas.forEach { linea ->
            val previa = fusionadas.lastOrNull()
            if (previa != null && previa.bloque == linea.bloque && aLaMismaAltura(previa, linea)) {
                fusionadas[fusionadas.lastIndex] = unir(previa, linea)
            } else {
                fusionadas += linea
            }
        }
        return fusionadas
    }

    /** Se solapan en vertical más de media línea: están en el mismo renglón. */
    private fun aLaMismaAltura(
        una: LineaLeida,
        otra: LineaLeida,
    ): Boolean {
        val solape = min(una.y1, otra.y1) - max(una.y0, otra.y0)
        val menor = min(una.y1 - una.y0, otra.y1 - otra.y0)
        return menor > 0f && solape > menor * FRACCION_DE_SOLAPE
    }

    private fun unir(
        una: LineaLeida,
        otra: LineaLeida,
    ): LineaLeida {
        val letras = una.letras + otra.letras
        return LineaLeida(
            bloque = una.bloque,
            // Por x y no por orden de dibujo: nada obliga a un PDF a escribir las celdas de
            // izquierda a derecha, y el orden de las columnas es el que se ve.
            palabras = (una.palabras + otra.palabras).sortedBy { it.x0 },
            // Media ponderada por letras: una celda de dos caracteres en cuerpo grande no
            // convierte la fila entera en un título.
            tamano =
                if (letras > 0) {
                    (una.tamano * una.letras + otra.tamano * otra.letras) / letras
                } else {
                    max(una.tamano, otra.tamano)
                },
            y0 = min(una.y0, otra.y0),
            y1 = max(una.y1, otra.y1),
        )
    }

    // -- Tablas ---------------------------------------------------------------

    /** Un trozo de línea separado del siguiente por un hueco de los de columna. */
    private class Celda(
        val texto: String,
        val x0: Float,
    )

    private class FilaCandidata(
        val indice: Int,
        val linea: LineaLeida,
        val celdas: List<Celda>,
    )

    /**
     * Las tablas de una página, deducidas de las posiciones.
     *
     * **En esta versión del binding no hay `find_tables`**, así que no se pueden buscar
     * las líneas dibujadas de la rejilla como hace el escritorio: lo único que se tiene
     * son las coordenadas del texto. Una tabla es entonces un grupo de líneas seguidas que
     * parten en el mismo número de trozos y cuyos trozos empiezan a la misma altura
     * horizontal. Todo lo que salga de aquí se marca **aproximada**, que es exactamente
     * para lo que existe ese campo del modelo.
     *
     * @param consumidas se rellena con los índices de las líneas que ya son tabla, para
     *   que no salgan otra vez como párrafos.
     */
    private fun tablasDe(
        lineas: List<LineaLeida>,
        cuerpo: Float,
        pagina: Int,
        consumidas: MutableSet<Int>,
    ): List<Situado> {
        val tablas = mutableListOf<Situado>()
        var racha = mutableListOf<FilaCandidata>()

        fun cerrar() {
            if (racha.size >= FILAS_MINIMAS) {
                tablas +=
                    Situado(
                        y = racha.first().linea.y0,
                        bloque =
                            BloqueDeTexto.Tabla(
                                filas = racha.map { fila -> fila.celdas.map { it.texto } },
                                pagina = pagina,
                                aproximada = true,
                            ),
                    )
                racha.forEach { consumidas += it.indice }
            }
            racha = mutableListOf()
        }

        lineas.forEachIndexed { indice, linea ->
            val celdas = celdasDe(linea, cuerpo)
            if (celdas.size < COLUMNAS_MINIMAS) {
                cerrar()
                return@forEachIndexed
            }
            if (racha.isNotEmpty() && !encajaEn(racha, linea, celdas, cuerpo)) cerrar()
            racha += FilaCandidata(indice, linea, celdas)
        }
        cerrar()
        return tablas
    }

    /**
     * Parte una línea en celdas por los huecos grandes.
     *
     * El umbral es relativo al tamaño de la letra y no una cifra en puntos: en un texto de
     * 8 puntos un hueco de 10 ya es una columna, y en uno de 24 es un espacio ancho. El
     * espacio entre palabras de una fuente normal ronda un tercio del cuerpo, así que
     * exigir casi un cuerpo entero deja fuera hasta las separaciones de un texto
     * justificado.
     */
    private fun celdasDe(
        linea: LineaLeida,
        cuerpo: Float,
    ): List<Celda> {
        if (linea.palabras.isEmpty()) return emptyList()
        val hueco = max(linea.tamano, cuerpo) * FACTOR_DE_COLUMNA
        val celdas = mutableListOf<Celda>()
        var texto = StringBuilder(linea.palabras.first().texto)
        var inicio = linea.palabras.first().x0
        var derecha = linea.palabras.first().x1

        linea.palabras.drop(1).forEach { palabra ->
            if (palabra.x0 - derecha > hueco) {
                celdas += Celda(texto.toString(), inicio)
                texto = StringBuilder(palabra.texto)
                inicio = palabra.x0
            } else {
                texto.append(' ').append(palabra.texto)
            }
            derecha = max(derecha, palabra.x1)
        }
        celdas += Celda(texto.toString(), inicio)
        return celdas
    }

    /**
     * Si una línea sigue la tabla que se está formando.
     *
     * Se piden las tres cosas a la vez porque cada una sola se equivoca: mismo número de
     * columnas lo cumple cualquier par de líneas de dos palabras muy separadas, las
     * columnas alineadas las cumple un texto en dos bloques, y la cercanía vertical la
     * cumple todo lo que va seguido. Juntas dejan pasar poco que no sea una tabla.
     */
    private fun encajaEn(
        racha: List<FilaCandidata>,
        linea: LineaLeida,
        celdas: List<Celda>,
        cuerpo: Float,
    ): Boolean {
        val referencia = racha.first().celdas
        if (celdas.size != referencia.size) return false
        val tolerancia = cuerpo * FACTOR_DE_TOLERANCIA
        if (celdas.indices.any { abs(celdas[it].x0 - referencia[it].x0) > tolerancia }) return false
        val separacion = linea.y0 - racha.last().linea.y1
        return separacion <= max(linea.tamano, cuerpo) * FACTOR_DE_SALTO
    }

    // -- Títulos y párrafos ---------------------------------------------------

    /**
     * Lo que no es tabla, agrupado en párrafos y títulos.
     *
     * Se agrupa por bloque del motor **y por nivel de título**. Lo primero porque MuPDF ya
     * junta las líneas que van seguidas, que es el trabajo de encontrar dónde acaba un
     * párrafo. Lo segundo porque a veces no lo hace: un título pegado a su primer párrafo
     * llega en un solo bloque, y sin separar por nivel el párrafo entero acabaría siendo
     * un título de veinte líneas.
     */
    private fun textosDe(
        lineas: List<LineaLeida>,
        consumidas: Set<Int>,
        cuerpo: Float,
    ): List<Situado> {
        val grupos = mutableListOf<MutableList<LineaLeida>>()
        var anterior: Pair<Int, Int>? = null

        lineas.forEachIndexed { indice, linea ->
            if (indice in consumidas) {
                anterior = null
                return@forEachIndexed
            }
            val clave = linea.bloque to nivelDeTitulo(linea.tamano, cuerpo)
            if (clave == anterior) grupos.last() += linea else grupos += mutableListOf(linea)
            anterior = clave
        }

        return grupos.mapNotNull { grupo -> situadoDe(grupo, cuerpo) }
    }

    private fun situadoDe(
        grupo: List<LineaLeida>,
        cuerpo: Float,
    ): Situado? {
        val texto = grupo.joinToString(" ") { it.texto.trim() }.trim()
        if (texto.isEmpty()) return null
        val nivel = nivelDeTitulo(grupo.maxOf { it.tamano }, cuerpo)
        val bloque =
            if (nivel > 0) BloqueDeTexto.Titulo(texto, nivel) else BloqueDeTexto.Parrafo(texto)
        return Situado(grupo.minOf { it.y0 }, bloque)
    }

    /** Un decimal al redondear tamaños: dos fuentes a 11,02 y 11,04 son la misma letra. */
    private const val DECIMAL = 10f

    /** Si no se ha leído ni una letra, once puntos, que es el cuerpo de casi todo. */
    private const val TAMANO_HABITUAL = 11f

    private const val RAZON_PRIMER_NIVEL = 1.8f
    private const val RAZON_SEGUNDO_NIVEL = 1.45f
    private const val RAZON_TERCER_NIVEL = 1.25f

    /** Fracción del cuerpo a partir de la cual un hueco entre palabras separa columnas. */
    private const val FACTOR_DE_COLUMNA = 0.9f

    /** Cuánto puede bailar el borde izquierdo de una columna entre una fila y otra. */
    private const val FACTOR_DE_TOLERANCIA = 1.5f

    /** Cuánto blanco cabe entre dos filas antes de que dejen de ser la misma tabla. */
    private const val FACTOR_DE_SALTO = 2.5f

    /** Cuánto se tienen que pisar dos líneas para ser la misma fila. */
    private const val FRACCION_DE_SOLAPE = 0.5f

    private const val COLUMNAS_MINIMAS = 2

    /** Con una sola fila no hay tabla que valga: hay una línea con un hueco en medio. */
    private const val FILAS_MINIMAS = 2
}
