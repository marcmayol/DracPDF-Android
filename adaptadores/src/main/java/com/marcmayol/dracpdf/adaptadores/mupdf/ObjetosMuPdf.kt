package com.marcmayol.dracpdf.adaptadores.mupdf

import com.artifex.mupdf.fitz.PDFAnnotation
import com.artifex.mupdf.fitz.PDFObject

/**
 * Lleva la cuenta de los objetos nativos que se crean al mirar dentro de un PDF, y
 * los suelta todos al terminar.
 *
 * **Esto no es higiene, es evitar un SIGSEGV.** Cada `PDFObject` y cada `PDFWidget`
 * de Java sostiene memoria de MuPDF y trae un `finalize()` que la suelta. Si se deja
 * que los recoja el recolector de basura, ese `finalize()` se ejecuta en el hilo del
 * recolector —`HeapTaskDaemon`—, que no es el hilo del documento, y encima puede
 * ocurrir después de que el documento se haya cerrado: entonces suelta memoria que ya
 * no existe y el proceso se cae sin excepción que capturar, igual que si se leyera el
 * documento desde dos hilos.
 *
 * Por eso todo lo que se pide se anota, y se suelta antes de salir del hilo del
 * documento, que es donde se creó.
 */
internal class ObjetosMuPdf {
    private val creados = mutableListOf<Any>()

    /** Anota un objeto para soltarlo al final. Devuelve el mismo que recibe. */
    fun <T : PDFObject?> anotar(objeto: T): T {
        if (objeto != null) creados.add(objeto)
        return objeto
    }

    fun anotarAnotacion(objeto: PDFAnnotation?): PDFAnnotation? {
        if (objeto != null) creados.add(objeto)
        return objeto
    }

    /** Un valor del diccionario, ya anotado para soltarlo. */
    fun clave(
        objeto: PDFObject?,
        nombre: String,
    ): PDFObject? = anotar(objeto?.get(nombre))

    fun soltar() {
        creados.forEach { objeto ->
            runCatching {
                when (objeto) {
                    is PDFAnnotation -> objeto.destroy()
                    is PDFObject -> objeto.destroy()
                    else -> Unit
                }
            }
        }
        creados.clear()
    }
}

/** Hace algo con los objetos de un documento y los suelta pase lo que pase. */
internal inline fun <T> conObjetos(bloque: (ObjetosMuPdf) -> T): T {
    val objetos = ObjetosMuPdf()
    return try {
        bloque(objetos)
    } finally {
        objetos.soltar()
    }
}
