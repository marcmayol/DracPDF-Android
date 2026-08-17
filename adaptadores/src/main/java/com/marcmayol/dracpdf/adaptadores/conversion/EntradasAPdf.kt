package com.marcmayol.dracpdf.adaptadores.conversion

import android.content.ContentResolver
import android.net.Uri
import com.artifex.mupdf.fitz.PDFDocument
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ConversorEntradas
import com.marcmayol.dracpdf.dominio.puertos.EntradaAConvertir
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import com.marcmayol.dracpdf.dominio.puertos.TipoDeEntrada
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Lo que ya está en el teléfono, metido en un PDF nuevo.
 *
 * **Un solo documento y un solo recorrido**, aunque las entradas sean de clases
 * distintas. Es la razón de que esto exista en vez de dos adaptadores independientes:
 * las páginas se van añadiendo a un mismo [PDFDocument] abierto, y ni el dominio ni el
 * caso de uso pueden sostener ese documento en la mano —no saben qué es—. Aquí sí, y por
 * eso quien reparte el trabajo entre [ImagenesAPdf] y [TextoAPdf] es este objeto.
 *
 * **Se monta en la caché y se vuelca al final**, igual que la caja de herramientas: el
 * motor guarda a una ruta del sistema de ficheros y el destino suele ser un `content://`
 * del SAF, así que el temporal hace falta de todos modos; de paso da la atomicidad que el
 * dominio exige, porque el destino no se toca hasta que el documento está entero.
 */
class EntradasAPdf(
    private val resolver: ContentResolver,
    private val salidas: SalidasDeHerramienta,
    private val carpetaTemporal: File,
) : ConversorEntradas {
    private val imagenes = ImagenesAPdf()

    override fun aPdf(
        entradas: List<EntradaAConvertir>,
        destino: OrigenDocumento,
        progreso: Progreso,
    ): Int {
        require(entradas.isNotEmpty()) { "No se ha elegido nada que convertir" }
        carpetaTemporal.mkdirs()
        val temporal = File.createTempFile("entrante-", ".pdf", carpetaTemporal)
        return try {
            val paginas = montar(entradas, temporal, progreso)
            // Cero páginas es un documento que ningún lector abre. Antes que escribir eso
            // donde el usuario esperaba el suyo, no se escribe nada y se dice.
            if (paginas > 0) salidas.volcar(temporal, destino)
            paginas
        } finally {
            temporal.delete()
        }
    }

    /**
     * Recorre las entradas añadiendo páginas y guarda el resultado en [temporal].
     *
     * Cancelar a mitad devuelve 0 y no guarda nada: media conversión no es un documento
     * a medias, es un documento equivocado, y el usuario no tendría manera de saber que
     * le faltan tres hojas.
     */
    private fun montar(
        entradas: List<EntradaAConvertir>,
        temporal: File,
        progreso: Progreso,
    ): Int {
        val pdf = PDFDocument()
        val texto = TextoAPdf(pdf)
        return try {
            var paginas = 0
            for ((hechas, entrada) in entradas.withIndex()) {
                paginas += anadir(pdf, texto, entrada)
                if (!progreso.paso(hechas + 1, entradas.size)) return 0
            }
            if (paginas > 0) pdf.save(temporal.absolutePath, OPCIONES_LIMPIO)
            paginas
        } finally {
            texto.cerrar()
            pdf.destroy()
        }
    }

    private fun anadir(
        pdf: PDFDocument,
        texto: TextoAPdf,
        entrada: EntradaAConvertir,
    ): Int {
        val bytes = resolver.bytesDe(entrada.origen)
        return when (entrada.tipo) {
            TipoDeEntrada.IMAGEN -> imagenes.anadirPagina(pdf, bytes)
            // Se lee como UTF-8 y punto. Adivinar codificaciones a partir de los bytes es
            // una ciencia inexacta que acierta poco, y cualquier cosa escrita en este
            // siglo —Markdown, HTML, notas de móvil— viene en UTF-8.
            else -> texto.anadir(LectorDeTextoEntrante.leer(String(bytes, Charsets.UTF_8), entrada.tipo))
        }
    }

    private companion object {
        /** Sin basura y con los objetos repetidos unificados, como el resto de la casa. */
        const val OPCIONES_LIMPIO = "compress,garbage=deduplicate"
    }
}

/**
 * Los bytes de un origen, venga de donde venga.
 *
 * Se lee entero a memoria a propósito. Una imagen o un `.md` caben de sobra —los cuatro
 * megas de una foto no son nada— y a cambio se evita lo que sí duele: un flujo del SAF
 * abierto mientras el motor construye el documento, que es justo cuando el proveedor
 * puede decidir cerrarlo.
 */
private fun ContentResolver.bytesDe(origen: OrigenDocumento): ByteArray =
    when (origen) {
        is OrigenDocumento.Privado -> File(origen.identificador).readBytes()
        is OrigenDocumento.Externo ->
            openInputStream(Uri.parse(origen.identificador))?.use { entrada ->
                ByteArrayOutputStream().use { salida ->
                    entrada.copyTo(salida)
                    salida.toByteArray()
                }
            } ?: throw IOException("El sistema no deja leer ${origen.identificador}")
    }

/**
 * Un número como lo escribe un PDF.
 *
 * El `Locale.ROOT` no es una precaución teórica: en el teléfono del usuario, que está en
 * español, `"%.3f"` con el idioma del sistema escribe «595,000», y una coma dentro de un
 * flujo de contenido convierte la página en dos operandos que el lector no entiende. El
 * documento sale en blanco y no hay error en ninguna parte.
 */
internal fun Float.enPdf(): String = String.format(Locale.ROOT, "%.3f", this)

/**
 * La hoja sobre la que se compone todo lo que entra.
 *
 * A4 y no «el tamaño de la imagen»: lo que se produce aquí es un documento para leer,
 * imprimir y mandar, y un PDF cuyas páginas midan cada una una cosa distinta es un
 * documento que ninguna impresora sabe sacar de una vez. La medida va en puntos, que es
 * la unidad del formato: 1/72 de pulgada.
 */
internal object HojaA4 {
    const val ANCHO = 595f
    const val ALTO = 842f
}
