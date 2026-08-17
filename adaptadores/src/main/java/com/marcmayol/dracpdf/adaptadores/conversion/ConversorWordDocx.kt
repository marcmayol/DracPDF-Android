package com.marcmayol.dracpdf.adaptadores.conversion

import android.content.ContentResolver
import android.net.Uri
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ConversorWord
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * El puerto de Word puesto sobre los ficheros de Android.
 *
 * Aquí no hay nada de OOXML: eso es de [EscritorDocx] y [LectorDocx], que trabajan con
 * flujos y no saben qué es un `content://`. Lo único que se resuelve en esta clase es de
 * dónde se lee y dónde se deja lo escrito, que es lo que cambia entre un fichero privado
 * y uno del sistema.
 *
 * **Al escribir se pasa por un temporal**, como el resto de herramientas: el destino
 * suele ser un `content://` y un `.docx` a medias es un ZIP roto. **Al leer no**: un ZIP
 * se recorre en secuencia y no hace falta copiar el fichero entero a la caché para eso,
 * al contrario que la firma, que necesita acceso aleatorio y sí lo copia.
 */
class ConversorWordDocx(
    private val resolver: ContentResolver,
    private val salidas: SalidasDeHerramienta,
    private val carpetaTemporal: File,
    private val escritor: EscritorDocx = EscritorDocx(),
    private val lector: LectorDocx = LectorDocx(),
) : ConversorWord {
    override fun escribir(
        documento: DocumentoEstructurado,
        destino: OrigenDocumento,
    ) {
        carpetaTemporal.mkdirs()
        val temporal = File.createTempFile("word-", ".docx", carpetaTemporal)
        try {
            temporal.outputStream().use { salida -> escritor.escribir(documento, salida) }
            salidas.volcar(temporal, destino)
        } finally {
            temporal.delete()
        }
    }

    override fun leer(origen: OrigenDocumento): DocumentoEstructurado = flujoDe(origen).use(lector::leer)

    private fun flujoDe(origen: OrigenDocumento): InputStream =
        when (origen) {
            is OrigenDocumento.Privado -> File(origen.identificador).inputStream()
            is OrigenDocumento.Externo ->
                resolver.openInputStream(Uri.parse(origen.identificador))
                    ?: throw IOException("No se ha podido leer ${origen.identificador}")
        }
}
