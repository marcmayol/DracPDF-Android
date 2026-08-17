package com.marcmayol.dracpdf.ui.escaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.adaptadores.camara.EsquinasDeHoja
import com.marcmayol.dracpdf.adaptadores.camara.RecorteDeHoja
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Una hoja capturada, antes y después de enderezarla. */
data class HojaEscaneada(
    /** La foto tal como salió de la cámara. Se conserva para poder recortar otra vez. */
    val foto: File,
    /** El recorte ya enderezado, que es lo que irá al PDF. */
    val recorte: File,
    val esquinas: EsquinasDeHoja,
)

/**
 * Qué está pasando en el escáner.
 *
 * Las hojas se acumulan porque escanear un documento es escanear varias: pedir el
 * destino después de cada foto convertiría un contrato de seis páginas en seis PDF.
 */
data class EstadoEscaner(
    val hojas: List<HojaEscaneada> = emptyList(),
    /** La que se está ajustando ahora mismo, si hay alguna. */
    val ajustando: Int? = null,
    val trabajando: Boolean = false,
    val error: String? = null,
) {
    val hayHojas: Boolean get() = hojas.isNotEmpty()
}

/**
 * El escáner: capturar hojas, enderezarlas y quedarse con ellas hasta montar el PDF.
 *
 * **La foto original no se tira** hasta el final. Un recorte mal ajustado es lo más
 * normal del mundo —los tiradores se ponen a ojo— y sin la foto de partida corregirlo
 * significaría volver a hacer la fotografía.
 *
 * Todo el trabajo con mapas de bits pasa por el despachador de disco: enderezar una
 * foto de doce megapíxeles en el hilo de la interfaz congela la pantalla justo cuando
 * el usuario acaba de disparar.
 */
class EscanerViewModel(
    private val carpeta: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoEscaner())
    val estado: StateFlow<EstadoEscaner> = _estado.asStateFlow()

    /** Un fichero nuevo para que la cámara deje ahí la foto. */
    fun ficheroParaLaFoto(): File {
        carpeta.mkdirs()
        return File(carpeta, "hoja-${System.currentTimeMillis()}.jpg")
    }

    /**
     * Entra una hoja recién fotografiada.
     *
     * Se recorta de entrada con la foto entera, que es lo honesto: detectar los bordes
     * automáticamente y fallar deja los tiradores en un sitio peor que las esquinas, y
     * moverlos desde ahí cuesta más que ponerlos desde cero.
     */
    fun capturada(foto: File) {
        viewModelScope.launch {
            _estado.value = _estado.value.copy(trabajando = true, error = null)
            val hecho =
                runCatching {
                    withContext(dispatcher) {
                        val mapa = RecorteDeHoja.fotoDe(foto)
                        val esquinas =
                            try {
                                EsquinasDeHoja.deTodaLaFoto(mapa.width, mapa.height)
                            } finally {
                                mapa.recycle()
                            }
                        val recorte = File(carpeta, "recorte-${foto.nameWithoutExtension}.jpg")
                        RecorteDeHoja.corregirAFichero(foto, esquinas, recorte)
                        HojaEscaneada(foto, recorte, esquinas)
                    }
                }

            _estado.value =
                hecho.fold(
                    onSuccess = { hoja ->
                        _estado.value.copy(hojas = _estado.value.hojas + hoja, trabajando = false)
                    },
                    onFailure = { fallo ->
                        _estado.value.copy(trabajando = false, error = fallo.message ?: "No se ha podido usar la foto")
                    },
                )
        }
    }

    /** Vuelve a recortar una hoja con las esquinas que el usuario acaba de mover. */
    fun ajustar(
        cual: Int,
        esquinas: EsquinasDeHoja,
    ) {
        val hoja = _estado.value.hojas.getOrNull(cual) ?: return
        viewModelScope.launch {
            _estado.value = _estado.value.copy(trabajando = true)
            val hecho =
                runCatching {
                    withContext(dispatcher) { RecorteDeHoja.corregirAFichero(hoja.foto, esquinas, hoja.recorte) }
                }
            _estado.value =
                hecho.fold(
                    onSuccess = {
                        val actualizadas = _estado.value.hojas.toMutableList()
                        actualizadas[cual] = hoja.copy(esquinas = esquinas)
                        _estado.value.copy(hojas = actualizadas, ajustando = null, trabajando = false)
                    },
                    onFailure = { fallo ->
                        _estado.value.copy(trabajando = false, error = fallo.message ?: "No se ha podido recortar")
                    },
                )
        }
    }

    fun empezarAAjustar(cual: Int) {
        if (cual in _estado.value.hojas.indices) _estado.value = _estado.value.copy(ajustando = cual)
    }

    fun dejarDeAjustar() {
        _estado.value = _estado.value.copy(ajustando = null)
    }

    /** Quita una hoja de la tanda y borra sus ficheros: no va a ir a ninguna parte. */
    fun descartar(cual: Int) {
        val hoja = _estado.value.hojas.getOrNull(cual) ?: return
        _estado.value = _estado.value.copy(hojas = _estado.value.hojas - hoja, ajustando = null)
        viewModelScope.launch {
            withContext(dispatcher) {
                runCatching { hoja.foto.delete() }
                runCatching { hoja.recorte.delete() }
            }
        }
    }

    /** Los recortes en orden, que es lo que hay que convertir en PDF. */
    fun recortes(): List<File> = _estado.value.hojas.map { it.recorte }

    /** Se acabó la tanda: se sueltan los ficheros de trabajo. */
    fun terminar() {
        val hojas = _estado.value.hojas
        _estado.value = EstadoEscaner()
        viewModelScope.launch {
            withContext(dispatcher) {
                hojas.forEach { hoja ->
                    runCatching { hoja.foto.delete() }
                    runCatching { hoja.recorte.delete() }
                }
            }
        }
    }

    fun descartarError() {
        _estado.value = _estado.value.copy(error = null)
    }
}
