package com.marcmayol.dracpdf.adaptadores.ajustes

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

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

    private companion object {
        val CLAVE_TEMA = stringPreferencesKey("tema")
    }
}

private val Context.almacen by preferencesDataStore(name = "ajustes-interfaz")
