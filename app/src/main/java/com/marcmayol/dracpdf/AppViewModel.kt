package com.marcmayol.dracpdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.CerrarDocumento
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
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
 */
class AppViewModel(
    private val abrirDocumento: AbrirDocumento,
    private val cerrarDocumento: CerrarDocumento,
) : ViewModel() {
    private val _estado = MutableStateFlow<EstadoApp>(EstadoApp.Inicio)
    val estado: StateFlow<EstadoApp> = _estado.asStateFlow()

    /**
     * Abre un documento. Si está cifrado, el estado pasa a pedir la contraseña en vez
     * de fallar: un PDF protegido no es un error, es un PDF que espera.
     */
    fun abrir(
        origen: OrigenDocumento,
        contrasena: String? = null,
    ) {
        viewModelScope.launch {
            // Abrir toca disco y el motor nativo: nunca en el hilo de la interfaz,
            // aunque casi siempre tarde unos milisegundos.
            val resultado = withContext(Dispatchers.IO) { runCatching { abrirDocumento(origen, contrasena) } }

            _estado.value =
                resultado.fold(
                    onSuccess = { EstadoApp.Viendo(it.id) },
                    onFailure = { fallo -> estadoDelFallo(origen, contrasena, fallo) },
                )
        }
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

    fun volverAlInicio() {
        val actual = _estado.value
        if (actual is EstadoApp.Viendo) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) { runCatching { cerrarDocumento(actual.id) } }
                _estado.value = EstadoApp.Inicio
            }
        } else {
            _estado.value = EstadoApp.Inicio
        }
    }

    private fun nombreDe(origen: OrigenDocumento): String =
        when (origen) {
            is OrigenDocumento.Externo -> origen.nombre
            is OrigenDocumento.Privado -> origen.nombre
        }
}
