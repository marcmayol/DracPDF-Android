package com.marcmayol.dracpdf.ui.visor

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.adaptadores.ajustes.AjustesDeInterfaz
import com.marcmayol.dracpdf.dominio.casos.BuscarEnDocumento
import com.marcmayol.dracpdf.dominio.casos.EditarContenido
import com.marcmayol.dracpdf.dominio.casos.EstamparFirma
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.MarcarDocumento
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.Anotacion
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.Coincidencia
import com.marcmayol.dracpdf.dominio.modelo.ColorAnotacion
import com.marcmayol.dracpdf.dominio.modelo.DestinoEnlace
import com.marcmayol.dracpdf.dominio.modelo.EnlacePagina
import com.marcmayol.dracpdf.dominio.modelo.EntradaIndice
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.modelo.Formulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.PropiedadesDocumento
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.RectPt
import com.marcmayol.dracpdf.dominio.modelo.SeleccionTexto
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.modelo.TipoAnotacion
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import com.marcmayol.dracpdf.dominio.modelo.unionCon
import com.marcmayol.dracpdf.dominio.puertos.EstructuraPdf
import com.marcmayol.dracpdf.dominio.puertos.TextoPdf
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Si hay algo escrito que todavía no está en el fichero. */
    val cambiosSinGuardar: Boolean = false,
    /**
     * Si el documento lleva firma. Lo miran las herramientas: las que reescriben el
     * PDF se apagan, porque el resultado saldría con las firmas rotas.
     */
    val firmado: Boolean = false,
    val guardando: Boolean = false,
    /** Lo último que salió mal, para decirlo y no tragárselo. */
    val error: String? = null,
    /** Las páginas que traen campos, según se van conociendo. */
    val paginasConCampos: List<Int> = emptyList(),
    /** Si ya se ha mirado el documento entero: hasta entonces la lista es parcial. */
    val indiceCompleto: Boolean = false,
    /**
     * Si hay campo al que ir hacia atrás o hacia delante.
     *
     * Se calculan y no se suponen: un botón encendido que al pulsarlo no hace nada
     * es peor que uno apagado, porque el usuario cree que el formulario se ha
     * quedado colgado.
     */
    val hayCampoAnterior: Boolean = false,
    val hayCampoSiguiente: Boolean = false,
    /** El aviso pendiente sobre el formulario, si hay alguno que dar. */
    val aviso: AvisoFormulario? = null,
    /** La firma que se está colocando, si hay alguna en el aire. */
    val colocacion: ColocacionFirma? = null,
    /** La búsqueda abierta, si la hay. `null` es «no se está buscando». */
    val busqueda: Busqueda? = null,
    /** Lo que hay seleccionado con el dedo, si hay algo. */
    val seleccion: SeleccionEnCurso? = null,
    /** El índice del documento, cuando ya se ha pedido. */
    val indice: List<EntradaIndice> = emptyList(),
    /** El enlace externo que espera un sí o un no. */
    val enlacePreguntado: String? = null,
    /** La ficha del documento, cuando se ha pedido verla. */
    val propiedades: PropiedadesDocumento? = null,
    /** Si queda algún cambio del formulario por deshacer. */
    val hayQueDeshacer: Boolean = false,
    /** Las marcas de la página actual, cuando se han pedido. */
    val anotaciones: List<Anotacion> = emptyList(),
    /**
     * Cómo se está enseñando el documento. Va en el estado del visor y no en uno
     * propio porque cambia lo mismo que cambia el zoom —cuánto mide una página en
     * pantalla— y tenerlos separados obligaría a leer dos flujos para dibujar una fila.
     */
    val vista: VistaDelVisor = VistaDelVisor(),
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
 * Lo que el visor sabe hacer con un documento, en un solo bulto.
 *
 * Van juntos porque siempre viajan juntos: cada uno por su lado convertía el
 * constructor en una lista de la compra donde el orden de los argumentos era el único
 * que sabía qué es cada cosa.
 */
@Suppress("LongParameterList")
class CasosDelVisor(
    val renderizar: RenderizarPagina,
    val listarCampos: ListarCampos,
    val rellenar: RellenarCampo,
    val guardar: GuardarDocumento,
    val estamparFirma: EstamparFirma,
    val buscar: BuscarEnDocumento,
    val texto: TextoPdf,
    val estructura: EstructuraPdf,
    /** Marcar el documento: lo de la Fase 8. */
    val marcar: MarcarDocumento,
    /** Y editar su contenido: imágenes y corrección de texto. */
    val editar: EditarContenido,
)

/**
 * Una búsqueda en marcha o terminada.
 *
 * Las coincidencias se van añadiendo mientras se recorre el documento, así que esto
 * cambia varias veces por búsqueda: el contador sube, y con él la sensación de que
 * algo está pasando. Un documento de quinientas páginas tarda, y una barra quieta
 * parece rota.
 */
data class Busqueda(
    val termino: String = "",
    val coincidencias: List<Coincidencia> = emptyList(),
    /** Cuál de todas está enseñada ahora mismo, o -1 si aún no hay ninguna. */
    val activa: Int = -1,
    val buscando: Boolean = false,
    /** Si ya se recorrió el documento entero: hasta entonces «0 de 3» es provisional. */
    val completa: Boolean = false,
) {
    val hayResultados: Boolean get() = coincidencias.isNotEmpty()

    /** «3 de 12», o «Sin resultados» cuando ya se ha mirado todo. */
    val contador: String
        get() =
            when {
                hayResultados -> "${activa + 1} de ${coincidencias.size}"
                buscando -> "Buscando…"
                termino.isBlank() -> ""
                completa -> "Sin resultados"
                else -> ""
            }

    /** Las de una página, para pintarlas encima de ella. */
    fun enLaPagina(pagina: Int): List<Coincidencia> = coincidencias.filter { it.pagina == pagina }
}

/** Cómo estaba un campo antes del último cambio, que es a donde vuelve «deshacer». */
data class CambioDeCampo(
    val campo: IdCampo,
    val valor: String,
    val tipo: TipoCampo,
)

/**
 * Lo que hay seleccionado con el dedo, y en qué página.
 *
 * Los dos puntos se guardan porque son los que mueven las asas: la selección se
 * recalcula pidiéndosela al motor cada vez que uno de ellos cambia, en vez de
 * intentar estirar los rectángulos que ya había.
 */
data class SeleccionEnCurso(
    val pagina: Int,
    val desde: PuntoPt,
    val hasta: PuntoPt,
    val seleccion: SeleccionTexto,
)

/**
 * Una firma que se está colocando, antes de estamparla.
 *
 * Vive aquí y no en el documento a propósito: mientras se arrastra no se ha tocado
 * nada, y cancelar tiene que dejar el PDF exactamente como estaba. El marco va en
 * puntos de página, que es el sistema en el que se estampará.
 */
data class ColocacionFirma(
    val firma: Firma,
    val pagina: Int,
    val marco: RectPt,
)

/** Hacia dónde se mueve el foco entre campos. */
enum class Direccion(
    val paso: Int,
) {
    ANTERIOR(-1),
    SIGUIENTE(1),
}

/**
 * Traer a la vista un campo concreto.
 *
 * La posición va como fracción de la altura de la página y no en píxeles: quien
 * sabe cuántos píxeles mide la página a este zoom es la lista, no el modelo.
 */
data class DesplazamientoACampo(
    val pagina: Int,
    val fraccionY: Float,
)

/**
 * El visor.
 *
 * Dos ideas gobiernan esto y las dos vienen del criterio de la Fase 1: **no se
 * rasteriza lo que no se ve** y **el tamaño de una página se sabe sin dibujarla**.
 * La segunda es la que permite cumplir la primera sin que el scroll pegue saltos: la
 * lista reserva el hueco con la proporción correcta y el bitmap llega después.
 *
 * Concentra bastantes acciones, y es a conciencia: el diseño manda que sea el modo
 * quien decida la pantalla, así que los modos comparten documento, caché y estado de
 * página. Repartirlos en varios modelos obligaría a mantenerlos sincronizados entre
 * sí, que es peor problema que una clase con muchas funciones.
 *
 * Y por lo mismo es grande: el argumento de arriba vale igual para las líneas que para
 * las funciones, así que la clase se salta las dos medidas a conciencia y no por
 * descuido.
 */
@Suppress("TooManyFunctions", "LargeClass")
class VisorViewModel(
    private val casos: CasosDelVisor,
    private val registro: RegistroDocumentos,
    private val cache: CachePaginas,
    private val cacheMiniaturas: CachePaginas = CachePaginas(PRESUPUESTO_MINIATURAS),
    private val dispatcherRender: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Dónde se recuerda la vista elegida. Puede no haberlo —los tests que sólo miran
     * cómo se dibuja una página no necesitan disco—, y entonces el visor funciona
     * igual: lo único que se pierde es que la elección siga puesta mañana.
     */
    private val ajustes: AjustesDeInterfaz? = null,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoVisor())
    val estado: StateFlow<EstadoVisor> = _estado.asStateFlow()

    init {
        // La vista guardada se recoge sin bloquear el arranque; ver el porqué en
        // `AjustesDeInterfaz.vista`. Llegue antes o después de abrir el documento, no
        // se pierde: `mostrar` conserva la vista que hubiera.
        ajustes?.let { almacen ->
            viewModelScope.launch {
                _estado.value = _estado.value.copy(vista = VistaDelVisor.de(almacen.vista.first()))
            }
        }
    }

    private val _paginas = MutableStateFlow<Map<ClavePagina, ImageBitmap>>(emptyMap())

    /** Las páginas ya dibujadas y disponibles para pintar. */
    val paginas: StateFlow<Map<ClavePagina, ImageBitmap>> = _paginas.asStateFlow()

    private val _miniaturas = MutableStateFlow<Map<Int, ImageBitmap>>(emptyMap())

    /** Las miniaturas ya dibujadas, por número de página. */
    val miniaturas: StateFlow<Map<Int, ImageBitmap>> = _miniaturas.asStateFlow()

    /** Serializa los saltos entre campos: uno detrás de otro, nunca dos a la vez. */
    private val navegacion = Mutex()

    private val _enlaces = MutableStateFlow<Map<Int, List<EnlacePagina>>>(emptyMap())

    /**
     * Los enlaces de las páginas visibles.
     *
     * Se cargan con la ventana igual que los campos, y por el mismo motivo: hay que
     * saber si hay un enlace bajo el dedo **en el momento del toque**, y preguntárselo
     * al motor entonces metería un viaje al hilo del documento entre el toque y la
     * reacción.
     */
    val enlaces: StateFlow<Map<Int, List<EnlacePagina>>> = _enlaces.asStateFlow()

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
    private var indexado: Job? = null

    /** La búsqueda en vuelo. La cancela la tecla siguiente, o cerrar la barra. */
    private var buscando: Job? = null

    /**
     * Lo que había en cada campo antes de tocarlo, en orden.
     *
     * Tiene tope: un formulario largo con muchas correcciones no puede acumular
     * historia sin límite, y nadie deshace cien pasos en un móvil.
     */
    private val historia = ArrayDeque<CambioDeCampo>()

    private val _desplazarA = MutableStateFlow<DesplazamientoACampo?>(null)

    /** Lo que la lista tiene que traer a la vista, si hay algo pendiente. */
    val desplazarA: StateFlow<DesplazamientoACampo?> = _desplazarA.asStateFlow()
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
                firmado = estadoDocumento.documento.estaFirmado,
                // La vista es del lector y no del documento: cambiar de fichero no
                // devuelve el ajuste a ancho ni endereza lo que se había girado.
                vista = _estado.value.vista,
            )
        viewModelScope.launch {
            val formulario = enElMotor { casos.listarCampos.formulario(id) } ?: return@launch
            _estado.value = _estado.value.copy(formulario = formulario, aviso = avisoDe(formulario))
        }
        viewModelScope.launch {
            // El tamaño de la primera página sirve de estimación para todas: casi
            // ningún documento mezcla formatos, y pedir las 500 al abrir costaría el
            // presupuesto entero de los dos segundos. Cada página corrige su hueco en
            // cuanto se conoce su tamaño real.
            val primera = enElMotor { casos.renderizar.tamano(id, 0) } ?: return@launch
            tamanosReales[0] = primera
            _estado.value = _estado.value.copy(tamanoEstimado = primera)
        }
    }

    /**
     * Hace algo con el motor y devuelve `null` si el documento ya no está.
     *
     * Cerrar un documento mientras se está dibujando no es un caso raro: es lo que
     * pasa cada vez que alguien pulsa «atrás» con páginas encoladas. Los trabajos en
     * vuelo se encuentran con un documento que ya no existe, y sin esto la excepción
     * subiría por una corrutina sin nadie que la recoja: se lleva la aplicación por
     * delante justo al salir, que es cuando peor sienta.
     *
     * La excepción no se registra porque no informa de nada: que el documento se haya
     * cerrado mientras un trabajo estaba en vuelo es lo esperado, y el `null` ya dice
     * todo lo que hay que saber. Llenar el registro con esto taparía lo que sí importa.
     */
    @Suppress("SwallowedException")
    private suspend fun <T> enElMotor(bloque: () -> T): T? =
        withContext(dispatcherRender) {
            try {
                bloque()
            } catch (e: ErrorDocumento.NoEstaAbierto) {
                null
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
        sincronizarEnlaces(id, ventana, vivas)

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
                        val delaPagina = enElMotor { casos.listarCampos(id, pagina) } ?: return@launch
                        _campos.value = _campos.value + (pagina to delaPagina)
                        _estado.value = conNavegacion(_estado.value)
                        trabajosCampos.remove(pagina)
                    }
            }
    }

    /**
     * Trae los enlaces de lo que está a la vista y suelta los de lo que se fue.
     *
     * Se piden para todas las páginas y no sólo cuando el documento «tiene enlaces»:
     * a diferencia del formulario, un PDF no declara en ninguna parte si los lleva, y
     * la única forma de saberlo es mirar página a página. Es barato: es leer una lista
     * corta, no rasterizar.
     */
    private fun sincronizarEnlaces(
        id: IdDocumento,
        ventana: IntRange,
        vivas: List<Int>,
    ) {
        val sobran = _enlaces.value.keys.filter { it !in ventana }
        if (sobran.isNotEmpty()) _enlaces.value = _enlaces.value - sobran.toSet()

        vivas
            .filter { it !in _enlaces.value }
            .forEach { pagina ->
                viewModelScope.launch {
                    val delaPagina = enElMotor { casos.estructura.enlaces(id, pagina) } ?: return@launch
                    _enlaces.value = _enlaces.value + (pagina to delaPagina)
                }
            }
    }

    /**
     * Sigue un enlace: si lleva a otra página, salta; si sale fuera, lo deja
     * preguntado.
     *
     * Un enlace externo **no se abre sin avisar**. En un PDF que llega por correo, ese
     * enlace lo escribió quien mandó el documento, y salir del visor a un navegador es
     * exactamente la clase de cosa que el usuario tiene derecho a confirmar.
     */
    fun seguirEnlace(destino: DestinoEnlace) {
        when (destino) {
            is DestinoEnlace.Pagina -> {
                val estado = _estado.value
                if (destino.numero !in 0 until estado.paginas) return
                irAPagina(destino.numero)
                _desplazarA.value = DesplazamientoACampo(destino.numero, 0f)
            }

            is DestinoEnlace.Fuera -> _estado.value = _estado.value.copy(enlacePreguntado = destino.url)
        }
    }

    /** El usuario ha decidido sobre el enlace externo: se olvida la pregunta. */
    fun olvidarEnlace() {
        _estado.value = _estado.value.copy(enlacePreguntado = null)
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
                    enElMotor {
                        casos.renderizar(id, pagina, CachePaginas.escalaDe(clave.escalaCuantizada)).let {
                            tamanosReales.putIfAbsent(pagina, casos.renderizar.tamano(id, pagina))
                            it.aImageBitmap()
                        }
                    } ?: run {
                        trabajos.remove(clave)
                        return@launch
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
                        enElMotor { casos.renderizar(id, pagina, ESCALA_MINIATURA).aImageBitmap() } ?: run {
                            trabajosMiniatura.remove(pagina)
                            return@launch
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

    /** Elige cómo se encaja la página: a lo ancho, o entera. */
    fun ajustarLaVista(ajuste: AjusteDeVista) = cambiarLaVista { it.copy(ajuste = ajuste) }

    /**
     * Pide dos páginas lado a lado, o vuelve a una.
     *
     * Aquí se guarda el deseo y no el resultado: si la pantalla es estrecha se seguirá
     * viendo una sola, pero al girar el teléfono o al abrir el mismo documento en una
     * tablet aparecerán las dos, sin tener que volver a pedirlo.
     */
    fun alternarDoblePagina() = cambiarLaVista { it.copy(doblePagina = !it.doblePagina) }

    /** Gira lo que se ve un cuarto de vuelta más. El documento no se toca. */
    fun girarLaVista() = cambiarLaVista { it.copy(giro = it.giro.siguiente) }

    /**
     * Lo común a las tres: aplicar en el acto y guardar después.
     *
     * Es la misma disciplina que el tema, y por el mismo motivo: el lector ve el cambio
     * al soltar el dedo, sin esperar al disco, y si el guardado fallara lo que se
     * pierde es la preferencia de mañana, no la vista de ahora.
     */
    private fun cambiarLaVista(cambio: (VistaDelVisor) -> VistaDelVisor) {
        val anterior = _estado.value.vista
        val nueva = cambio(anterior)
        if (nueva == anterior) return

        _estado.value = _estado.value.copy(vista = nueva)
        val almacen = ajustes ?: return
        viewModelScope.launch { almacen.elegirVista(nueva.ajuste.name, nueva.doblePagina, nueva.giro.name) }
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
        indexarPaginasConCampos(id)
    }

    /** Sale del modo y suelta el campo activo con él: fuera del modo no hay campo. */
    fun salirDelFormulario() {
        indexado?.cancel()
        indexado = null
        _estado.value =
            _estado.value.copy(
                modo = ModoVisor.Lectura,
                campoActivo = null,
                paginasConCampos = emptyList(),
            )
    }

    /**
     * Recorre el documento anotando qué páginas traen campos.
     *
     * Hace falta para poder decir la verdad con los botones de campo anterior y
     * siguiente: en el W-9 sólo la primera de sus seis páginas tiene campos, y sin
     * saberlo el botón «siguiente» quedaría encendido en el último campo para no
     * hacer nada al pulsarlo.
     *
     * Va en segundo plano y es cancelable: mira las páginas sin dibujarlas, que es
     * barato, pero en un documento largo son muchas y no puede retrasar lo que el
     * usuario está mirando. Hasta que termina, la navegación funciona con lo que se
     * sabe hasta ese momento.
     */
    private fun indexarPaginasConCampos(id: IdDocumento) {
        indexado?.cancel()
        indexado =
            viewModelScope.launch {
                val paginas = _estado.value.paginas
                val conCampos = mutableListOf<Int>()
                for (pagina in 0 until paginas) {
                    if (!isActive) return@launch
                    // El documento puede cerrarse mientras esto recorre —el usuario
                    // sale, o la aplicación termina—, y entonces no hay nada que
                    // indexar ni nada que avisar: se deja a medias y ya está.
                    val tiene = enElMotor { casos.listarCampos(id, pagina).isNotEmpty() } ?: return@launch
                    if (tiene) {
                        conCampos += pagina
                        _estado.value = conNavegacion(_estado.value.copy(paginasConCampos = conCampos.toList()))
                    }
                }
                _estado.value =
                    conNavegacion(
                        _estado.value.copy(paginasConCampos = conCampos.toList(), indiceCompleto = true),
                    )
            }
    }

    /**
     * Va al campo siguiente, o al anterior. Es el mismo recorrido que usa la tecla
     * «siguiente» del teclado: dos caminos y un solo orden, el que declara el
     * documento.
     */
    fun irACampo(direccion: Direccion) {
        viewModelScope.launch {
            // El estado se lee **dentro** y bajo llave, y no al entrar en la función.
            // Leerlo fuera dejaba una carrera de verdad: dos toques seguidos en
            // «siguiente» —que en un formulario largo es lo normal— leían los dos el
            // mismo campo activo, buscaban los dos el mismo vecino y el segundo toque
            // se perdía. Serializar las navegaciones también las ordena: cada salto
            // parte de donde dejó el anterior.
            navegacion.withLock {
                val estado = _estado.value
                val id = estado.id ?: return@withLock
                if (estado.modo != ModoVisor.Formulario) return@withLock

                val destino = enElMotor { buscarCampo(id, estado, direccion) } ?: return@withLock
                if (destino.pagina !in _campos.value) {
                    val delaPagina = enElMotor { casos.listarCampos(id, destino.pagina) } ?: return@withLock
                    _campos.value = _campos.value + (destino.pagina to delaPagina)
                }
                _estado.value =
                    conNavegacion(_estado.value.copy(campoActivo = destino.id, paginaActual = destino.pagina))
                registro.anotarPagina(id, destino.pagina)
                // Que el campo esté enfocado y debajo del teclado es el fallo clásico de
                // los formularios en el móvil: se pide llevarlo a la vista, siempre.
                _desplazarA.value =
                    DesplazamientoACampo(destino.pagina, destino.marco.y0 / (tamanoDe(destino.pagina)?.alto ?: 1f))
            }
        }
    }

    /** El campo al que toca ir, buscando en otras páginas si en ésta se acabaron. */
    private fun buscarCampo(
        id: IdDocumento,
        estado: EstadoVisor,
        direccion: Direccion,
    ): CampoFormulario? {
        val paginaActual = estado.campoActivo?.pagina ?: estado.paginaActual
        val enEsta = camposEditablesDe(id, paginaActual)
        val posicion = enEsta.indexOfFirst { it.id == estado.campoActivo }

        val vecino = if (posicion < 0) enEsta.firstOrNull() else enEsta.getOrNull(posicion + direccion.paso)
        if (vecino != null) return vecino

        // Se acabaron los de esta página: se busca en la siguiente que tenga.
        val candidatas =
            if (direccion == Direccion.SIGUIENTE) {
                (paginaActual + 1 until estado.paginas)
            } else {
                (paginaActual - 1 downTo 0)
            }
        for (pagina in candidatas) {
            val campos = camposEditablesDe(id, pagina)
            if (campos.isNotEmpty()) {
                return if (direccion == Direccion.SIGUIENTE) campos.first() else campos.last()
            }
        }
        return null
    }

    private fun camposEditablesDe(
        id: IdDocumento,
        pagina: Int,
    ): List<CampoFormulario> = (_campos.value[pagina] ?: casos.listarCampos(id, pagina)).filter { it.esEditable }

    /** El desplazamiento ya se ha hecho: no se repite al recomponer. */
    fun desplazamientoAtendido() {
        _desplazarA.value = null
    }

    fun activarCampo(id: IdCampo?) {
        if (_estado.value.modo != ModoVisor.Formulario) return
        _estado.value = conNavegacion(_estado.value.copy(campoActivo = id))
    }

    /**
     * Recalcula si hay campo antes y después del activo.
     *
     * Mira primero en la página —que es lo que está cargado— y luego en el índice de
     * páginas con campos, que puede estar a medias: mientras se completa, la respuesta
     * es conservadora, y prefiere apagar un botón que encenderlo en falso.
     */
    private fun conNavegacion(estado: EstadoVisor): EstadoVisor {
        if (estado.modo != ModoVisor.Formulario) {
            return estado.copy(hayCampoAnterior = false, hayCampoSiguiente = false)
        }
        val pagina = estado.campoActivo?.pagina ?: estado.paginaActual
        val editables = _campos.value[pagina].orEmpty().filter { it.esEditable }
        val posicion = editables.indexOfFirst { it.id == estado.campoActivo }

        val antesEnOtraPagina = estado.paginasConCampos.any { it < pagina }
        val despuesEnOtraPagina = estado.paginasConCampos.any { it > pagina }

        return estado.copy(
            hayCampoAnterior = (posicion > 0) || antesEnOtraPagina,
            hayCampoSiguiente =
                if (posicion < 0) {
                    editables.isNotEmpty() || despuesEnOtraPagina
                } else {
                    posicion < editables.lastIndex || despuesEnOtraPagina
                },
        )
    }

    /**
     * Escribe un campo de texto. Lo llama la interfaz **al perder el foco**, no en
     * cada tecla: escribir en el documento por pulsación regeneraría la apariencia del
     * campo treinta veces por palabra, y cada una es trabajo del motor.
     */
    fun escribirTexto(
        campo: IdCampo,
        valor: String,
    ) = cambiarCampo(campo) { id -> casos.rellenar.texto(id, campo, valor) }

    /** Marca o desmarca una casilla, o elige un botón de radio. */
    fun alternarCampo(campo: IdCampo) = cambiarCampo(campo) { id -> casos.rellenar.alternar(id, campo) }

    /** Elige una opción de un combo o de una lista. */
    fun elegirOpcion(
        campo: IdCampo,
        opcion: String,
    ) = cambiarCampo(campo) { id -> casos.rellenar.elegir(id, campo, opcion) }

    /**
     * Deshace el último cambio del formulario.
     *
     * Se guarda **lo que había antes** de cada escritura y se vuelve a escribir, en
     * vez de usar el diario del motor: el diario de MuPDF y el guardado incremental
     * cuentan la misma historia de dos maneras, y mezclarlos deshace más de lo que el
     * usuario cree que está deshaciendo. Aquí lo que se deshace es exactamente un
     * cambio suyo.
     */
    fun deshacer() {
        val cambio = historia.removeLastOrNull() ?: return
        _estado.value = _estado.value.copy(hayQueDeshacer = historia.isNotEmpty())
        // Rehacer el valor anterior es otro cambio para el documento, pero no para la
        // historia: si se apuntara, «deshacer» se pelearía consigo mismo.
        cambiarCampo(cambio.campo, apuntar = false) { id -> restaurar(id, cambio) }
    }

    private fun restaurar(
        id: IdDocumento,
        cambio: CambioDeCampo,
    ): CampoFormulario =
        when (cambio.tipo) {
            TipoCampo.CASILLA, TipoCampo.RADIO -> casos.rellenar.alternar(id, cambio.campo)
            TipoCampo.COMBO, TipoCampo.LISTA -> casos.rellenar.elegir(id, cambio.campo, cambio.valor)
            else -> casos.rellenar.texto(id, cambio.campo, cambio.valor)
        }

    /** Apunta cómo estaba el campo antes de tocarlo, para poder volver ahí. */
    private fun apuntarEnLaHistoria(campo: IdCampo) {
        val actual =
            _campos.value[campo.pagina]?.firstOrNull { it.id == campo } ?: return
        historia.addLast(CambioDeCampo(campo, actual.valor, actual.tipo))
        if (historia.size > TOPE_DE_HISTORIA) historia.removeFirst()
        _estado.value = _estado.value.copy(hayQueDeshacer = true)
    }

    /**
     * Lo común a los tres: cambiar, reflejarlo en el overlay y **tirar el render de
     * esa página**.
     *
     * Lo tercero es lo que se olvida: el valor ya está en el documento, pero en la
     * caché sigue la página de antes de escribirlo. Sin tirarla, el usuario ve su
     * texto en el overlay flotando sobre un papel que sigue en blanco, y no sabe si
     * se ha guardado algo o no.
     */
    private fun cambiarCampo(
        campo: IdCampo,
        apuntar: Boolean = true,
        accion: (IdDocumento) -> CampoFormulario,
    ) {
        if (apuntar) apuntarEnLaHistoria(campo)
        val estado = _estado.value
        val id = estado.id ?: return
        // A propósito no se exige estar en el modo: el volcado de lo que quedó a medio
        // escribir llega **justo después** de salir de él —al girar el teléfono, al
        // pulsar «Hecho»—, y exigir el modo aquí tiraría precisamente lo que se estaba
        // salvando. Lo que se puede tocar y lo que no lo decide el dominio.

        viewModelScope.launch {
            val resultado = runCatching { withContext(dispatcherRender) { accion(id) } }
            resultado
                .onSuccess { actualizado ->
                    _campos.value =
                        _campos.value.mapValues { (pagina, lista) ->
                            if (pagina != campo.pagina) lista else lista.map { if (it.id == campo) actualizado else it }
                        }
                    cache.olvidar(campo.pagina)
                    _paginas.value = _paginas.value.filterKeys { it.pagina != campo.pagina }
                    _estado.value = _estado.value.copy(cambiosSinGuardar = true, error = null)
                    pedir(id, campo.pagina, _estado.value.zoom)
                }.onFailure { fallo ->
                    _estado.value = _estado.value.copy(error = mensajeDe(fallo))
                }
        }
    }

    /**
     * Escribe en el fichero lo que se lleva rellenado.
     *
     * Guardar es una acción del usuario y no un efecto de escribir: un guardado por
     * pulsación dejaría una revisión nueva en el fichero por cada letra.
     */
    fun guardar() {
        val estado = _estado.value
        val id = estado.id ?: return
        if (estado.guardando) return

        _estado.value = estado.copy(guardando = true)
        viewModelScope.launch {
            val resultado = runCatching { withContext(dispatcherRender) { casos.guardar(id) } }
            _estado.value =
                resultado.fold(
                    onSuccess = { _estado.value.copy(guardando = false, cambiosSinGuardar = false, error = null) },
                    onFailure = { fallo ->
                        // La marca no se limpia: no se ha guardado, y decir lo contrario
                        // sería la forma más limpia de que alguien pierda su trabajo.
                        _estado.value.copy(guardando = false, error = mensajeDe(fallo))
                    },
                )
        }
    }

    /**
     * Empieza a colocar una firma sobre la página que se está viendo.
     *
     * Nace centrada y ocupando un tercio del ancho, que es un tamaño de firma
     * razonable en un A4; a partir de ahí manda el dedo. La proporción es la del PNG
     * y no se toca nunca al redimensionar, o la letra saldría estirada.
     */
    fun empezarAColocar(firma: Firma) {
        val estado = _estado.value
        if (estado.id == null) return
        val tamano = tamanoDe(estado.paginaActual) ?: return

        val ancho = tamano.ancho * FRACCION_INICIAL_DE_ANCHO
        val alto = ancho * firma.proporcion
        val x = (tamano.ancho - ancho) / 2f
        val y = (tamano.alto - alto) / 2f

        _estado.value =
            estado.copy(
                modo = ModoVisor.ColocarFirma,
                chromeVisible = true,
                campoActivo = null,
                colocacion =
                    ColocacionFirma(
                        firma = firma,
                        pagina = estado.paginaActual,
                        marco = RectPt(x, y, x + ancho, y + alto),
                    ),
            )
    }

    /** Arrastra la firma, sin dejar que se salga de la página. */
    fun moverColocacion(
        dxPt: Float,
        dyPt: Float,
    ) {
        val colocacion = _estado.value.colocacion ?: return
        val tamano = tamanoDe(colocacion.pagina) ?: return
        val marco = colocacion.marco

        val x = (marco.x0 + dxPt).coerceIn(0f, tamano.ancho - marco.ancho)
        val y = (marco.y0 + dyPt).coerceIn(0f, tamano.alto - marco.alto)
        _estado.value =
            _estado.value.copy(
                colocacion = colocacion.copy(marco = RectPt(x, y, x + marco.ancho, y + marco.alto)),
            )
    }

    /**
     * Cambia el tamaño manteniendo la proporción y la esquina de arriba a la
     * izquierda: se estira desde el asa de abajo a la derecha, como en todas partes.
     */
    fun redimensionarColocacion(dAnchoPt: Float) {
        val colocacion = _estado.value.colocacion ?: return
        val tamano = tamanoDe(colocacion.pagina) ?: return
        val marco = colocacion.marco

        val maximo = minOf(tamano.ancho - marco.x0, (tamano.alto - marco.y0) / colocacion.firma.proporcion)
        val ancho = (marco.ancho + dAnchoPt).coerceIn(ANCHO_MINIMO_PT, maximo)
        val alto = ancho * colocacion.firma.proporcion
        _estado.value =
            _estado.value.copy(
                colocacion = colocacion.copy(marco = RectPt(marco.x0, marco.y0, marco.x0 + ancho, marco.y0 + alto)),
            )
    }

    /** Deja el documento como estaba: mientras se colocaba, no se había tocado. */
    fun cancelarColocacion() {
        _estado.value = _estado.value.copy(modo = ModoVisor.Lectura, colocacion = null)
    }

    /** Estampa la firma donde quedó y vuelve a leer la página con ella dentro. */
    fun confirmarColocacion() {
        val estado = _estado.value
        val id = estado.id ?: return
        val colocacion = estado.colocacion ?: return

        viewModelScope.launch {
            val resultado =
                runCatching {
                    withContext(dispatcherRender) {
                        casos.estamparFirma(id, colocacion.firma.id, colocacion.pagina, colocacion.marco)
                    }
                }
            _estado.value =
                resultado.fold(
                    onSuccess = {
                        cache.olvidar(colocacion.pagina)
                        _paginas.value = _paginas.value.filterKeys { it.pagina != colocacion.pagina }
                        _estado.value.copy(
                            modo = ModoVisor.Lectura,
                            colocacion = null,
                            cambiosSinGuardar = true,
                            error = null,
                        )
                    },
                    onFailure = { fallo -> _estado.value.copy(error = mensajeDe(fallo)) },
                )
            if (resultado.isSuccess) pedir(id, colocacion.pagina, _estado.value.zoom)
        }
    }

    /**
     * Abre la búsqueda. El campo empieza vacío pero el modo ya está puesto: la barra
     * cambia al momento y el teclado sube con ella.
     */
    fun abrirBusqueda() {
        if (_estado.value.id == null) return
        _estado.value = _estado.value.copy(modo = ModoVisor.Buscar, chromeVisible = true, busqueda = Busqueda())
    }

    /** Cierra la búsqueda y con ella los resaltados: fuera del modo no hay nada pintado. */
    fun cerrarBusqueda() {
        buscando?.cancel()
        buscando = null
        _estado.value = _estado.value.copy(modo = ModoVisor.Lectura, busqueda = null)
    }

    /**
     * Busca lo escrito, desde la página que se está mirando.
     *
     * Cada tecla cancela la búsqueda anterior y arranca otra: en un documento largo,
     * la de «tot» ya no le importa a nadie cuando se ha escrito «total», y dejarla
     * correr ocuparía el hilo del documento que la nueva necesita.
     */
    fun buscar(termino: String) {
        val estado = _estado.value
        val id = estado.id ?: return
        buscando?.cancel()
        _estado.value = estado.copy(busqueda = Busqueda(termino = termino, buscando = termino.isNotBlank()))
        if (termino.isBlank()) return

        buscando =
            viewModelScope.launch {
                val encontradas = mutableListOf<Coincidencia>()
                withContext(dispatcherRender) {
                    casos.buscar(id, termino, estado.paginas, estado.paginaActual) { enLaPagina ->
                        encontradas += enLaPagina
                        publicarHallazgos(termino, encontradas.toList())
                        // Se sigue mientras la corrutina viva: cancelarla es lo que
                        // hace la tecla siguiente.
                        isActive
                    }
                }
                if (isActive) cerrarLaBusqueda(termino, encontradas.toList())
            }
    }

    /** Lo encontrado hasta ahora, con la primera coincidencia ya señalada. */
    private fun publicarHallazgos(
        termino: String,
        encontradas: List<Coincidencia>,
    ) {
        val busqueda = _estado.value.busqueda ?: return
        if (busqueda.termino != termino) return
        _estado.value =
            _estado.value.copy(
                busqueda =
                    busqueda.copy(
                        coincidencias = encontradas,
                        // La primera que aparece se señala sola: es a la que hay que
                        // llevar al lector sin que tenga que pulsar «siguiente».
                        activa = if (busqueda.activa < 0) 0 else busqueda.activa,
                    ),
            )
        if (busqueda.activa < 0) llevarACoincidencia(0)
    }

    private fun cerrarLaBusqueda(
        termino: String,
        encontradas: List<Coincidencia>,
    ) {
        val busqueda = _estado.value.busqueda ?: return
        if (busqueda.termino != termino) return
        _estado.value =
            _estado.value.copy(
                busqueda = busqueda.copy(coincidencias = encontradas, buscando = false, completa = true),
            )
    }

    /**
     * Salta a la coincidencia siguiente o a la anterior, dando la vuelta por los
     * extremos: en la última, «siguiente» lleva a la primera.
     */
    fun irACoincidencia(direccion: Direccion) {
        val busqueda = _estado.value.busqueda ?: return
        if (!busqueda.hayResultados) return
        val cuantas = busqueda.coincidencias.size
        val siguiente = ((busqueda.activa + direccion.paso) % cuantas + cuantas) % cuantas
        _estado.value = _estado.value.copy(busqueda = busqueda.copy(activa = siguiente))
        llevarACoincidencia(siguiente)
    }

    /** Trae a la vista la coincidencia señalada, como se hace con un campo. */
    private fun llevarACoincidencia(cual: Int) {
        val busqueda = _estado.value.busqueda ?: return
        val coincidencia = busqueda.coincidencias.getOrNull(cual) ?: return
        val id = _estado.value.id ?: return

        _estado.value = _estado.value.copy(paginaActual = coincidencia.pagina)
        registro.anotarPagina(id, coincidencia.pagina)
        _desplazarA.value =
            DesplazamientoACampo(
                pagina = coincidencia.pagina,
                fraccionY = coincidencia.marco.y0 / (tamanoDe(coincidencia.pagina)?.alto ?: 1f),
            )
    }

    /**
     * Empieza una selección en el punto que se ha mantenido pulsado.
     *
     * Si ahí no hay texto no pasa nada: en un escaneado no lo hay en ninguna parte, y
     * entrar en el modo de selección para no poder seleccionar sería peor que no
     * entrar.
     */
    fun seleccionarPalabraEn(
        pagina: Int,
        punto: PuntoPt,
    ) {
        val id = _estado.value.id ?: return
        viewModelScope.launch {
            val encontrada = enElMotor { casos.texto.palabraEn(id, pagina, punto) } ?: return@launch
            _estado.value =
                _estado.value.copy(
                    modo = ModoVisor.SeleccionarTexto,
                    chromeVisible = true,
                    seleccion = SeleccionEnCurso(pagina, punto, punto, encontrada),
                )
        }
    }

    /** Mueve un asa de la selección y vuelve a preguntar qué queda dentro. */
    fun moverAsa(
        inicial: Boolean,
        punto: PuntoPt,
    ) {
        val actual = _estado.value.seleccion ?: return
        val id = _estado.value.id ?: return
        val desde = if (inicial) punto else actual.desde
        val hasta = if (inicial) actual.hasta else punto

        viewModelScope.launch {
            val nueva = enElMotor { casos.texto.seleccionEntre(id, actual.pagina, desde, hasta) } ?: return@launch
            _estado.value =
                _estado.value.copy(seleccion = actual.copy(desde = desde, hasta = hasta, seleccion = nueva))
        }
    }

    /**
     * Marca lo que hay seleccionado y suelta la selección.
     *
     * Se suelta a propósito: la marca ya está puesta y **se ve**, así que dejar encima
     * las asas de la selección sólo taparía lo que se acaba de hacer.
     */
    fun marcarSeleccion(
        tipo: TipoAnotacion,
        color: ColorAnotacion = ColorAnotacion.AMARILLO,
    ) {
        val seleccion = _estado.value.seleccion ?: return
        val id = _estado.value.id ?: return
        if (seleccion.seleccion.marcos.isEmpty()) return

        viewModelScope.launch {
            val hecho =
                runCatching {
                    withContext(dispatcherRender) {
                        casos.marcar.marcar(id, seleccion.pagina, tipo, seleccion.seleccion.marcos, color)
                    }
                }
            hecho
                .onSuccess {
                    // La página cambia: hay que tirar su render o la marca no se vería
                    // hasta salir y volver a entrar.
                    cache.olvidar(seleccion.pagina)
                    _paginas.value = _paginas.value.filterKeys { it.pagina != seleccion.pagina }
                    _estado.value =
                        _estado.value.copy(
                            modo = ModoVisor.Lectura,
                            seleccion = null,
                            cambiosSinGuardar = true,
                            error = null,
                        )
                    pedir(id, seleccion.pagina, _estado.value.zoom)
                }.onFailure { fallo -> _estado.value = _estado.value.copy(error = mensajeDe(fallo)) }
        }
    }

    /** Las marcas de la página que se está mirando, para poder quitarlas. */
    fun cargarAnotaciones() {
        val estado = _estado.value
        val id = estado.id ?: return
        viewModelScope.launch {
            val marcas = enElMotor { casos.marcar.listar(id, estado.paginaActual) } ?: return@launch
            _estado.value = _estado.value.copy(anotaciones = marcas)
        }
    }

    /**
     * Quita una marca y vuelve a dibujar la página.
     *
     * Se recarga la lista después de borrar porque las posiciones se corren: la marca
     * que era la 3 pasa a ser la 2, y con la lista vieja el siguiente borrado se
     * llevaría otra distinta.
     */
    fun borrarAnotacion(anotacion: Anotacion) {
        val id = _estado.value.id ?: return
        viewModelScope.launch {
            val hecho =
                runCatching {
                    withContext(dispatcherRender) { casos.marcar.borrar(id, anotacion.pagina, anotacion.posicion) }
                }
            hecho
                .onSuccess {
                    cache.olvidar(anotacion.pagina)
                    _paginas.value = _paginas.value.filterKeys { it.pagina != anotacion.pagina }
                    _estado.value = _estado.value.copy(cambiosSinGuardar = true, error = null)
                    pedir(id, anotacion.pagina, _estado.value.zoom)
                    cargarAnotaciones()
                }.onFailure { fallo -> _estado.value = _estado.value.copy(error = mensajeDe(fallo)) }
        }
    }

    /**
     * Cambia el texto seleccionado por otro.
     *
     * El original **desaparece** del documento: es una redacción de verdad y no un
     * parche encima, que es lo único que se puede llamar corregir en algo que otro va
     * a leer con otra herramienta.
     *
     * Si el texto nuevo no cabe en el hueco del viejo no se recorta ni se encoge la
     * letra sin decirlo: se avisa y el documento se queda como estaba.
     */
    fun corregirSeleccion(nuevo: String) {
        val seleccion = _estado.value.seleccion ?: return
        val id = _estado.value.id ?: return
        val marco = seleccion.seleccion.marcos.reduceOrNull { uno, otro -> uno.unionCon(otro) } ?: return

        viewModelScope.launch {
            val hecho =
                runCatching {
                    withContext(dispatcherRender) { casos.editar.corregirTexto(id, seleccion.pagina, marco, nuevo) }
                }
            hecho
                .onSuccess { cabia ->
                    if (cabia) {
                        cache.olvidar(seleccion.pagina)
                        _paginas.value = _paginas.value.filterKeys { it.pagina != seleccion.pagina }
                        _estado.value =
                            _estado.value.copy(
                                modo = ModoVisor.Lectura,
                                seleccion = null,
                                cambiosSinGuardar = true,
                                error = null,
                            )
                        pedir(id, seleccion.pagina, _estado.value.zoom)
                    } else {
                        _estado.value =
                            _estado.value.copy(
                                error = "El texto nuevo no cabe donde estaba el viejo. Prueba con uno más corto.",
                            )
                    }
                }.onFailure { fallo -> _estado.value = _estado.value.copy(error = mensajeDe(fallo)) }
        }
    }

    /** Suelta la selección y vuelve a leer. */
    fun soltarSeleccion() {
        _estado.value = _estado.value.copy(modo = ModoVisor.Lectura, seleccion = null)
    }

    /** Pide la ficha del documento para enseñarla. */
    fun verPropiedades() {
        val id = _estado.value.id ?: return
        viewModelScope.launch {
            val ficha = enElMotor { casos.estructura.propiedades(id) } ?: return@launch
            _estado.value = _estado.value.copy(propiedades = ficha)
        }
    }

    fun cerrarPropiedades() {
        _estado.value = _estado.value.copy(propiedades = null)
    }

    /** Pide el índice del documento la primera vez que hace falta. */
    fun cargarIndice() {
        val id = _estado.value.id ?: return
        if (_estado.value.indice.isNotEmpty()) return
        viewModelScope.launch {
            val entradas = enElMotor { casos.estructura.indice(id) } ?: return@launch
            _estado.value = _estado.value.copy(indice = entradas)
        }
    }

    fun descartarError() {
        _estado.value = _estado.value.copy(error = null)
    }

    private fun mensajeDe(fallo: Throwable): String =
        when (fallo) {
            is ErrorDocumento.DocumentoFirmado ->
                "Este documento está firmado: editarlo invalidaría la firma. Guarda una copia editable."

            is ErrorDocumento.SinPermiso -> "Ya no hay permiso para escribir en este documento."
            is ErrorDocumento -> fallo.message ?: "No se ha podido completar la operación."
            else -> "No se ha podido completar la operación."
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
        indexado?.cancel()
        super.onCleared()
    }

    companion object {
        /** Lo que ocupa de ancho una firma recién colocada. */
        const val FRACCION_INICIAL_DE_ANCHO = 0.33f

        /** Por debajo de esto la firma no se ve, y el asa no se puede coger. */
        const val ANCHO_MINIMO_PT = 40f

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

        /** Cuántos cambios de formulario se recuerdan para poder deshacerlos. */
        const val TOPE_DE_HISTORIA = 50
    }
}
