package com.marcmayol.dracpdf.dominio.modelo

/**
 * Lo que puede salir mal al abrir o leer un documento, y que no es culpa de quien
 * programa sino del documento o del usuario. Los errores de programación —pedir la
 * página 900 de un documento de 12— son `IllegalArgumentException`, no esto.
 */
sealed class ErrorDocumento(
    mensaje: String,
    causa: Throwable? = null,
) : Exception(mensaje, causa) {
    /** El documento está cifrado y hay que pedir la contraseña antes de mostrarlo. */
    class NecesitaContrasena(
        val origen: OrigenDocumento,
    ) : ErrorDocumento("El documento «${origen.identificador}» está protegido con contraseña")

    /** La contraseña no abre el documento. */
    class ContrasenaIncorrecta(
        val origen: OrigenDocumento,
    ) : ErrorDocumento("La contraseña no abre «${origen.identificador}»")

    /** No se ha podido leer: fichero corrupto, truncado o que no es un PDF. */
    class NoSePuedeLeer(
        val origen: OrigenDocumento,
        causa: Throwable? = null,
    ) : ErrorDocumento("No se ha podido leer «${origen.identificador}»", causa)

    /**
     * El permiso sobre el fichero ya no vale. Pasa con los recientes: el usuario
     * revocó el acceso, o el proveedor de nube dejó de compartirlo.
     */
    class SinPermiso(
        val origen: OrigenDocumento,
    ) : ErrorDocumento("Ya no hay permiso para leer «${origen.identificador}»")

    /** Se pidió algo de un documento que no está abierto. */
    class NoEstaAbierto(
        val id: IdDocumento,
    ) : ErrorDocumento("El documento ${id.valor} no está abierto")
}
