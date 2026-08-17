package com.marcmayol.dracpdf.ui.herramientas

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.dominio.casos.AvisosDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.ComprimirDocumento
import com.marcmayol.dracpdf.dominio.casos.ConvertirAPdf
import com.marcmayol.dracpdf.dominio.casos.ConvertirDocumento
import com.marcmayol.dracpdf.dominio.casos.ConvertirEstructura
import com.marcmayol.dracpdf.dominio.casos.DesprotegerDocumento
import com.marcmayol.dracpdf.dominio.casos.DividirDocumento
import com.marcmayol.dracpdf.dominio.casos.OrganizarPaginas
import com.marcmayol.dracpdf.dominio.casos.ProtegerDocumento
import com.marcmayol.dracpdf.dominio.casos.ResultadoConversion
import com.marcmayol.dracpdf.dominio.casos.RevisarAntesDeOperar
import com.marcmayol.dracpdf.dominio.casos.UnirDocumentos
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.EntradaAConvertir
import com.marcmayol.dracpdf.dominio.puertos.FormatoSalida
import com.marcmayol.dracpdf.dominio.puertos.FormatoTabla
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import com.marcmayol.dracpdf.dominio.puertos.TipoDeEntrada
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Los casos de uso de la caja de herramientas, en un solo bulto.
 *
 * Van juntos por el mismo motivo que los del visor: sueltos convertían el constructor
 * en una lista de la compra donde el orden de los argumentos era lo único que sabía
 * qué es cada cosa.
 *
 * El aviso de «demasiados parámetros» se silencia porque aquí no aplica: esto no es
 * una función a la que se le pasan siete cosas, es la lista de las siete herramientas
 * que hay. Partirla en dos bultos para bajar la cuenta escondería la simetría en vez
 * de simplificar nada.
 */
@Suppress("LongParameterList")
class CasosDeHerramientas(
    val unir: UnirDocumentos,
    val organizar: OrganizarPaginas,
    val dividir: DividirDocumento,
    val proteger: ProtegerDocumento,
    val desproteger: DesprotegerDocumento,
    val comprimir: ComprimirDocumento,
    val convertir: ConvertirDocumento,
    /** Las conversiones de la Fase 9: HTML, Markdown, ODT, RTF y tablas. */
    val convertirEstructura: ConvertirEstructura,
    /** La conversión al revés: de imágenes y textos sueltos a un PDF nuevo. */
    val crearPdf: ConvertirAPdf,
)

/** Por dónde va una operación larga, para enseñarlo y poder pararla. */
data class EstadoHerramienta(
    val herramienta: Herramienta? = null,
    val hechas: Int = 0,
    val total: Int = 0,
    /** Lo que hay que contar cuando termina bien: «Ahorrado un 38 %», por ejemplo. */
    val resultado: String? = null,
    val error: String? = null,
    val avisos: AvisosDeHerramienta? = null,
) {
    val enMarcha: Boolean get() = herramienta != null && resultado == null && error == null

    /** Entre 0 y 1; `null` mientras no se sepa cuánto hay que hacer. */
    val fraccion: Float? get() = if (total <= 0) null else hechas.toFloat() / total
}

/**
 * Las herramientas, ejecutándose fuera del hilo de la interfaz.
 *
 * **Una operación a la vez.** No es una limitación técnica sino la única forma
 * honesta de enseñar progreso: dos barras compitiendo por la misma pantalla no dicen
 * nada, y dos herramientas escribiendo a la vez sobre los mismos ficheros son un
 * problema mucho peor que una espera.
 *
 * Una función por herramienta y por destino de conversión. Son muchas porque la caja de
 * herramientas tiene muchas cosas dentro, y agruparlas en una sola con un parámetro
 * «qué hacer» escondería justo lo que aquí se lee de un vistazo: qué se puede pedir.
 */
@Suppress("TooManyFunctions")
class HerramientasViewModel(
    private val casos: CasosDeHerramientas,
    // Va suelto y no en el bulto: revisar no es una operación, es lo que se consulta
    // **antes** de decidir si se hace alguna.
    private val revisar: RevisarAntesDeOperar,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoHerramienta())
    val estado: StateFlow<EstadoHerramienta> = _estado.asStateFlow()

    /**
     * La bandera de cancelación.
     *
     * Es atómica porque la escribe el hilo de la interfaz —cuando el usuario pulsa
     * «Cancelar»— y la lee el hilo que está trabajando, en cada página. Cancelar deja
     * el destino sin escribir, no a medias: de eso se encarga el adaptador.
     */
    private val cancelada = AtomicBoolean(false)

    fun cancelar() {
        cancelada.set(true)
    }

    fun descartarResultado() {
        _estado.value = EstadoHerramienta()
    }

    /** Lo que hay que advertir antes de operar sobre estos ficheros. */
    fun revisar(origenes: List<OrigenDocumento>) {
        viewModelScope.launch {
            val avisos = withContext(dispatcher) { revisar.invoke(origenes) }
            _estado.value = _estado.value.copy(avisos = avisos)
        }
    }

    fun unir(
        origenes: List<OrigenDocumento>,
        destino: OrigenDocumento,
    ) = enMarcha(Herramienta.UNIR) { progreso ->
        casos.unir.invoke(origenes, destino, progreso)
        "${origenes.size} documentos unidos"
    }

    fun organizar(
        origen: OrigenDocumento,
        paginas: List<PaginaOrdenada>,
        destino: OrigenDocumento,
    ) = enMarcha(Herramienta.ORGANIZAR) { progreso ->
        casos.organizar.invoke(origen, paginas, destino, progreso)
        "${paginas.size} páginas guardadas"
    }

    fun dividir(
        origen: OrigenDocumento,
        rangos: List<IntRange>,
        destinos: List<OrigenDocumento>,
    ) = enMarcha(Herramienta.DIVIDIR) { progreso ->
        casos.dividir.invoke(origen, rangos, destinos, progreso)
        "${rangos.size} documentos"
    }

    fun proteger(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
    ) = enMarcha(Herramienta.PROTEGER) {
        casos.proteger.invoke(origen, destino, contrasena)
        "Documento protegido"
    }

    fun desproteger(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
    ) = enMarcha(Herramienta.PROTEGER) {
        casos.desproteger.invoke(origen, destino, contrasena)
        "Contraseña quitada"
    }

    fun comprimir(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
    ) = enMarcha(Herramienta.COMPRIMIR) { progreso ->
        val reduccion = casos.comprimir.invoke(origen, destino, progreso)
        // Se dice lo que ha pasado de verdad, también cuando no ha encogido: un PDF ya
        // optimizado puede crecer unos bytes, y fingir una mejora sería mentir.
        val porcentaje = (reduccion.fraccion * PORCENTAJE).toInt()
        if (porcentaje > 0) {
            "Ahorrado un $porcentaje %: ${enKb(reduccion.antes)} → ${enKb(reduccion.despues)}"
        } else {
            "Ya estaba optimizado: ${enKb(reduccion.antes)} → ${enKb(reduccion.despues)}"
        }
    }

    fun convertirAImagenes(
        origen: OrigenDocumento,
        paginas: List<Int>,
        carpeta: OrigenDocumento,
        ajustes: AjustesImagen,
    ) = enMarcha(Herramienta.CONVERTIR) { progreso ->
        val escritas = casos.convertir.aImagenes(origen, paginas, carpeta, ajustes, progreso)
        "${escritas.size} imágenes"
    }

    /**
     * Convierte el documento **abierto** a otro formato de texto.
     *
     * Trabaja sobre el documento de la sesión y no sobre el fichero, al revés que el
     * resto de herramientas: deducir títulos y tablas necesita el documento ya
     * analizado por el motor, y volver a abrirlo desde el disco sería recorrerlo dos
     * veces para llegar a lo mismo.
     */
    fun convertirDocumentoA(
        id: IdDocumento,
        formato: FormatoSalida,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ) = enMarcha(Herramienta.CONVERTIR) { progreso ->
        val estructura = casos.convertirEstructura.leer(id, progreso)
        contar(casos.convertirEstructura.aDocumento(estructura, formato, carpeta, nombreBase))
    }

    /** Las tablas del documento, a CSV o a XLSX. */
    fun convertirTablas(
        id: IdDocumento,
        formato: FormatoTabla,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ) = enMarcha(Herramienta.CONVERTIR) { progreso ->
        val estructura = casos.convertirEstructura.leer(id, progreso)
        contar(casos.convertirEstructura.aTablas(estructura, formato, carpeta, nombreBase))
    }

    /**
     * Lo que hay que decir de una conversión.
     *
     * Los dos «no» se cuentan como resultados y no como errores porque no lo son: un
     * escaneado y un documento sin tablas están bien, y lo único que pasa es que no
     * había nada que escribir.
     */
    private fun contar(resultado: ResultadoConversion): String =
        when (resultado) {
            is ResultadoConversion.Escrito ->
                if (resultado.ficheros.size == 1) {
                    "Guardado en «${nombreDe(resultado.ficheros.single())}»"
                } else {
                    "${resultado.ficheros.size} ficheros guardados"
                }

            ResultadoConversion.PareceEscaneado -> "Este documento no tiene texto: parece escaneado"
            ResultadoConversion.SinTablas -> "No se ha encontrado ninguna tabla que exportar"
        }

    private fun nombreDe(origen: OrigenDocumento) =
        when (origen) {
            is OrigenDocumento.Externo -> origen.nombre
            is OrigenDocumento.Privado -> origen.nombre
        }

    /**
     * Monta un PDF con lo que se le dé: imágenes, Markdown, HTML o texto.
     *
     * Es la conversión al revés que las demás, y la única que no parte de un documento
     * abierto: aquí no hay PDF todavía, se está haciendo uno.
     */
    fun crearPdfDesde(
        entradas: List<EntradaAConvertir>,
        destino: OrigenDocumento,
    ) = enMarcha(Herramienta.CONVERTIR) { progreso ->
        val paginas = casos.crearPdf.invoke(entradas, destino, progreso)
        if (paginas == 0) {
            "No había nada que convertir"
        } else {
            "PDF creado con $paginas ${if (paginas == 1) "página" else "páginas"}"
        }
    }

    /**
     * Monta el PDF de una tanda de escaneo.
     *
     * El destino es un fichero de la propia aplicación y no un sitio elegido por el
     * usuario: lo que se acaba de escanear no existía hace un minuto, así que primero
     * se abre para verlo y ya se decide después si se guarda en otro sitio, se comparte
     * o se tira.
     */
    fun crearPdfDesdeEscaneo(
        recortes: List<File>,
        carpeta: File,
    ) = enMarcha(Herramienta.CONVERTIR) { progreso ->
        val destino = File(carpeta, "escaneo-${System.currentTimeMillis()}.pdf")
        val entradas =
            recortes.map { fichero ->
                EntradaAConvertir(
                    OrigenDocumento.Privado(fichero.absolutePath, fichero.name),
                    TipoDeEntrada.IMAGEN,
                )
            }
        val paginas =
            casos.crearPdf.invoke(
                entradas,
                OrigenDocumento.Privado(destino.absolutePath, destino.name),
                progreso,
            )
        if (paginas == 0) "No se ha escaneado ninguna hoja" else "PDF de $paginas hojas escaneadas"
    }

    fun convertirATexto(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
    ) = enMarcha(Herramienta.CONVERTIR) { progreso ->
        // Un escaneado no tiene texto que sacar. Se dice, en vez de entregar un fichero
        // vacío y dejar al usuario preguntándose qué ha fallado.
        if (casos.convertir.aTexto(origen, destino, progreso)) {
            "Texto guardado"
        } else {
            "Este documento no tiene texto: parece escaneado"
        }
    }

    /**
     * Monta el andamio común: marca la herramienta en marcha, corre fuera del hilo de
     * la interfaz, va contando y recoge el final —bien, cancelado o roto—.
     */
    private fun enMarcha(
        herramienta: Herramienta,
        trabajo: suspend (Progreso) -> String,
    ) {
        if (_estado.value.enMarcha) return
        cancelada.set(false)
        _estado.value = EstadoHerramienta(herramienta = herramienta)

        viewModelScope.launch {
            val progreso =
                Progreso { hechas, total ->
                    _estado.value = _estado.value.copy(hechas = hechas, total = total)
                    !cancelada.get()
                }
            val resultado = runCatching { withContext(dispatcher) { trabajo(progreso) } }

            _estado.value =
                when {
                    cancelada.get() -> EstadoHerramienta(herramienta = herramienta, resultado = "Cancelado")
                    resultado.isSuccess ->
                        _estado.value.copy(resultado = resultado.getOrNull())

                    else ->
                        _estado.value.copy(error = mensajeDe(resultado.exceptionOrNull()))
                }
        }
    }

    private fun mensajeDe(fallo: Throwable?): String =
        when (fallo) {
            is ErrorDocumento.FicheroFirmado ->
                "«${fallo.origen.nombreCorto()}» está firmado: esta operación rompería su firma."

            is ErrorDocumento.ContrasenaIncorrecta -> "La contraseña no abre el documento."
            is ErrorDocumento.NecesitaContrasena -> "El documento está protegido: hace falta su contraseña."
            is ErrorDocumento -> fallo.message ?: "No se ha podido completar la operación."
            else -> fallo?.message ?: "No se ha podido completar la operación."
        }

    private fun OrigenDocumento.nombreCorto() =
        when (this) {
            is OrigenDocumento.Externo -> nombre
            is OrigenDocumento.Privado -> nombre
        }

    private fun enKb(bytes: Long) = "${bytes / KB} KB"

    private companion object {
        const val PORCENTAJE = 100
        const val KB = 1024
    }
}
