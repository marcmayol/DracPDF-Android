package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.DefaultColorSpaces
import com.artifex.mupdf.fitz.Device
import com.artifex.mupdf.fitz.Image
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.Path
import com.artifex.mupdf.fitz.Rect
import com.artifex.mupdf.fitz.Shade
import com.artifex.mupdf.fitz.StrokeState
import com.artifex.mupdf.fitz.Text
import com.marcmayol.dracpdf.dominio.modelo.RectPt

/**
 * Un dispositivo de dibujo que **no dibuja**: sólo apunta dónde queda cada imagen.
 *
 * Es la única manera honesta de saber qué imágenes hay en una página y en qué
 * rectángulo exacto caen. Un PDF no tiene una lista de imágenes con sus posiciones:
 * tiene un programa de dibujo, y las imágenes son órdenes de ese programa con la
 * matriz que las coloca. Aquí se «ejecuta» la página contra este dispositivo y se
 * anota cada orden de imagen que pasa.
 *
 * La matriz transforma el **cuadrado unidad**: toda imagen se dibuja como si midiera
 * 1×1 y fuera la matriz quien la estira y la coloca. De ahí sale el contorno exacto
 * que la interfaz necesita para dejar tocarla.
 *
 * El resto de métodos están vacíos a propósito y son muchos porque el motor exige
 * implementarlos todos; ignorar el texto, los trazos y los degradados es justo lo que
 * este dispositivo tiene que hacer.
 */
@Suppress("TooManyFunctions", "EmptyFunctionBlock")
internal class DispositivoDeImagenes : Device() {
    private val encontradas = mutableListOf<RectPt>()

    /** Los contornos de las imágenes, en el orden en que la página las dibuja. */
    val imagenes: List<RectPt> get() = encontradas.toList()

    override fun fillImage(
        imagen: Image?,
        matriz: Matrix?,
        alfa: Float,
        parametros: Int,
    ) {
        anotar(matriz)
    }

    override fun fillImageMask(
        imagen: Image?,
        matriz: Matrix?,
        espacio: ColorSpace?,
        color: FloatArray?,
        alfa: Float,
        parametros: Int,
    ) {
        anotar(matriz)
    }

    /**
     * El contorno que resulta de aplicar la matriz al cuadrado unidad.
     *
     * Se calculan las cuatro esquinas y se toman los extremos porque la imagen puede
     * venir girada o espejada —una matriz con el signo cambiado es lo normal en PDF,
     * donde el eje vertical va al revés— y entonces «la esquina de arriba» no es la
     * que sale de multiplicar (0,0).
     */
    private fun anotar(matriz: Matrix?) {
        val m = matriz ?: return
        val xs = listOf(m.e, m.a + m.e, m.c + m.e, m.a + m.c + m.e)
        val ys = listOf(m.f, m.b + m.f, m.d + m.f, m.b + m.d + m.f)
        val contorno = RectPt(xs.min(), ys.min(), xs.max(), ys.max())
        if (contorno.ancho > 0f && contorno.alto > 0f) encontradas += contorno
    }

    override fun close() {}

    override fun fillPath(
        camino: Path?,
        parImpar: Boolean,
        matriz: Matrix?,
        espacio: ColorSpace?,
        color: FloatArray?,
        alfa: Float,
        parametros: Int,
    ) {}

    override fun strokePath(
        camino: Path?,
        trazo: StrokeState?,
        matriz: Matrix?,
        espacio: ColorSpace?,
        color: FloatArray?,
        alfa: Float,
        parametros: Int,
    ) {}

    override fun clipPath(
        camino: Path?,
        parImpar: Boolean,
        matriz: Matrix?,
    ) {}

    override fun clipStrokePath(
        camino: Path?,
        trazo: StrokeState?,
        matriz: Matrix?,
    ) {}

    override fun fillText(
        texto: Text?,
        matriz: Matrix?,
        espacio: ColorSpace?,
        color: FloatArray?,
        alfa: Float,
        parametros: Int,
    ) {}

    override fun strokeText(
        texto: Text?,
        trazo: StrokeState?,
        matriz: Matrix?,
        espacio: ColorSpace?,
        color: FloatArray?,
        alfa: Float,
        parametros: Int,
    ) {}

    override fun clipText(
        texto: Text?,
        matriz: Matrix?,
    ) {}

    override fun clipStrokeText(
        texto: Text?,
        trazo: StrokeState?,
        matriz: Matrix?,
    ) {}

    override fun ignoreText(
        texto: Text?,
        matriz: Matrix?,
    ) {}

    override fun fillShade(
        degradado: Shade?,
        matriz: Matrix?,
        alfa: Float,
        parametros: Int,
    ) {}

    override fun clipImageMask(
        imagen: Image?,
        matriz: Matrix?,
    ) {}

    override fun popClip() {}

    override fun beginMask(
        area: Rect?,
        luminosidad: Boolean,
        espacio: ColorSpace?,
        color: FloatArray?,
        parametros: Int,
    ) {}

    override fun endMask() {}

    override fun beginGroup(
        area: Rect?,
        espacio: ColorSpace?,
        aislado: Boolean,
        golpeando: Boolean,
        modo: Int,
        alfa: Float,
    ) {}

    override fun endGroup() {}

    override fun beginTile(
        area: Rect?,
        vista: Rect?,
        pasoX: Float,
        pasoY: Float,
        matriz: Matrix?,
        identificador: Int,
        documento: Int,
    ): Int = 0

    override fun endTile() {}

    override fun renderFlags(
        activar: Int,
        desactivar: Int,
    ) {}

    override fun setDefaultColorSpaces(espacios: DefaultColorSpaces?) {}

    override fun beginLayer(nombre: String?) {}

    override fun endLayer() {}

    override fun beginStructure(
        estructura: Int,
        etiqueta: String?,
        indice: Int,
    ) {}

    override fun endStructure() {}

    override fun beginMetatext(
        tipo: Int,
        texto: String?,
    ) {}

    override fun endMetatext() {}
}
