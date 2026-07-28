package com.marcmayol.dracpdf.ui.visor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Lo que el visor sabe del documento que está enseñando. */
data class EstadoVisor(
    val id: IdDocumento? = null,
    val nombre: String = "",
    val paginas: Int = 0,
    val paginaActual: Int = 0,
    val zoom: Float = 1f,
    val chromeVisible: Boolean = true,
    val tamanoEstimado: TamanoPt? = null,
)

/**
 * El visor.
 *
 * Dos ideas gobiernan esto y las dos vienen del criterio de la fase: **no se
 * rasteriza lo que no se ve** y **el tamaño de una página se sabe sin dibujarla**.
 * La segunda es la que permite cumplir la primera sin que el scroll pegue saltos: la
 * lista reserva el hueco con la proporción correcta y el bitmap llega después.
 */
class VisorViewModel(
    private val renderizar: RenderizarPagina,
    private val registro: RegistroDocumentos,
    private val cache: CachePaginas,
    private val cacheMiniaturas: CachePaginas = CachePaginas(PRESUPUESTO_MINIATURAS),
    private val dispatcherRender: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoVisor())
    val estado: StateFlow<EstadoVisor> = _estado.asStateFlow()

    private val _paginas = MutableStateFlow<Map<ClavePagina, ImageBitmap>>(emptyMap())

    /** Las páginas ya dibujadas y disponibles para pintar. */
    val paginas: StateFlow<Map<ClavePagina, ImageBitmap>> = _paginas.asStateFlow()

    private val _miniaturas = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())

    /** Las miniaturas ya dibujadas, por número de página. */
    val miniaturas: StateFlow<Map<Int, ImageBitmap>> = _miniaturas.asStateFlow()

    private val trabajos = ConcurrentHashMap<ClavePagina, Job>()
    private val trabajosMiniatura = ConcurrentHashMap<Int, Job>()
    private val tamanosReales = ConcurrentHashMap<Int, TamanoPt>()

    fun mostrar(id: IdDocumento) {
        val estadoDocumento = registro.estado(id)
        _estado.value =
            EstadoVisor(
                id = id,
                nombre = estadoDocumento.documento.nombre,
                paginas = estadoDocumento.documento.paginas,
                paginaActual = estadoDocumento.paginaActual,
                zoom = estadoDocumento.zoom,
            )
        viewModelScope.launch {
            // El tamaño de la primera página sirve de estimación para todas: casi
            // ningún documento mezcla formatos, y pedir las 500 al abrir costaría el
            // presupuesto entero de los dos segundos. Cada página corrige su hueco en
            // cuanto se conoce su tamaño real.
            val primera = withContext(dispatcherRender) { renderizar.tamano(id, 0) }
            tamanosReales[0] = primera
            _estado.value = _estado.value.copy(tamanoEstimado = primera)
        }
    }

    /** El tamaño que la lista debe reservar para una página. */
    fun tamanoDe(pagina: Int): TamanoPt? = tamanosReales[pagina] ?: _estado.value.tamanoEstimado

    /**
     * Pide el render de las páginas visibles y de una a cada lado, y cancela lo que
     * haya en vuelo fuera de esa ventana.
     *
     * La cancelación no es un detalle: en un documento de 500 páginas, un dedo rápido
     * encola cien renders que ya no le importan a nadie, y cada uno ocupa el hilo del
     * documento que la página que sí se está mirando necesita.
     */
    fun alCambiarVentana(
        primeraVisible: Int,
        ultimaVisible: Int,
    ) {
        val estado = _estado.value
        val id = estado.id ?: return
        val ventana = (primeraVisible - PRECARGA)..(ultimaVisible + PRECARGA)
        val vivas = ventana.filter { it in 0 until estado.paginas }

        trabajos.keys
            .filter { it.pagina !in ventana }
            .forEach { clave -> trabajos.remove(clave)?.cancel() }

        vivas.forEach { pagina -> pedir(id, pagina, estado.zoom) }

        if (primeraVisible in 0 until estado.paginas && primeraVisible != estado.paginaActual) {
            _estado.value = estado.copy(paginaActual = primeraVisible)
            registro.anotarPagina(id, primeraVisible)
        }
    }

    private fun pedir(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ) {
        val clave = ClavePagina(pagina, CachePaginas.cuantizar(escala))

        cache[clave]?.let { yaEstaba ->
            if (_paginas.value[clave] == null) _paginas.value = _paginas.value + (clave to yaEstaba)
            return
        }
        if (trabajos.containsKey(clave)) return

        val trabajo =
            viewModelScope.launch {
                val mapa =
                    withContext(dispatcherRender) {
                        renderizar(id, pagina, CachePaginas.escalaDe(clave.escalaCuantizada)).let {
                            tamanosReales.putIfAbsent(pagina, renderizar.tamano(id, pagina))
                            it.aImageBitmap()
                        }
                    }
                cache.guardar(clave, mapa)
                _paginas.value = _paginas.value + (clave to mapa)
                trabajos.remove(clave)
            }
        trabajos[clave] = trabajo
    }

    /**
     * Pide la miniatura de una página, si no la tiene ya.
     *
     * Las miniaturas van a una escala fija y minúscula y **a su propia caché**: no
     * pueden competir por el presupuesto con las páginas grandes, porque quinientas
     * miniaturas echarían de la caché justo la página que se está mirando.
     */
    fun pedirMiniatura(pagina: Int) {
        val estado = _estado.value
        val id = estado.id ?: return
        if (pagina !in 0 until estado.paginas) return
        if (_miniaturas.value.containsKey(pagina) || trabajosMiniatura.containsKey(pagina)) return

        val clave = ClavePagina(pagina, CachePaginas.cuantizar(ESCALA_MINIATURA))
        val enCache = cacheMiniaturas[clave]

        if (enCache != null) {
            publicar(pagina, enCache)
        } else {
            trabajosMiniatura[pagina] =
                viewModelScope.launch {
                    val mapa =
                        withContext(dispatcherRender) {
                            renderizar(id, pagina, ESCALA_MINIATURA).aImageBitmap()
                        }
                    cacheMiniaturas.guardar(clave, mapa)
                    publicar(pagina, mapa)
                    trabajosMiniatura.remove(pagina)
                }
        }
    }

    private fun publicar(
        pagina: Int,
        miniatura: ImageBitmap,
    ) {
        _miniaturas.value = _miniaturas.value + (pagina to miniatura)
    }

    /** Fija el zoom al soltar el pellizco, y con él la escala a la que se rasteriza. */
    fun fijarZoom(zoom: Float) {
        val estado = _estado.value
        val id = estado.id ?: return
        val acotado = zoom.coerceIn(ZOOM_MINIMO, ZOOM_MAXIMO)
        if (CachePaginas.cuantizar(acotado) == CachePaginas.cuantizar(estado.zoom)) return

        _estado.value = estado.copy(zoom = acotado)
        registro.anotarZoom(id, acotado)
    }

    fun alternarChrome() {
        _estado.value = _estado.value.copy(chromeVisible = !_estado.value.chromeVisible)
    }

    fun irAPagina(pagina: Int) {
        val estado = _estado.value
        val id = estado.id ?: return
        require(pagina in 0 until estado.paginas) { "La página $pagina no existe" }
        _estado.value = estado.copy(paginaActual = pagina)
        registro.anotarPagina(id, pagina)
    }

    /** El sistema anda justo de memoria: se suelta todo lo que no se está viendo. */
    fun alApretarLaMemoria() {
        cache.vaciar()
        cacheMiniaturas.vaciar()
        _paginas.value = emptyMap()
        _miniaturas.value = emptyMap()
    }

    override fun onCleared() {
        trabajos.values.forEach(Job::cancel)
        trabajos.clear()
        trabajosMiniatura.values.forEach(Job::cancel)
        trabajosMiniatura.clear()
        super.onCleared()
    }

    companion object {
        /** Páginas que se rasterizan a cada lado de lo visible. */
        const val PRECARGA = 1

        const val ZOOM_MINIMO = 0.25f
        const val ZOOM_MAXIMO = 6f

        /**
         * Escala de las miniaturas: una A4 queda en unos 150 × 210 px, que a 120 dp de
         * ancho se ve nítida en cualquier densidad razonable.
         */
        const val ESCALA_MINIATURA = 0.25f

        /**
         * Ocho megas para las miniaturas, aparte del presupuesto de las páginas. Con
         * eso caben unas sesenta a la vez, y la LRU va soltando las que se alejan.
         */
        const val PRESUPUESTO_MINIATURAS = 8 * 1024 * 1024
    }
}
