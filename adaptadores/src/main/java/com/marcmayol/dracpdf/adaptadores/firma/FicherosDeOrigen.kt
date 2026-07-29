package com.marcmayol.dracpdf.adaptadores.firma

import android.content.ContentResolver
import android.net.Uri
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import java.io.File
import java.io.IOException

/**
 * Convierte un origen del dominio en un fichero de verdad, porque PDFBox necesita
 * uno.
 *
 * Es la diferencia con MuPDF, y no es un capricho de PDFBox: firmar exige recorrer el
 * documento entero varias veces y calcular resúmenes sobre rangos de bytes concretos,
 * y para eso hace falta acceso aleatorio de principio a fin. MuPDF se apaña con un
 * flujo posicionable; PDFBox, no.
 *
 * Un `content://` se **copia** a la caché privada. Es el precio de firmar por SAF:
 * duplica el fichero durante la operación. Se paga sólo al firmar —no al abrir ni al
 * leer, que siguen sin copiar nada— y no hay alternativa: sin fichero no hay firma.
 */
class FicherosDeOrigen(
    private val resolver: ContentResolver,
    private val carpetaDeCopias: File,
) {
    fun comoFichero(origen: OrigenDocumento): File =
        when (origen) {
            is OrigenDocumento.Privado -> File(origen.identificador)
            is OrigenDocumento.Externo -> copiar(origen)
        }

    private fun copiar(origen: OrigenDocumento.Externo): File {
        carpetaDeCopias.mkdirs()
        val destino = File(carpetaDeCopias, "para-firmar-${origen.identificador.hashCode()}.pdf")
        resolver.openInputStream(Uri.parse(origen.identificador))?.use { entrada ->
            destino.outputStream().use(entrada::copyTo)
        } ?: throw IOException("No se ha podido leer ${origen.identificador}")
        return destino
    }
}
