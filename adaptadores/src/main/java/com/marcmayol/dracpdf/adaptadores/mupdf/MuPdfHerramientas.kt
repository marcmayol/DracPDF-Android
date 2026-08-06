package com.marcmayol.dracpdf.adaptadores.mupdf

import android.graphics.Bitmap
import com.artifex.mupdf.fitz.ColorSpace
import com.artifex.mupdf.fitz.Document
import com.artifex.mupdf.fitz.Matrix
import com.artifex.mupdf.fitz.PDFDocument
import com.artifex.mupdf.fitz.PDFGraftMap
import com.artifex.mupdf.fitz.StructuredText
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.FormatoImagen
import com.marcmayol.dracpdf.dominio.puertos.HerramientasPdf
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import com.marcmayol.dracpdf.dominio.puertos.Reduccion
import java.io.File

/**
 * La caja de herramientas sobre MuPDF.
 *
 * **Cada operación abre sus documentos y los cierra**, sin pasar por las sesiones del
 * visor. No es duplicar trabajo: las sesiones existen para el documento que se está
 * mirando, con su hilo vivo mientras dure; aquí se trabaja con ficheros que nadie mira
 * —los cinco que se van a unir— y meterlos en el registro dejaría medio catálogo de
 * documentos abiertos por cada operación. Cada llamada corre en el hilo de quien la
 * pide, que es un hilo de trabajo, y no comparte estado con nadie.
 *
 * **Todo se escribe en la caché y se copia al destino al final.** MuPDF guarda a una
 * ruta del sistema de ficheros y el destino suele ser un `content://` del SAF, así que
 * el temporal es obligatorio de todos modos; de paso da la atomicidad que el dominio
 * exige, porque el destino no se toca hasta que el resultado está entero.
 */
class MuPdfHerramientas(
    private val ficheros: FicherosDeOrigen,
    private val salidas: SalidasDeHerramienta,
    private val carpetaTemporal: File,
) : HerramientasPdf {
    override fun paginasDe(origen: OrigenDocumento): Int = conDocumento(origen) { documento -> documento.countPages() }

    override fun unir(
        origenes: List<OrigenDocumento>,
        destino: OrigenDocumento,
        progreso: Progreso,
    ) {
        require(origenes.size >= 2) { "Unir uno solo no es unir" }

        escribiendoEn(destino) { temporal ->
            val resultado = PDFDocument()
            // Las fuentes se abren todas y **no se cierran hasta después de guardar**.
            // MuPDF lee del fichero según lo necesita: injertar una página no copia sus
            // flujos en el acto, así que cerrar el origen antes del `save` deja el
            // resultado con las páginas puestas y el contenido en blanco de la segunda
            // en adelante. Es el mismo motivo por el que el guardado va aquí dentro.
            val fuentes = mutableListOf<Document>()
            try {
                var pegadas = 0
                var cancelado = false
                val total = origenes.sumOf { paginasDe(it) }

                for (origen in origenes) {
                    val documento = Document.openDocument(ficheros.comoFichero(origen).absolutePath)
                    fuentes += documento
                    if (documento.needsPassword()) throw ErrorDocumento.NecesitaContrasena(origen)
                    val fuente = documento as? PDFDocument ?: throw ErrorDocumento.NoSePuedeLeer(origen)

                    conInjerto(resultado) { injerto ->
                        for (pagina in 0 until fuente.countPages()) {
                            injerto.graftPage(pegadas, fuente, pagina)
                            pegadas++
                            if (!progreso.paso(pegadas, total)) {
                                cancelado = true
                                break
                            }
                        }
                    }
                    if (cancelado) break
                }

                if (!cancelado) resultado.save(temporal.absolutePath, OPCIONES_LIMPIO)
                !cancelado
            } finally {
                resultado.destroy()
                fuentes.forEach { it.destroy() }
            }
        }
    }

    override fun reorganizar(
        origen: OrigenDocumento,
        paginas: List<PaginaOrdenada>,
        destino: OrigenDocumento,
        progreso: Progreso,
    ) {
        escribiendoEn(destino) { temporal ->
            // Se trabaja sobre una copia del propio documento y **se guarda en otro
            // fichero**. Las dos cosas importan: reordenar dentro del documento conserva
            // lo que sus páginas comparten —las fuentes del texto—, y guardar encima del
            // fichero que MuPDF tiene abierto lo vacía por debajo mientras lo lee.
            carpetaTemporal.mkdirs()
            val trabajo = File.createTempFile("reorganizando-", ".pdf", carpetaTemporal)
            try {
                ficheros.comoFichero(origen).copyTo(trabajo, overwrite = true)
                val documento = Document.openDocument(trabajo.absolutePath)
                try {
                    if (documento.needsPassword()) throw ErrorDocumento.NecesitaContrasena(origen)
                    val pdf = documento as? PDFDocument ?: throw ErrorDocumento.NoSePuedeLeer(origen)
                    pdf.rearrangePages(paginas.map { it.original }.toIntArray())
                    paginas.forEachIndexed { puesto, pagina ->
                        if (pagina.giro != 0) girar(pdf, puesto, pagina.giro)
                    }
                    progreso.paso(paginas.size, paginas.size)
                    pdf.save(temporal.absolutePath, OPCIONES_LIMPIO)
                } finally {
                    documento.destroy()
                }
            } finally {
                trabajo.delete()
            }
            true
        }
    }

    /**
     * Copia páginas de un documento a otro **con memoria de lo ya copiado**.
     *
     * El mapa no es una optimización: sin él, cada página se injerta como si el
     * documento de origen se acabara de abrir, y los objetos que las páginas comparten
     * —la fuente del texto, sobre todo— se pierden en todas menos en la primera. El
     * resultado tiene sus páginas y sus marcos, y el texto en blanco a partir de la
     * segunda.
     */
    private fun <T> conInjerto(
        destino: PDFDocument,
        bloque: (PDFGraftMap) -> T,
    ): T {
        val mapa = destino.newPDFGraftMap()
        return try {
            bloque(mapa)
        } finally {
            mapa.destroy()
        }
    }

    /**
     * El giro de una página no es una transformación del contenido: es la entrada
     * `/Rotate` de su diccionario, que dice cómo hay que enseñarla. Se suma a la que ya
     * trajera —una página que venía del revés y se gira 90° acaba a 270°— y se
     * normaliza, porque un `/Rotate` de 450 es legal pero luego nadie lo entiende.
     */
    private fun girar(
        documento: PDFDocument,
        pagina: Int,
        grados: Int,
    ) {
        val hoja = documento.findPage(pagina)
        val actual = hoja.get(CLAVE_ROTACION)?.asInteger() ?: 0
        val nueva = ((actual + grados) % VUELTA_COMPLETA + VUELTA_COMPLETA) % VUELTA_COMPLETA
        hoja.put(CLAVE_ROTACION, documento.newInteger(nueva))
    }

    override fun proteger(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
        contrasenaPropietario: String,
    ) {
        escribiendoEn(destino) { temporal ->
            conPdf(origen) { documento ->
                documento.save(
                    temporal.absolutePath,
                    "compress,user-password=$contrasena,owner-password=$contrasenaPropietario,encrypt=aes-256",
                )
            }
            true
        }
    }

    override fun desproteger(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
    ) {
        escribiendoEn(destino) { temporal ->
            val fichero = ficheros.comoFichero(origen)
            val documento = Document.openDocument(fichero.absolutePath)
            try {
                if (documento.needsPassword() && !documento.authenticatePassword(contrasena)) {
                    throw ErrorDocumento.ContrasenaIncorrecta(origen)
                }
                (documento as PDFDocument).save(temporal.absolutePath, "compress,decrypt")
            } finally {
                documento.destroy()
            }
            true
        }
    }

    override fun comprimir(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso,
    ): Reduccion {
        val fichero = ficheros.comoFichero(origen)
        val antes = fichero.length()
        var despues = antes

        escribiendoEn(destino) { temporal ->
            conPdf(origen) { documento ->
                progreso.paso(0, 1)
                documento.save(temporal.absolutePath, OPCIONES_APRETADO)
            }
            // **Se guarda el menor de los dos.** Apretar un PDF puede dejarlo más gordo
            // —recodificar sus flujos no siempre gana, y con algunos formularios pierde
            // por bastante—, y entregar eso sería lo contrario de lo que se pidió: quien
            // pulsa «Comprimir» quiere el fichero más pequeño en ese destino, no el
            // resultado de un algoritmo. Si no hay mejora se copia el original tal cual y
            // se dice, que es distinto de fingir que ha encogido.
            if (temporal.length() >= antes) fichero.copyTo(temporal, overwrite = true)
            despues = temporal.length()
            progreso.paso(1, 1)
            true
        }
        return Reduccion(antes = antes, despues = despues)
    }

    override fun aImagenes(
        origen: OrigenDocumento,
        paginas: List<Int>,
        carpeta: OrigenDocumento,
        ajustes: AjustesImagen,
        progreso: Progreso,
    ): List<OrigenDocumento> {
        val escritos = mutableListOf<OrigenDocumento>()
        val base = ficheros.comoFichero(origen).nameWithoutExtension

        conDocumento(origen) { documento ->
            paginas.forEachIndexed { hechas, numero ->
                val hoja = documento.loadPage(numero)
                val mapa =
                    try {
                        hoja.toPixmap(Matrix.Scale(ajustes.escala), ColorSpace.DeviceRGB, true)
                    } finally {
                        hoja.destroy()
                    }
                try {
                    val nombre = "$base-${numero + 1}.${extension(ajustes.formato)}"
                    escritos +=
                        salidas.escribirEn(carpeta, nombre) { salida ->
                            val bitmap = Bitmap.createBitmap(mapa.width, mapa.height, Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(mapa.samples))
                                bitmap.compress(compresor(ajustes.formato), ajustes.calidad, salida)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                } finally {
                    mapa.destroy()
                }
                if (!progreso.paso(hechas + 1, paginas.size)) return@conDocumento
            }
        }
        return escritos
    }

    override fun aTexto(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso,
    ): Boolean {
        val texto = StringBuilder()

        conDocumento(origen) { documento ->
            val total = documento.countPages()
            for (numero in 0 until total) {
                val hoja = documento.loadPage(numero)
                try {
                    val estructurado = hoja.toStructuredText()
                    try {
                        texto.append(textoDe(estructurado))
                    } finally {
                        estructurado.destroy()
                    }
                } finally {
                    hoja.destroy()
                }
                if (numero < total - 1) texto.append(SEPARADOR_DE_PAGINA)
                if (!progreso.paso(numero + 1, total)) return@conDocumento
            }
        }

        // Un PDF escaneado devuelve páginas sin una sola letra. Escribir el fichero
        // igualmente dejaría al usuario con un .txt vacío y sin saber por qué.
        if (texto.isBlank()) return false
        escribiendoEn(destino) { temporal ->
            temporal.writeText(texto.toString())
            true
        }
        return true
    }

    private fun textoDe(estructurado: StructuredText): String =
        buildString {
            // Una línea de MuPDF son sus caracteres, uno a uno y cada uno un punto de
            // código: no hay una cadena hecha que pedirle.
            //
            // Los caracteres de control se quedan fuera, y hay uno que da guerra: MuPDF
            // cierra cada página con un salto de página (U+000C). Colado en el fichero
            // se pega al principio de la línea siguiente, invisible al mirarlo, y
            // «Pagina 2 de 4» deja de empezar por «P» para cualquiera que busque dentro
            // del texto. Las páginas se separan aquí con una línea en blanco, que se ve.
            estructurado.blocks?.forEach { bloque ->
                bloque.lines?.forEach { linea ->
                    linea.chars?.forEach { caracter ->
                        if (caracter.c >= PRIMER_CARACTER_VISIBLE || caracter.c == TABULADOR) {
                            appendCodePoint(caracter.c)
                        }
                    }
                    append('\n')
                }
            }
        }

    /** Abre el documento sólo para leerlo, y lo cierra pase lo que pase. */
    private fun <T> conDocumento(
        origen: OrigenDocumento,
        bloque: (Document) -> T,
    ): T {
        val documento = Document.openDocument(ficheros.comoFichero(origen).absolutePath)
        return try {
            if (documento.needsPassword()) throw ErrorDocumento.NecesitaContrasena(origen)
            bloque(documento)
        } finally {
            documento.destroy()
        }
    }

    private fun <T> conPdf(
        origen: OrigenDocumento,
        bloque: (PDFDocument) -> T,
    ): T =
        conDocumento(origen) { documento ->
            val pdf = documento as? PDFDocument ?: throw ErrorDocumento.NoSePuedeLeer(origen)
            bloque(pdf)
        }

    /**
     * Monta el resultado en la caché y sólo entonces lo pone en el destino.
     *
     * Si el bloque devuelve `false` —lo hace quien cancela— el temporal se tira y el
     * destino no se llega a tocar: cancelar a mitad no deja medio PDF donde el usuario
     * esperaba el suyo.
     */
    private fun escribiendoEn(
        destino: OrigenDocumento,
        bloque: (File) -> Boolean,
    ) {
        carpetaTemporal.mkdirs()
        val temporal = File.createTempFile("herramienta-", ".pdf", carpetaTemporal)
        try {
            if (bloque(temporal)) salidas.volcar(temporal, destino)
        } finally {
            temporal.delete()
        }
    }

    private fun extension(formato: FormatoImagen) =
        when (formato) {
            FormatoImagen.PNG -> "png"
            FormatoImagen.JPEG -> "jpg"
            FormatoImagen.WEBP -> "webp"
        }

    private fun compresor(formato: FormatoImagen) =
        when (formato) {
            FormatoImagen.PNG -> Bitmap.CompressFormat.PNG
            FormatoImagen.JPEG -> Bitmap.CompressFormat.JPEG
            // El WEBP con pérdida está deprecado a partir de API 30 en favor de este,
            // que es el mismo formato diciendo explícitamente que pierde.
            FormatoImagen.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
        }

    private companion object {
        /** Sin basura y con los objetos repetidos unificados, que es lo mínimo decente. */
        const val OPCIONES_LIMPIO = "compress,garbage=deduplicate"

        /** Todo lo que MuPDF sabe apretar, imágenes y fuentes incluidas. */
        const val OPCIONES_APRETADO = "compress,compress-images,compress-fonts,garbage=compact"

        const val CLAVE_ROTACION = "Rotate"
        const val VUELTA_COMPLETA = 360

        /**
         * Una línea en blanco entre páginas, y **nada de saltos de página**: el 0x0C se
         * pega invisible al principio de la línea siguiente y estropea cualquier
         * búsqueda dentro del texto exportado.
         */
        const val SEPARADOR_DE_PAGINA = "\n"

        /** Por debajo de esto sólo hay controles; el único que se conserva es el tabulador. */
        const val PRIMER_CARACTER_VISIBLE = 32
        const val TABULADOR = 9
    }
}
