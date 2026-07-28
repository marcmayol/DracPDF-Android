package com.marcmayol.dracpdf.dominio.modelo

/**
 * Identificador de sesión de un documento abierto, como en el escritorio: no es la
 * ruta ni el URI, porque el mismo fichero puede reabrirse tras firmarlo y sigue
 * siendo el mismo documento para quien lo está mirando.
 */
@JvmInline
value class IdDocumento(
    val valor: String,
)

/**
 * De dónde sale un documento. El dominio no sabe qué es un `content://` ni un
 * `ParcelFileDescriptor`: sólo que hay un origen con un identificador opaco que el
 * adaptador sabrá resolver.
 */
sealed interface OrigenDocumento {
    val identificador: String

    /** Un documento entregado por el sistema (Storage Access Framework o intent). */
    data class Externo(
        override val identificador: String,
        val nombre: String,
    ) : OrigenDocumento

    /** Un fichero del almacenamiento privado de la aplicación. */
    data class Privado(
        override val identificador: String,
        val nombre: String,
    ) : OrigenDocumento
}

/**
 * Marcas de estado de un documento. Son las del escritorio, y significan lo mismo.
 */
enum class Marca {
    /** Hay cambios en memoria que aún no están en el fichero. */
    CAMBIOS_SIN_GUARDAR,

    /**
     * El documento lleva al menos una firma. Se detecta también al abrir, incluidos
     * los documentos firmados por terceros: a partir de ahí, editar se rechaza y se
     * ofrece guardar una copia editable.
     */
    FIRMADO,
}

/** Un documento abierto, tal como lo ve el resto de la aplicación. */
data class DocumentoAbierto(
    val id: IdDocumento,
    val nombre: String,
    val paginas: Int,
    val marcas: Set<Marca> = emptySet(),
) {
    init {
        require(paginas > 0) { "Un documento abierto tiene al menos una página, y este dice tener $paginas" }
    }

    val estaFirmado: Boolean get() = Marca.FIRMADO in marcas
    val tieneCambiosSinGuardar: Boolean get() = Marca.CAMBIOS_SIN_GUARDAR in marcas
}

/** Tamaño de una página en puntos PDF (1/72 de pulgada), antes de aplicar escala. */
data class TamanoPt(
    val ancho: Float,
    val alto: Float,
) {
    init {
        require(ancho > 0f && alto > 0f) { "Una página mide más que cero: $ancho x $alto" }
    }
}

/**
 * Una página ya rasterizada. El dominio no conoce `Bitmap`: entrega los píxeles y
 * sus dimensiones, y es el adaptador quien los convierte en lo que la plataforma
 * sepa dibujar.
 */
class PaginaRenderizada(
    val pagina: Int,
    val escala: Float,
    val ancho: Int,
    val alto: Int,
    val pixeles: ByteArray,
) {
    /** Lo que ocupa de verdad, que es lo que la caché tiene que contabilizar. */
    val bytes: Int get() = pixeles.size
}
