package com.marcmayol.dracpdf

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.CerrarDocumento
import com.marcmayol.dracpdf.dominio.casos.FirmarDocumento
import com.marcmayol.dracpdf.dominio.casos.RecordarDocumentos
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.puertos.DocumentoReciente
import com.marcmayol.dracpdf.dominio.registro.EstadoDocumento
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import com.marcmayol.dracpdf.ui.visor.aImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dónde está la aplicación: sin documento, pidiendo contraseña, o enseñando uno. */
sealed interface EstadoApp {
    data object Inicio : EstadoApp

    data class PidiendoContrasena(
        val origen: OrigenDocumento,
        val fallo: Boolean = false,
    ) : EstadoApp

    data class Viendo(
        val id: IdDocumento,
    ) : EstadoApp

    data class NoSePudoAbrir(
        val nombre: String,
        val motivo: String,
    ) : EstadoApp
}

/**
 * Abre documentos. Es lo único que hace, y por eso es pequeño: el visor tiene su
 * propio modelo y el dominio ya sabe qué es un documento.
 *
 * Siete dependencias que son siete casos de uso, no siete opciones de configuración:
 * abrir, cerrar, el registro, dibujar, firmar, recordar y el motor. Agruparlas en un
 * bulto para bajar la cuenta escondería cuál usa cada función, que es justo lo que aquí
 * se lee de un vistazo.
 */
@Suppress("LongParameterList")
class AppViewModel(
    private val abrirDocumento: AbrirDocumento,
    private val cerrarDocumento: CerrarDocumento,
    private val registro: RegistroDocumentos,
    private val renderizarPagina: RenderizarPagina? = null,
    private val firmarDocumento: FirmarDocumento? = null,
    private val recordar: RecordarDocumentos? = null,
    private val repositorio: DocumentRepository? = null,
) : ViewModel() {
    private val _recientes = MutableStateFlow<List<DocumentoReciente>>(emptyList())

    /** Los documentos por los que se pasó, para la pantalla de inicio. */
    val recientes: StateFlow<List<DocumentoReciente>> = _recientes.asStateFlow()

    private val _efimeros = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Los documentos abiertos con un permiso que muere con la sesión: los que llegan
     * compartidos desde otra aplicación.
     *
     * La aplicación tiene que tratarlos distinto —ofrecer «guardar una copia» en vez
     * de dejar creer que ese documento va a seguir ahí mañana— y por eso se recuerda
     * cuáles son, por identificador.
     */
    val efimeros: StateFlow<Set<String>> = _efimeros.asStateFlow()
    private val _estado = MutableStateFlow<EstadoApp>(EstadoApp.Inicio)
    val estado: StateFlow<EstadoApp> = _estado.asStateFlow()

    private val _abiertos = MutableStateFlow<List<EstadoDocumento>>(emptyList())

    /**
     * Los documentos abiertos a la vez.
     *
     * El registro es el dueño de la lista; esto es sólo su reflejo para la interfaz,
     * que se refresca cuando algo cambia. Duplicar la verdad en dos sitios es la
     * manera más rápida de que dejen de coincidir.
     */
    val abiertos: StateFlow<List<EstadoDocumento>> = _abiertos.asStateFlow()

    /**
     * Abre un documento. Si está cifrado, el estado pasa a pedir la contraseña en vez
     * de fallar: un PDF protegido no es un error, es un PDF que espera.
     */
    fun abrir(
        origen: OrigenDocumento,
        contrasena: String? = null,
        permisoPersistido: Boolean = false,
    ) {
        viewModelScope.launch {
            // Abrir toca disco y el motor nativo: nunca en el hilo de la interfaz,
            // aunque casi siempre tarde unos milisegundos.
            val resultado = withContext(Dispatchers.IO) { runCatching { abrirDocumento(origen, contrasena) } }

            _estado.value =
                resultado.fold(
                    onSuccess = { abierto ->
                        // Un documento puede llegar ya firmado, y casi siempre lo habrá
                        // firmado otra persona. Se comprueba al abrir porque a partir de
                        // ahí hay que tratarlo con cuidado: editar invalidaría la firma
                        // de alguien.
                        withContext(Dispatchers.IO) {
                            runCatching { firmarDocumento?.marcarSiEstaFirmado(abierto.id) }
                            // Apuntar el reciente **restaura por dónde iba**, así que
                            // tiene que pasar antes de que la pantalla lo enseñe: si no,
                            // el visor abriría por la primera página y saltaría después.
                            runCatching {
                                recordar?.alAbrir(abierto.id, origen, abierto.documento.nombre, permisoPersistido)
                            }
                        }
                        refrescarRecientes()
                        if (!permisoPersistido) _efimeros.value = _efimeros.value + abierto.id.valor
                        EstadoApp.Viendo(abierto.id)
                    },
                    onFailure = { fallo -> estadoDelFallo(origen, contrasena, fallo) },
                )
            refrescarAbiertos()
        }
    }

    /**
     * Cambia al documento elegido en la lista. No se cierra el anterior: la gracia de
     * tener varios abiertos es volver a ellos por donde iban.
     */
    fun cambiarA(id: IdDocumento) {
        if (registro.estaAbierto(id)) _estado.value = EstadoApp.Viendo(id)
    }

    /**
     * Cierra un documento de la lista.
     *
     * Si era el que se estaba mirando, se pasa al siguiente que quede abierto, y sólo
     * si no queda ninguno se vuelve a inicio: cerrar una pestaña no debería echarte de
     * las demás.
     */
    fun cerrar(id: IdDocumento) {
        viewModelScope.launch {
            val eraElActivo = (_estado.value as? EstadoApp.Viendo)?.id == id
            withContext(Dispatchers.IO) {
                // Antes de cerrarlo, mientras todavía se le puede preguntar por dónde
                // iba: después, el documento ya no existe para nadie.
                runCatching { recordar?.alCerrar(id) }
                runCatching { cerrarDocumento(id) }
            }
            refrescarRecientes()
            refrescarAbiertos()

            if (eraElActivo) {
                val siguiente = _abiertos.value.firstOrNull()
                _estado.value = if (siguiente == null) EstadoApp.Inicio else EstadoApp.Viendo(siguiente.id)
            }
        }
    }

    fun cerrarTodos() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                registro.abiertos().forEach { abierto ->
                    runCatching { recordar?.alCerrar(abierto.id) }
                    runCatching { cerrarDocumento(abierto.id) }
                }
            }
            refrescarAbiertos()
            refrescarRecientes()
            _estado.value = EstadoApp.Inicio
        }
    }

    /**
     * Guarda el documento abierto en otro sitio.
     *
     * Es lo que rescata un documento prestado: el que llegó compartido desde otra
     * aplicación deja de poder abrirse en cuanto el sistema retira el permiso, y esta
     * es la única manera de quedárselo. Lo que se escribe es **lo que hay en memoria**,
     * con los cambios sin guardar incluidos, que es lo que el usuario está viendo.
     */
    fun guardarCopia(
        id: IdDocumento,
        destino: OrigenDocumento,
    ) {
        val motor = repositorio ?: return
        viewModelScope.launch {
            val hecho = withContext(Dispatchers.IO) { runCatching { motor.copiarA(id, destino) } }
            _estado.value =
                hecho.fold(
                    onSuccess = { _estado.value },
                    onFailure = { fallo -> estadoDelFallo(destino, null, fallo) },
                )
        }
    }

    /** Trae los recientes del disco a la interfaz. */
    fun refrescarRecientes() {
        val almacen = recordar ?: return
        viewModelScope.launch {
            _recientes.value =
                withContext(Dispatchers.IO) { runCatching { almacen.listar() }.getOrDefault(emptyList()) }
        }
    }

    /** Quita un documento de la lista de recientes. No lo borra del teléfono. */
    fun olvidarReciente(identificador: String) {
        val almacen = recordar ?: return
        viewModelScope.launch {
            val victima = _recientes.value.firstOrNull { it.origen.identificador == identificador } ?: return@launch
            withContext(Dispatchers.IO) { runCatching { almacen.olvidar(victima.origen) } }
            refrescarRecientes()
        }
    }

    private val _miniaturas = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** La primera página de cada documento abierto, para la lista. */
    val miniaturas: StateFlow<Map<String, ImageBitmap>> = _miniaturas.asStateFlow()

    /**
     * Dibuja la primera página de un documento para su fila de la lista.
     *
     * Es un render por documento y a escala mínima, y se queda hecho: sin él la lista
     * enseña rectángulos blancos, que es peor que no enseñar nada porque parece que
     * el documento está vacío.
     */
    fun pedirMiniatura(id: String) {
        val render = renderizarPagina ?: return
        if (_miniaturas.value.containsKey(id)) return
        val documento = IdDocumento(id)
        if (!registro.estaAbierto(documento)) return

        viewModelScope.launch {
            val mapa =
                withContext(Dispatchers.IO) {
                    runCatching { render(documento, 0, ESCALA_MINIATURA).aImageBitmap() }.getOrNull()
                }
            if (mapa != null) _miniaturas.value = _miniaturas.value + (id to mapa)
        }
    }

    private fun refrescarAbiertos() {
        _abiertos.value = registro.abiertos()
        // Las miniaturas de documentos ya cerrados no tienen a quién acompañar.
        val vivos = _abiertos.value.map { it.id.valor }.toSet()
        _miniaturas.value = _miniaturas.value.filterKeys { it in vivos }
    }

    private fun estadoDelFallo(
        origen: OrigenDocumento,
        contrasena: String?,
        fallo: Throwable,
    ): EstadoApp =
        when (fallo) {
            is ErrorDocumento.NecesitaContrasena -> EstadoApp.PidiendoContrasena(origen)
            // Se vuelve a pedir marcando el fallo: quien se equivoca al teclear quiere
            // reintentar, no volver al principio.
            is ErrorDocumento.ContrasenaIncorrecta ->
                EstadoApp.PidiendoContrasena(origen, fallo = contrasena != null)
            is ErrorDocumento.SinPermiso ->
                EstadoApp.NoSePudoAbrir(nombreDe(origen), "Ya no hay permiso para leer este documento.")
            // Se distingue no haber podido llegar al fichero de no haber podido
            // entenderlo: decir «¿es un PDF válido?» cuando el problema es el acceso
            // manda a buscar el fallo donde no está.
            is ErrorDocumento.NoSePuedeAbrirElFichero ->
                EstadoApp.NoSePudoAbrir(
                    nombreDe(origen),
                    "No se ha podido acceder al fichero. Puede que se haya movido o que la aplicación " +
                        "que lo comparte ya no lo permita.",
                )
            else ->
                EstadoApp.NoSePudoAbrir(nombreDe(origen), "El documento no se ha podido leer. ¿Es un PDF válido?")
        }

    fun cancelarContrasena() {
        _estado.value = EstadoApp.Inicio
    }

    /**
     * Vuelve a la pantalla de inicio cerrando el documento que se estaba mirando.
     *
     * La flecha de atrás cierra ese documento y sólo ese: los demás siguen abiertos y
     * se ven en la sección «Abiertos» del inicio.
     */
    fun volverAlInicio() {
        val actual = _estado.value
        if (actual is EstadoApp.Viendo) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { runCatching { cerrarDocumento(actual.id) } }
                refrescarAbiertos()
                _estado.value = EstadoApp.Inicio
            }
        } else {
            _estado.value = EstadoApp.Inicio
        }
    }

    private companion object {
        /** Una A4 queda en unos 150 x 210 px: de sobra para 36 x 44 dp. */
        const val ESCALA_MINIATURA = 0.25f
    }

    private fun nombreDe(origen: OrigenDocumento): String =
        when (origen) {
            is OrigenDocumento.Externo -> origen.nombre
            is OrigenDocumento.Privado -> origen.nombre
        }
}
