package com.marcmayol.dracpdf.dominio.modelo

/**
 * Con qué se firma.
 *
 * Es una abstracción y no «un fichero .p12» a propósito, igual que en el escritorio:
 * hoy la credencial es un PKCS#12 que el usuario elige por el selector del sistema,
 * pero mañana puede ser un certificado del almacén de Android, uno en tarjeta o uno
 * remoto. Lo que no puede pasar es que el resto de la aplicación tenga que cambiar
 * cuando aparezca el segundo tipo.
 */
sealed interface Credencial {
    /** Un PKCS#12 (`.p12` o `.pfx`) con su contraseña. */
    data class Pkcs12(
        val origen: OrigenDocumento,
        val contrasena: String,
    ) : Credencial
}

/**
 * Lo que se sabe de una firma que ya está dentro del documento.
 *
 * El estado va con **icono y texto** en la interfaz, nunca sólo con color: quien
 * mira un documento firmado necesita entender qué le están diciendo, y «rojo» no es
 * una frase.
 */
data class FirmaDelDocumento(
    /** El nombre del campo de firma en el PDF. */
    val campo: String,
    /** Quién dice ser el firmante, tal como viene en el certificado. */
    val firmante: String?,
    val estado: EstadoDeFirma,
    /** La página donde está el sello visible, si lo tiene. */
    val pagina: Int?,
    /**
     * Si el documento se modificó **después** de esta firma.
     *
     * No es lo mismo que estar inválida: un PDF puede llevar una firma íntegra y
     * encima cambios posteriores legítimos, y el estándar lo permite. Pero hay que
     * decirlo, porque lo que se firmó no es lo que se está viendo.
     */
    val huboCambiosDespues: Boolean = false,
)

/**
 * En qué estado está una firma.
 *
 * Son tres y no dos, y la diferencia entre las dos últimas es la que más se
 * malinterpreta: una firma **desconocida** no es una firma mala. Significa que las
 * cuentas cuadran pero no se puede decir de quién es, casi siempre porque el
 * certificado es autofirmado o su emisor no está entre los de confianza. Presentarla
 * como inválida asustaría a quien recibe un documento perfectamente correcto.
 */
enum class EstadoDeFirma {
    /** Los bytes firmados cuadran y el firmante es de confianza. */
    VALIDA,

    /**
     * Las cuentas no cuadran: el documento se ha tocado por debajo de la firma, o la
     * firma está corrupta. Esto sí es un problema.
     */
    INVALIDA,

    /**
     * Íntegra, pero no se puede afirmar de quién es. El caso del certificado
     * autofirmado, y el estado en el que aparecerá el de pruebas de este repositorio
     * hasta que se añada a mano a los de confianza.
     */
    DESCONOCIDA,
}

/** Dónde y cómo se dibuja el sello de una firma digital. */
data class SelloVisible(
    val pagina: Int,
    val marco: RectPt,
    /**
     * La imagen del sello, si la hay: normalmente la firma dibujada de la Fase 3.
     *
     * Es opcional porque una firma digital **no necesita verse** para valer: lo que
     * tiene valor es la criptografía, y el sello es sólo cortesía para quien mira el
     * documento. Confundir las dos cosas es lo que lleva a creer que un garabato
     * pegado encima firma algo.
     */
    val png: ByteArray? = null,
    /** El texto que acompaña al sello; si es nulo, se compone del certificado. */
    val texto: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SelloVisible &&
                    pagina == other.pagina &&
                    marco == other.marco &&
                    texto == other.texto &&
                    (png?.contentEquals(other.png) ?: (other.png == null))
            )

    override fun hashCode(): Int {
        var resultado = pagina
        resultado = 31 * resultado + marco.hashCode()
        resultado = 31 * resultado + (texto?.hashCode() ?: 0)
        resultado = 31 * resultado + (png?.contentHashCode() ?: 0)
        return resultado
    }
}
