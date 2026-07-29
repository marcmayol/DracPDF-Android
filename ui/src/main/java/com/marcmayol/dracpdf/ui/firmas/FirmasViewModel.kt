package com.marcmayol.dracpdf.ui.firmas

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.puertos.AlmacenFirmas
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * La biblioteca de firmas.
 *
 * Va en su propio modelo y no dentro del visor porque no depende del documento: las
 * firmas son del usuario y siguen ahí con cualquier PDF abierto, o sin ninguno.
 */
class FirmasViewModel(
    private val almacen: AlmacenFirmas,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _firmas = MutableStateFlow<List<Firma>>(emptyList())
    val firmas: StateFlow<List<Firma>> = _firmas.asStateFlow()

    private val _miniaturas = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** Las firmas ya decodificadas para enseñarlas, por identificador. */
    val miniaturas: StateFlow<Map<String, ImageBitmap>> = _miniaturas.asStateFlow()

    fun cargar() {
        viewModelScope.launch {
            val guardadas = withContext(dispatcher) { almacen.listar() }
            _firmas.value = guardadas
            guardadas.forEach { firma -> cargarMiniatura(firma) }
        }
    }

    private fun cargarMiniatura(firma: Firma) {
        if (_miniaturas.value.containsKey(firma.id.valor)) return
        viewModelScope.launch {
            val mapa =
                withContext(dispatcher) {
                    runCatching {
                        val bytes = almacen.leer(firma.id)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
            if (mapa != null) _miniaturas.value = _miniaturas.value + (firma.id.valor to mapa)
        }
    }

    /** Guarda lo dibujado y devuelve la ficha por si hay que colocarla enseguida. */
    fun guardar(
        dibujada: FirmaDibujada,
        nombre: String = "",
        alGuardar: (Firma) -> Unit = {},
    ) {
        viewModelScope.launch {
            val firma =
                withContext(dispatcher) {
                    almacen.guardar(dibujada.png, dibujada.anchoPx, dibujada.altoPx, nombre)
                }
            _firmas.value = listOf(firma) + _firmas.value
            cargarMiniatura(firma)
            alGuardar(firma)
        }
    }

    fun borrar(firma: Firma) {
        viewModelScope.launch {
            withContext(dispatcher) { almacen.borrar(firma.id) }
            _firmas.value = _firmas.value.filterNot { it.id == firma.id }
            _miniaturas.value = _miniaturas.value - firma.id.valor
        }
    }
}
