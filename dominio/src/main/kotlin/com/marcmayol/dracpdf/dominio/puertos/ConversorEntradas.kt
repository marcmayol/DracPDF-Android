package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Lo que el teléfono ya tiene y sabe acabar en un PDF.
 *
 * Es la cara simétrica de [FormatoSalida], y por eso vive bajo el mismo verbo
 * «convertir»: sacar el documento a otro formato y meter otro formato en un documento
 * son la misma operación mirada desde los dos lados, y separarlas en la interfaz sería
 * repetir el error que el escritorio corrigió en la 0.4.0.
 *
 * **No entra el PDF.** Un PDF que se convierte a PDF no se convierte: se une, se
 * organiza o se comprime, y para eso ya hay tres herramientas que lo hacen bien.
 */
enum class TipoDeEntrada(
    val tiposMime: List<String>,
    val extensiones: List<String>,
) {
    /** Una imagen es una página: una foto, una captura, un recibo escaneado por otro. */
    IMAGEN(
        tiposMime = listOf("image/jpeg", "image/png", "image/webp"),
        extensiones = listOf("jpg", "jpeg", "png", "webp"),
    ),

    TEXTO(tiposMime = listOf("text/plain"), extensiones = listOf("txt")),

    /**
     * Markdown y HTML son texto con marcas, y las marcas dicen qué es un título. Es
     * justo lo que un PDF no sabe decir por sí mismo —de ahí la deducción por tamaños de
     * letra al salir—, así que al entrar se aprovecha en vez de tirarlo.
     */
    MARKDOWN(tiposMime = listOf("text/markdown", "text/x-markdown"), extensiones = listOf("md", "markdown")),

    HTML(tiposMime = listOf("text/html"), extensiones = listOf("html", "htm")),
    ;

    companion object {
        /**
         * Qué es un fichero elegido por el usuario.
         *
         * Se mira **primero el nombre y después el tipo declarado**, al revés de lo que
         * parecería sensato: media docena de proveedores del sistema —descargas, adjuntos
         * de correo, algunas nubes— entregan todo como `application/octet-stream`, y
         * entonces el tipo no dice nada mientras que la extensión sí. Cuando el nombre no
         * lleva extensión reconocible, el tipo es la única pista que queda.
         *
         * @return `null` si no es nada que se sepa convertir, que la interfaz tiene que
         *   poder decir en vez de intentarlo y fallar a mitad.
         */
        fun de(
            nombre: String,
            tipoMime: String? = null,
        ): TipoDeEntrada? {
            val extension = nombre.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { extension in it.extensiones }
                ?: tipoMime?.substringBefore(';')?.trim()?.lowercase()?.let { tipo ->
                    entries.firstOrNull { tipo in it.tiposMime }
                }
        }

        /** Lo que se le pide al selector del sistema para que no enseñe lo que no sirve. */
        val MIMES_ACEPTADOS: List<String> = entries.flatMap { it.tiposMime }
    }
}

/** Un fichero elegido, ya clasificado: sin saber qué es no hay manera de componerlo. */
data class EntradaAConvertir(
    val origen: OrigenDocumento,
    val tipo: TipoDeEntrada,
)

/**
 * Mete en un PDF lo que ya está en el teléfono.
 *
 * **Un solo destino para todas las entradas**, y en el orden en que llegan. No es una
 * limitación: quien elige seis fotos de un contrato quiere un documento de seis páginas,
 * no seis documentos, y quien elige una sola sigue teniendo el caso simple. Lo que
 * produce cada entrada sí cambia —una imagen es siempre una página y un texto son las
 * que haga falta—, y por eso se devuelve la cuenta en vez de darla por sabida.
 *
 * **El destino se escribe entero o no se escribe**, como el resto de herramientas: el
 * documento se monta aparte y sólo se pone en su sitio cuando está completo.
 */
interface ConversorEntradas {
    /**
     * @return cuántas páginas tiene el PDF escrito; 0 si no había nada que convertir
     *   —ficheros de texto vacíos, o una cancelación— y entonces el destino **no se
     *   toca**, que es mejor que dejar allí un documento sin páginas.
     */
    fun aPdf(
        entradas: List<EntradaAConvertir>,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Int
}
