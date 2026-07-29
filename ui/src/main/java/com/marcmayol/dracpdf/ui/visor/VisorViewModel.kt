package com.marcmayol.dracpdf.ui.visor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
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
    val modo: ModoVisor = ModoVisor.Lectura,
    /** El formulario del documento; `null` mientras no se ha averiguado. */
    val formulario: Formulario? = null,
    val campoActivo: IdCampo? = null,
    /** El aviso pendiente sobre el formulario, si hay alguno que dar. */
    val aviso: AvisoFormulario? = null,
) {
    /** Si hay formulario que rellenar, que es lo que habilita el modo. */
    val hayFormulario: Boolean get() = formulario?.esRellenable == true
}

/**
 * Lo que hay que advertir sobre el formulario de este documento, si hay algo.
 *
 * Los dos avisos existen porque XFA no es una cosa sola. Uno cierra la puerta y el
 * otro sólo la entorna, y confundirlos deja al usuario o rellenando lo que no se
 * guardará, o sin rellenar lo que sí podía.
 */
enum class AvisoFormulario {
    /**
     * XFA puro: no hay AcroForm debajo, así que aquí no se rellena. Se dice y se
     * deja leer el documento, que es lo único que se puede ofrecer de verdad.
     */
    NO_SE_PUEDE_RELLENAR,

    /**
     * XFA híbrido: se rellena la capa AcroForm, que es la que entienden todos los
     * visores. El matiz que hay que dar es que Adobe puede dibujar la capa XFA por
     * encima y enseñar los valores viejos justo ahí. No es un fallo del relleno; es
     * que ese PDF lleva dos verdades dentro desde que lo emitieron.
     */
    PUEDE_VERSE_DISTINTO_EN_ADOBE,
}

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
    private val listarCampos: ListarCampos,
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

    private val _campos = MutableStateFlow<Map<Int, List<CampoFormulario>>>(emptyMap())

    /**
     * Los campos de las páginas que están a la vista, por número de página.
     *
     * Se cargan y se sueltan con la misma disciplina que los píxeles: el overlay de
     * una página nace cuando la página entra en la ventana y muere cuando sale. No
     * hay nada que custodiar al soltarlo, porque el valor de un campo vive en el
     * documento y no aquí; esto es una vista, no una copia.
     */
    val campos: StateFlow<Map<Int, List<CampoFormulario>>> = _campos.asStateFlow()

    private val trabajos = ConcurrentHashMap<ClavePagina, Job>()
    private val trabajosMiniatura = ConcurrentHashMap<Int, Job>()
    private val trabajosCampos = ConcurrentHashMap<Int, Job>()
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
            val formulario = withContext(dispatcherRender) { listarCampos.formulario(id) }
            _estado.value = _estado.value.copy(formulario = formulario, aviso = avisoDe(formulario))
        }
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

        if (estado.hayFormulario) sincronizarCampos(id, ventana, vivas)

        if (primeraVisible in 0 until estado.paginas && primeraVisible != estado.paginaActual) {
            _estado.value = estado.copy(paginaActual = primeraVisible)
            registro.anotarPagina(id, primeraVisible)
        }
    }

    /**
     * Trae los campos de las páginas visibles y suelta los de las que se fueron.
     *
     * Soltar es la mitad del trabajo: en un formulario de cien páginas, quedarse los
     * campos de todas las que han pasado por pantalla es acumular sin límite algo que
     * se vuelve a pedir en un milisegundo.
     */
    private fun sincronizarCampos(
        id: IdDocumento,
        ventana: IntRange,
        vivas: List<Int>,
    ) {
        trabajosCampos.keys
            .filter { it !in ventana }
            .forEach { pagina -> trabajosCampos.remove(pagina)?.cancel() }

        val sobran = _campos.value.keys.filter { it !in ventana }
        if (sobran.isNotEmpty()) _campos.value = _campos.value - sobran.toSet()

        vivas
            .filter { it !in _campos.value && !trabajosCampos.containsKey(it) }
            .forEach { pagina ->
                trabajosCampos[pagina] =
                    viewModelScope.launch {
                        val delaPagina = withContext(dispatcherRender) { listarCampos(id, pagina) }
                        _campos.value = _campos.value + (pagina to delaPagina)
                        trabajosCampos.remove(pagina)
                    }
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

    /**
     * Entra en el modo de formulario, si hay formulario. Un documento sin campos —o
     * con un XFA que no se puede rellenar— no entra: no habría nada que hacer dentro.
     */
    fun entrarEnFormulario() {
        val estado = _estado.value
        val id = estado.id ?: return
        if (!estado.hayFormulario) return
        _estado.value = estado.copy(modo = ModoVisor.Formulario, chromeVisible = true)
        // Al entrar hay que tener ya los campos de lo que se está viendo, aunque el
        // documento se abriera en modo lectura y nadie los hubiera pedido.
        val ventana = (estado.paginaActual - PRECARGA)..(estado.paginaActual + PRECARGA)
        sincronizarCampos(id, ventana, ventana.filter { it in 0 until estado.paginas })
    }

    /** Sale del modo y suelta el campo activo con él: fuera del modo no hay campo. */
    fun salirDelFormulario() {
        _estado.value = _estado.value.copy(modo = ModoVisor.Lectura, campoActivo = null)
    }

    fun activarCampo(id: IdCampo?) {
        if (_estado.value.modo != ModoVisor.Formulario) return
        _estado.value = _estado.value.copy(campoActivo = id)
    }

    /** El usuario ha leído el aviso. No se repite mientras el documento siga abierto. */
    fun descartarAviso() {
        _estado.value = _estado.value.copy(aviso = null)
    }

    private fun avisoDe(formulario: Formulario): AvisoFormulario? =
        when (formulario.tipo) {
            TipoFormulario.XFA_PURO -> AvisoFormulario.NO_SE_PUEDE_RELLENAR
            TipoFormulario.XFA_HIBRIDO -> AvisoFormulario.PUEDE_VERSE_DISTINTO_EN_ADOBE
            TipoFormulario.ACROFORM, TipoFormulario.NINGUNO -> null
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
        trabajosCampos.values.forEach(Job::cancel)
        trabajosCampos.clear()
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
