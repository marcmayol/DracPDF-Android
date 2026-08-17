package com.marcmayol.dracpdf.ui.visor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * La aritmética de la maqueta del visor: cuánto mide una página, dónde cae y en qué
 * punto del papel ha aterrizado un dedo.
 *
 * Vive aparte de la pantalla porque no dibuja nada y porque es donde se equivoca uno:
 * el ajuste, la doble página y el giro se cruzan aquí en cuatro cuentas, y tenerlas
 * sueltas entre composables era la manera de que la de ida y la de vuelta dejaran de
 * cuadrar sin que nadie lo notara. Aquí se leen juntas, y se pueden probar sin pintar.
 */

internal const val PROPORCION_A4 = 842f / 595f

/** El aire de arriba y de abajo de la lista, que también resta al encajar la página. */
internal val PADDING_VERTICAL = 14.dp

/**
 * Cuánto mide de ancho una página **antes** del zoom, según el ajuste elegido.
 *
 * Es el único sitio donde el modo de vista se convierte en píxeles, y de ahí sale todo
 * lo demás. A página completa manda el lado que primero se queda sin sitio: en un móvil
 * de pie es siempre el alto, y por eso «página completa» encoge tanto —es lo que
 * significa que la página entera quepa—.
 */
internal fun anchoDeLaPagina(
    disponible: Dp,
    altoDisponible: Dp,
    proporcion: Float,
    vista: VistaDelVisor,
    porFila: Int,
): Dp {
    val paraCadaUna = (disponible - MedidasLadon.hueco * (porFila - 1)) / porFila
    if (vista.ajuste == AjusteDeVista.ANCHO) return paraCadaUna

    // Girada, la página es tan alta como ancha era: la proporción que hay que encajar
    // en la pantalla es la de lo que se ve, no la del papel.
    val proporcionVisible = if (vista.giro.intercambiaLados) 1f / proporcion else proporcion
    return minOf(paraCadaUna, (altoDisponible - PADDING_VERTICAL * 2) / proporcionVisible)
}

/**
 * Lo que mide una página en la pantalla, antes y después de girarla.
 *
 * [ancho] y [alto] son los del papel —los que usan los overlays, que hablan en puntos
 * de página y no saben nada del giro—, y los «visibles» son los del hueco que ocupa en
 * la pantalla. Con la vista de lado, uno es el otro.
 */
internal data class CajaDePagina(
    val ancho: Dp,
    val alto: Dp,
    val girada: Boolean,
) {
    val anchoVisible: Dp get() = if (girada) alto else ancho
    val altoVisible: Dp get() = if (girada) ancho else alto
}

internal fun cajaDePagina(
    anchoEnPantalla: Dp,
    proporcion: Float,
    giro: GiroDeVista,
): CajaDePagina =
    if (giro.intercambiaLados) {
        CajaDePagina(ancho = anchoEnPantalla / proporcion, alto = anchoEnPantalla, girada = true)
    } else {
        CajaDePagina(ancho = anchoEnPantalla, alto = anchoEnPantalla * proporcion, girada = false)
    }

/**
 * Lo que hace falta para traducir un toque a un punto del documento.
 *
 * Va en un bulto porque los gestos lo necesitan entero, y porque con la doble página y
 * el giro puestos ya no basta con mirar el ancho del viewport: hay dos columnas, un
 * hueco que no es de nadie, y un sistema de coordenadas que puede estar de lado.
 */
internal data class MaquetaDeLaLista(
    /** Ancho en píxeles del hueco que ocupa **una** página. */
    val anchoPagina: Float,
    val hueco: Float,
    /** Lo que sobra a la izquierda cuando la fila es más estrecha que la pantalla. */
    val margenIzquierdo: Float,
    val porFila: Int,
    val giro: GiroDeVista,
    val paginas: Int,
)

/**
 * En qué página y en qué punto de ella ha caído un toque.
 *
 * Es la traducción inversa de la que hace el dibujo: la pantalla habla en píxeles
 * desplazados y el documento en puntos de página. Vive aquí, en un solo sitio, porque
 * la necesitan tres gestos —el toque, la pulsación larga y las asas— y tres copias de
 * esta aritmética serían tres sitios donde equivocarse con el scroll.
 *
 * Devuelve `null` si el toque cae en un hueco entre páginas o si todavía no se sabe
 * cuánto mide la página tocada.
 */
internal fun paginaYPunto(
    donde: Offset,
    lista: LazyListState,
    scrollHorizontal: ScrollState,
    tamanoDe: (Int) -> TamanoPt?,
    maqueta: MaquetaDeLaLista,
): Pair<Int, PuntoPt>? {
    val item =
        lista.layoutInfo.visibleItemsInfo
            .firstOrNull { donde.y >= it.offset && donde.y < it.offset + it.size } ?: return null
    if (item.size <= 0 || maqueta.anchoPagina <= 0f) return null

    // La x lleva el desplazamiento horizontal sumado y el margen restado: con zoom, lo
    // que se ve es una ventana sobre una fila más ancha que la pantalla; a página
    // completa, al revés, la fila va centrada y sobra sitio a los lados.
    val enLaFila = donde.x + scrollHorizontal.value - maqueta.margenIzquierdo
    val (pagina, dentro) = paginaYSuX(enLaFila, item.index, maqueta) ?: return null
    val tamano = tamanoDe(pagina) ?: return null

    return pagina to
        maqueta.giro.puntoEnLaPagina(
            fraccionX = dentro / maqueta.anchoPagina,
            fraccionY = (donde.y - item.offset) / item.size,
            tamano = tamano,
        )
}

/** Qué página de la fila toca, y a qué distancia de su borde izquierdo ha caído el dedo. */
internal fun paginaYSuX(
    enLaFila: Float,
    fila: Int,
    maqueta: MaquetaDeLaLista,
): Pair<Int, Float>? {
    val paso = maqueta.anchoPagina + maqueta.hueco
    val columna = (enLaFila / paso).toInt().coerceIn(0, maqueta.porFila - 1)
    val dentro = enLaFila - columna * paso
    // Entre las dos páginas hay un hueco que no es de ninguna de ellas.
    if (dentro < 0f || dentro > maqueta.anchoPagina) return null

    val pagina = fila * maqueta.porFila + columna
    return if (pagina < maqueta.paginas) pagina to dentro else null
}

/**
 * Cuánto hay que desplazar la vista para que el punto que se está ampliando se quede
 * donde estaba.
 *
 * Sin esto el zoom crece desde la esquina de arriba a la izquierda y lo que se estaba
 * mirando se escapa de la pantalla, que es el defecto clásico del zoom en un visor.
 * Se calcula **antes** de que la página se vuelva a medir, con la posición que el
 * contenido tiene todavía.
 */
internal fun anclaje(
    centro: Offset,
    factorReal: Float,
    lista: LazyListState,
    scrollHorizontal: ScrollState,
): Offset {
    if (!factorReal.isFinite() || factorReal == 1f) return Offset.Zero

    val crecimiento = factorReal - 1f
    val visibles = lista.layoutInfo.visibleItemsInfo
    // La página bajo el dedo, y si el dedo cae en un hueco, la primera visible: el
    // desplazamiento se mide desde el borde de esa página, no desde el principio del
    // documento, que con 500 páginas ni se conoce ni haría falta.
    val pagina =
        visibles.firstOrNull { centro.y >= it.offset && centro.y < it.offset + it.size }
            ?: visibles.firstOrNull()

    return Offset(
        x = (scrollHorizontal.value + centro.x) * crecimiento,
        y = if (pagina == null) 0f else (centro.y - pagina.offset) * crecimiento,
    )
}

/**
 * Cuánto mide de alto la página en píxeles, para saber dónde cae un campo dentro de
 * ella. Es una estimación: basta para dejar el campo a la vista, y quien afina el
 * resto es el propio scroll.
 */
internal fun altoDePaginaDe(
    altoPantallaDp: Int,
    estado: EstadoVisor,
    zoom: Float,
): Float {
    val proporcion = estado.tamanoEstimado?.let { it.alto / it.ancho } ?: PROPORCION_A4
    return altoPantallaDp * proporcion * zoom
}
