package com.marcmayol.dracpdf.adaptadores.recientes

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AlmacenRecientes
import com.marcmayol.dracpdf.dominio.puertos.DocumentoReciente
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Los recientes, en un JSON del almacenamiento privado.
 *
 * Un fichero y no DataStore ni una base de datos: son treinta líneas de texto que se
 * leen enteras al arrancar y se escriben enteras al cambiar. Montar una tabla para
 * esto sería más código, más dependencias y el mismo resultado.
 *
 * **La escritura es atómica**, como la de las firmas: se escribe al lado y se
 * renombra encima. Un teléfono al que se le acaba la batería a mitad de escritura no
 * puede dejar la lista de recientes rota, porque es lo primero que se lee al abrir la
 * aplicación.
 */
class RecientesEnFichero(
    private val fichero: File,
    /** Cuántos se guardan. Más allá de esto, un reciente ya no es reciente. */
    private val tope: Int = TOPE_POR_DEFECTO,
) : AlmacenRecientes {
    override fun listar(): List<DocumentoReciente> = leer().sortedByDescending { it.visto }

    override fun anotar(reciente: DocumentoReciente) {
        // El mismo documento no aparece dos veces: se compara por identificador, que es
        // lo que de verdad lo distingue. Dos ficheros pueden llamarse igual.
        val resto = leer().filterNot { it.origen.identificador == reciente.origen.identificador }
        escribir((listOf(reciente) + resto).sortedByDescending { it.visto }.take(tope))
    }

    override fun anotarPosicion(
        origen: OrigenDocumento,
        pagina: Int,
        zoom: Float,
    ) {
        val guardados = leer()
        val actual = guardados.firstOrNull { it.origen.identificador == origen.identificador } ?: return
        escribir(
            guardados.map { candidato ->
                if (candidato.origen.identificador == origen.identificador) {
                    actual.copy(pagina = pagina, zoom = zoom, visto = System.currentTimeMillis())
                } else {
                    candidato
                }
            },
        )
    }

    override fun olvidar(origen: OrigenDocumento) {
        escribir(leer().filterNot { it.origen.identificador == origen.identificador })
    }

    override fun olvidarTodos() {
        escribir(emptyList())
    }

    /**
     * Lee la lista. Un fichero que no existe todavía —la primera vez— y uno corrupto
     * se tratan igual: no hay recientes. Perder la lista es una molestia; caerse al
     * arrancar por un JSON a medias, un fallo.
     */
    private fun leer(): List<DocumentoReciente> {
        if (!fichero.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(fichero.readText())
            (0 until array.length()).mapNotNull { indice -> aReciente(array.getJSONObject(indice)) }
        }.getOrDefault(emptyList())
    }

    private fun aReciente(json: JSONObject): DocumentoReciente? =
        runCatching {
            val identificador = json.getString(CLAVE_ID)
            val nombre = json.getString(CLAVE_NOMBRE)
            DocumentoReciente(
                origen =
                    if (json.optBoolean(CLAVE_PRIVADO, false)) {
                        OrigenDocumento.Privado(identificador, nombre)
                    } else {
                        OrigenDocumento.Externo(identificador, nombre)
                    },
                nombre = nombre,
                visto = json.getLong(CLAVE_VISTO),
                pagina = json.optInt(CLAVE_PAGINA, 0),
                zoom = json.optDouble(CLAVE_ZOOM, 1.0).toFloat(),
                permisoPersistido = json.optBoolean(CLAVE_PERMISO, false),
            )
        }.getOrNull()

    private fun escribir(recientes: List<DocumentoReciente>) {
        val array = JSONArray()
        recientes.take(tope).forEach { reciente ->
            array.put(
                JSONObject().apply {
                    put(CLAVE_ID, reciente.origen.identificador)
                    put(CLAVE_NOMBRE, reciente.nombre)
                    put(CLAVE_VISTO, reciente.visto)
                    put(CLAVE_PAGINA, reciente.pagina)
                    put(CLAVE_ZOOM, reciente.zoom.toDouble())
                    put(CLAVE_PERMISO, reciente.permisoPersistido)
                    put(CLAVE_PRIVADO, reciente.origen is OrigenDocumento.Privado)
                },
            )
        }

        fichero.parentFile?.mkdirs()
        val aMedias = File(fichero.parentFile, "${fichero.name}.nuevo")
        aMedias.writeText(array.toString())
        if (!aMedias.renameTo(fichero)) {
            // Algunos sistemas de ficheros no renombran encima de uno existente.
            fichero.delete()
            aMedias.renameTo(fichero)
        }
    }

    private companion object {
        const val TOPE_POR_DEFECTO = 20
        const val CLAVE_ID = "id"
        const val CLAVE_NOMBRE = "nombre"
        const val CLAVE_VISTO = "visto"
        const val CLAVE_PAGINA = "pagina"
        const val CLAVE_ZOOM = "zoom"
        const val CLAVE_PERMISO = "permiso"
        const val CLAVE_PRIVADO = "privado"
    }
}
