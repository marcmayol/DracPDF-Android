package com.marcmayol.dracpdf.ui.herramientas

import com.marcmayol.dracpdf.ui.iconos.IconosLadon

/**
 * Una herramienta de la rejilla.
 *
 * Los iconos salen del paquete y **no de la maqueta**: la hoja está dibujada con
 * iconos prestados —«Convertir» con el de rotar, «Comprimir» con el de guardar,
 * «Proteger» con el de verificar— porque se maquetó antes de tener el set completo, y
 * `ic_convert`, `ic_compress` e `ic_lock` existen desde entonces.
 */
enum class Herramienta(
    val etiqueta: String,
    val icono: Int,
    val tag: String,
) {
    ORGANIZAR("Organizar\npáginas", IconosLadon.miniaturas, "herramienta_organizar"),
    UNIR("Unir PDF", IconosLadon.unir, "herramienta_unir"),
    DIVIDIR("Dividir", IconosLadon.dividir, "herramienta_dividir"),

    /**
     * **Una sola entrada de conversión**, no dos.
     *
     * El escritorio separaba «Exportar a texto» de «Convertir a Word» y lo corrigió en
     * la 0.4.0 porque los usuarios las leían como operaciones distintas cuando son la
     * misma: sacar el documento a otro formato. Aquí no se reintroduce esa separación,
     * y los casos de uso conservan sus nombres técnicos: la interfaz habla el idioma
     * del usuario y el código el suyo.
     */
    CONVERTIR("Convertir", IconosLadon.convertir, "herramienta_convertir"),
    COMPRIMIR("Comprimir", IconosLadon.comprimir, "herramienta_comprimir"),
    PROTEGER("Proteger", IconosLadon.bloquear, "herramienta_proteger"),
    COMPARTIR("Compartir", IconosLadon.compartir, "herramienta_compartir"),
    IMPRIMIR("Imprimir", IconosLadon.imprimir, "herramienta_imprimir"),
    ANOTACIONES("Anotaciones", IconosLadon.nota, "herramienta_anotaciones"),
}

/** Si la herramienta se puede usar hoy, con este documento. */
fun Herramienta.disponible(documentoFirmado: Boolean): Boolean =
    when (this) {
        // Llegan en fases posteriores y se ven apagadas hasta entonces.
        Herramienta.COMPARTIR, Herramienta.IMPRIMIR, Herramienta.ANOTACIONES -> false
        // Convertir sólo lee: sacar el texto de un contrato firmado para leerlo en otro
        // sitio es legítimo, y negarlo confundiría «no romper la firma» con «no dejar
        // mirar».
        Herramienta.CONVERTIR -> true
        else -> !documentoFirmado
    }
