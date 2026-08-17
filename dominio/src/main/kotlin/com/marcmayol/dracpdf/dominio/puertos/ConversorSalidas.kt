package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Formatos de documento a los que sabe salir un [DocumentoEstructurado].
 *
 * Están juntos en un solo enumerado porque **son la misma conversión escrita de cinco
 * maneras**: la deducción de qué es un título y qué un párrafo ocurre una vez, antes, y
 * aquí sólo cambian las etiquetas. Ofrecerlos como cinco operaciones distintas habría
 * significado cinco caminos que pueden desviarse entre sí, que es justo lo que el
 * modelo de estructura existe para evitar.
 *
 * Todos pierden la maquetación y todos se etiquetan «reformateado» en la interfaz: lo
 * que se promete es el contenido, no el diseño.
 */
enum class FormatoSalida(
    val extension: String,
) {
    HTML("html"),
    MARKDOWN("md"),
    TEXTO("txt"),
    ODT("odt"),
    RTF("rtf"),
}

/**
 * Formatos a los que salen las tablas.
 *
 * Van aparte de [FormatoSalida] porque **no producen la misma cantidad de ficheros**:
 * un documento se convierte en un fichero, y unas tablas en uno por tabla (CSV) o en
 * uno con una hoja por tabla (XLSX). Meterlos en el mismo enumerado obligaría a que la
 * conversión de un documento devolviera una lista para que las tablas cupieran.
 */
enum class FormatoTabla(
    val extension: String,
) {
    CSV("csv"),
    XLSX("xlsx"),
}

/**
 * Cuántas tablas hay y en qué páginas están, **antes** de convertir nada.
 *
 * Existe porque detectar tablas en un PDF es adivinarlas, y el usuario tiene derecho a
 * saber qué se ha adivinado antes de aceptar el resultado. El escritorio aprendió que
 * entregar tres CSV sin avisar —o ninguno, en silencio— se lee como un fallo de la
 * aplicación aunque el documento simplemente no tuviera tablas.
 */
data class RecuentoDeTablas(
    /** Una entrada por tabla encontrada, con la página —base cero— en la que está. */
    val paginas: List<Int>,
    /** Si alguna se dedujo por alineación en vez de por líneas dibujadas. */
    val algunaAproximada: Boolean = paginas.isNotEmpty(),
) {
    val total: Int get() = paginas.size
    val hayAlguna: Boolean get() = paginas.isNotEmpty()

    /** Las páginas donde hay tablas, sin repetir y en orden, para poder enseñarlas. */
    val paginasDistintas: List<Int> get() = paginas.distinct().sorted()
}

/**
 * Lee un documento abierto como contenido: títulos, párrafos y tablas.
 *
 * Está separado de [ConversorSalidas] a propósito, y no por pulcritud: leer necesita el
 * motor y el hilo del documento, y escribir no necesita ni una cosa ni la otra. Con los
 * dos en el mismo puerto, cada escritor nuevo —Word, el día que toque— arrastraría
 * consigo la dependencia de MuPDF, y probar el ODT exigiría abrir un PDF.
 *
 * Trabaja sobre un **documento abierto** y no sobre un fichero del disco, al revés que
 * [HerramientasPdf]: aquí se convierte lo que el usuario está mirando, y el motor ya lo
 * tiene abierto en su hilo. Volver a abrirlo por la ruta sería un segundo `Document`
 * sobre el mismo fichero, que es exactamente lo que las sesiones existen para evitar.
 */
interface LectorDeEstructura {
    /**
     * Recorre el documento entero deduciendo su estructura.
     *
     * El progreso va por páginas y se puede cancelar; cancelar devuelve lo leído hasta
     * ese momento, que es honesto: no hay nada escrito todavía que pueda quedar a
     * medias.
     */
    fun estructuraDe(
        id: IdDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): DocumentoEstructurado
}

/**
 * Escribe una estructura ya leída en los formatos que el usuario elige.
 *
 * **No decide si hay que escribir.** Un documento sin texto o sin tablas llega aquí
 * igual que cualquier otro, y quien tiene que negarse es el caso de uso: esa decisión
 * es una regla —«un escaneado no produce ficheros vacíos, se avisa»— y las reglas no
 * viven en el adaptador, donde cada formato podría interpretarlas a su manera.
 */
interface ConversorSalidas {
    /**
     * Escribe el documento en [carpeta] con el nombre [nombreBase] más su extensión.
     *
     * @return el origen del fichero escrito, que puede no llamarse como se pidió: si ya
     *   había uno igual, el sistema añade un sufijo.
     */
    fun escribir(
        documento: DocumentoEstructurado,
        formato: FormatoSalida,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ): OrigenDocumento

    /**
     * Escribe las tablas de [documento] en [carpeta].
     *
     * @return los ficheros escritos: uno por tabla en CSV —un CSV con varias tablas
     *   dentro no es un CSV que nadie sepa abrir— y uno solo con una hoja por tabla en
     *   XLSX, que sí sabe llevar varias.
     */
    fun escribirTablas(
        tablas: List<BloqueDeTexto.Tabla>,
        formato: FormatoTabla,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ): List<OrigenDocumento>
}
