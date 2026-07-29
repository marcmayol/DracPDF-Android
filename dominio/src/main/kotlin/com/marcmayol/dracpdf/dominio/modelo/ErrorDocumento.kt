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

    /**
     * El contenido no se deja interpretar: fichero corrupto, truncado, o que no es un
     * PDF por mucho que se llame así.
     */
    class NoSePuedeLeer(
        val origen: OrigenDocumento,
        causa: Throwable? = null,
    ) : ErrorDocumento("No se ha podido leer «${origen.identificador}»", causa)

    /**
     * Ni siquiera se ha podido llegar al fichero: la ruta ya no existe, o el sistema
     * no deja abrirla.
     *
     * Es distinto de [NoSePuedeLeer] y la diferencia importa para quien lo lee: culpar
     * al PDF de estar roto cuando el problema es que no se puede acceder a él manda al
     * usuario a buscar el fallo donde no está.
     */
    class NoSePuedeAbrirElFichero(
        val origen: OrigenDocumento,
        causa: Throwable? = null,
    ) : ErrorDocumento("No se ha podido abrir «${origen.identificador}»", causa)

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

    /**
     * Se ha intentado editar un documento firmado.
     *
     * No es un capricho ni un error del usuario: cambiar un PDF firmado invalida la
     * firma, y quien lo intenta casi siempre quiere seguir trabajando, no romperla.
     * Por eso quien capture esto tiene que ofrecer guardar una copia editable, igual
     * que en el escritorio, en vez de limitarse a decir que no.
     */
    class DocumentoFirmado(
        val id: IdDocumento,
    ) : ErrorDocumento("El documento ${id.valor} está firmado: editarlo invalidaría la firma")
}
