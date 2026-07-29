package com.marcmayol.dracpdf.adaptadores.firmas

import com.marcmayol.dracpdf.dominio.modelo.Firma
import com.marcmayol.dracpdf.dominio.modelo.IdFirma
import com.marcmayol.dracpdf.dominio.puertos.AlmacenFirmas
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * La biblioteca de firmas, en el almacenamiento privado: un PNG por firma y un JSON
 * al lado con su ficha.
 *
 * Dos ficheros y no uno porque son dos cosas con vidas distintas: los píxeles no
 * cambian nunca y la ficha sí —renombrar la firma—, y meter los metadatos dentro del
 * PNG obligaría a reescribir la imagen entera para cambiarle el nombre.
 *
 * **El alta es atómica y en este orden**: primero el PNG completo, después el JSON,
 * cada uno escrito aparte y renombrado al final. El JSON es el que da fe: mientras
 * no está, la firma no existe. Si el proceso muere a mitad —en un móvil pasa—, lo
 * que queda es un PNG huérfano que nadie mira, y no una firma que se dibuja cortada.
 */
class AlmacenFirmasFichero(
    private val carpeta: File,
) : AlmacenFirmas {
    override fun guardar(
        png: ByteArray,
        anchoPx: Int,
        altoPx: Int,
        nombre: String,
    ): Firma {
        require(png.isNotEmpty()) { "Una firma sin píxeles no es una firma" }
        carpeta.mkdirs()

        val id = IdFirma(UUID.randomUUID().toString())
        val firma =
            Firma(
                id = id,
                nombre = nombre.ifBlank { NOMBRE_POR_DEFECTO },
                creadaEn = System.currentTimeMillis(),
                anchoPx = anchoPx,
                altoPx = altoPx,
            )

        escribirAtomico(imagenDe(id), png)
        escribirAtomico(fichaDe(id), fichaJson(firma).toString().toByteArray())
        return firma
    }

    override fun listar(): List<Firma> =
        carpeta
            .listFiles { fichero -> fichero.name.endsWith(SUFIJO_FICHA) }
            .orEmpty()
            .mapNotNull { ficha -> leerFicha(ficha) }
            // De la más reciente a la más antigua: la que se acaba de dibujar es casi
            // siempre la que se va a usar.
            .sortedByDescending { it.creadaEn }

    override fun leer(id: IdFirma): ByteArray {
        val imagen = imagenDe(id)
        if (!imagen.exists()) throw IOException("La firma ${id.valor} ya no está")
        return imagen.readBytes()
    }

    override fun borrar(id: IdFirma) {
        // La ficha primero: en cuanto se va, la firma deja de existir para todos, y el
        // PNG que quede detrás es basura que se ignora sola.
        fichaDe(id).delete()
        imagenDe(id).delete()
    }

    /**
     * Lee una ficha, o `null` si no se puede.
     *
     * Un JSON corrupto, o uno cuyo PNG ha desaparecido, se ignoran en silencio: la
     * biblioteca de firmas no es sitio para dar errores por algo que el usuario no ha
     * hecho y no puede arreglar. Lo que no se puede dibujar, no se enseña.
     */
    private fun leerFicha(ficha: File): Firma? =
        runCatching {
            val json = JSONObject(ficha.readText())
            val id = IdFirma(json.getString(CLAVE_ID))
            if (!imagenDe(id).exists()) return null
            Firma(
                id = id,
                nombre = json.getString(CLAVE_NOMBRE),
                creadaEn = json.getLong(CLAVE_CREADA),
                anchoPx = json.getInt(CLAVE_ANCHO),
                altoPx = json.getInt(CLAVE_ALTO),
            )
        }.getOrNull()

    private fun fichaJson(firma: Firma): JSONObject =
        JSONObject().apply {
            put(CLAVE_ID, firma.id.valor)
            put(CLAVE_NOMBRE, firma.nombre)
            put(CLAVE_CREADA, firma.creadaEn)
            put(CLAVE_ANCHO, firma.anchoPx)
            put(CLAVE_ALTO, firma.altoPx)
        }

    /**
     * Escribe a un fichero temporal y lo renombra encima.
     *
     * El renombrado dentro del mismo sistema de ficheros es la única operación que el
     * sistema garantiza entera: o está el fichero viejo o está el nuevo, nunca medio
     * escrito.
     */
    private fun escribirAtomico(
        destino: File,
        contenido: ByteArray,
    ) {
        val temporal = File(destino.parentFile, "${destino.name}$SUFIJO_TEMPORAL")
        temporal.writeBytes(contenido)
        if (!temporal.renameTo(destino)) {
            temporal.delete()
            throw IOException("No se ha podido guardar ${destino.name}")
        }
    }

    private fun imagenDe(id: IdFirma) = File(carpeta, "${id.valor}$SUFIJO_IMAGEN")

    private fun fichaDe(id: IdFirma) = File(carpeta, "${id.valor}$SUFIJO_FICHA")

    private companion object {
        const val SUFIJO_IMAGEN = ".png"
        const val SUFIJO_FICHA = ".json"
        const val SUFIJO_TEMPORAL = ".parcial"
        const val NOMBRE_POR_DEFECTO = "Firma"

        const val CLAVE_ID = "id"
        const val CLAVE_NOMBRE = "nombre"
        const val CLAVE_CREADA = "creadaEn"
        const val CLAVE_ANCHO = "anchoPx"
        const val CLAVE_ALTO = "altoPx"
    }
}
