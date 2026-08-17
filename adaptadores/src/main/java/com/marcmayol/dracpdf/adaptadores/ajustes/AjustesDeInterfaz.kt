package com.marcmayol.dracpdf.adaptadores.ajustes

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

/**
 * Cómo quedó la vista del visor la última vez que se cerró.
 *
 * Viaja en crudo —dos cadenas y un sí o un no— por lo mismo que el tema: aquí abajo no
 * se sabe qué es «ajustar a la página» ni cuántos grados es un giro. Y viaja junta
 * porque se elige junta: son tres caras del mismo ajuste, y separarlas obligaría a leer
 * el fichero tres veces para contestar una sola pregunta.
 */
data class VistaGuardada(
    val ajuste: String? = null,
    val doblePagina: Boolean? = null,
    val giro: String? = null,
)

/**
 * Lo que el usuario elige sobre la interfaz y tiene que seguir eligido mañana.
 *
 * Guarda cadenas y no enumerados a propósito: aquí abajo no se sabe qué es un tema.
 * Quien conoce los valores es la capa de interfaz, que traduce en su borde; un valor
 * desconocido —una versión vieja, un fichero tocado a mano— se lee como «no hay
 * preferencia» en vez de tumbar el arranque.
 */
class AjustesDeInterfaz(
    private val contexto: Context,
) {
    val tema: Flow<String?> = contexto.almacen.data.map { it[CLAVE_TEMA] }

    suspend fun elegirTema(valor: String) {
        contexto.almacen.edit { ajustes -> ajustes[CLAVE_TEMA] = valor }
    }

    /**
     * El tema guardado, ahora mismo y bloqueando.
     *
     * Es la única lectura síncrona de la aplicación y tiene motivo: el tema hay que
     * saberlo **antes** de pintar el primer fotograma. Leerlo de forma asíncrona
     * pintaría una pantalla clara que se vuelve oscura a la vista del usuario, que es
     * justo lo que esta fase persigue. Son unos milisegundos de un fichero diminuto,
     * en el arranque de la aplicación y nunca dentro de una pantalla.
     */
    fun temaGuardado(): String? = runBlocking { tema.first() }

    /**
     * La vista del visor, y esta sí sin bloquear.
     *
     * No hace falta el trato de excepción que recibe el tema: el visor no es el primer
     * fotograma de la aplicación, y cuando alguien abre un documento este fichero ya se
     * leyó una vez y está en memoria. La elección llega mucho antes que el primer
     * bitmap de página, que es lo único que se vería cambiar.
     */
    val vista: Flow<VistaGuardada> =
        contexto.almacen.data.map { ajustes ->
            VistaGuardada(
                ajuste = ajustes[CLAVE_AJUSTE_DE_VISTA],
                doblePagina = ajustes[CLAVE_DOBLE_PAGINA],
                giro = ajustes[CLAVE_GIRO_DE_VISTA],
            )
        }

    /**
     * Guarda las tres cosas de una vez, en una sola escritura.
     *
     * Cambiar una sola de ellas y arrastrar las otras dos parece redundante, pero es lo
     * que hace que el fichero no pueda quedarse a medias: o está la vista entera, o está
     * la de antes.
     */
    suspend fun elegirVista(
        ajuste: String,
        doblePagina: Boolean,
        giro: String,
    ) {
        contexto.almacen.edit { ajustes ->
            ajustes[CLAVE_AJUSTE_DE_VISTA] = ajuste
            ajustes[CLAVE_DOBLE_PAGINA] = doblePagina
            ajustes[CLAVE_GIRO_DE_VISTA] = giro
        }
    }

    private companion object {
        val CLAVE_TEMA = stringPreferencesKey("tema")
        val CLAVE_AJUSTE_DE_VISTA = stringPreferencesKey("vista-ajuste")
        val CLAVE_DOBLE_PAGINA = booleanPreferencesKey("vista-doble-pagina")
        val CLAVE_GIRO_DE_VISTA = stringPreferencesKey("vista-giro")
    }
}

private val Context.almacen by preferencesDataStore(name = "ajustes-interfaz")
