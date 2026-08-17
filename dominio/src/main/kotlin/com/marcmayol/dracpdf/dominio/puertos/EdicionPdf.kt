package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt

/**
 * Una imagen que está dibujada en una página.
 *
 * Se identifica por su contorno y no por un nombre: el PDF no le pone nombre a lo que
 * dibuja, y lo que el usuario señala con el dedo es un sitio de la página.
 */
data class ImagenEnPagina(
    val pagina: Int,
    val marco: RectPt,
    /**
     * Si ocupa la página entera, o casi.
     *
     * Importa porque casi siempre significa que el documento es un **escaneo**: la
     * «imagen» es la hoja completa, y quitarla deja la página en blanco. Hay que
     * avisar antes, no después.
     */
    val esLaPaginaEntera: Boolean,
)

/**
 * Editar el contenido de la página: añadir y quitar imágenes, y corregir texto.
 *
 * Esto es otra cosa que anotar. Una anotación vive **encima** del documento y se
 * puede quitar desde cualquier visor; lo que entra por aquí cambia el contenido, y no
 * hay vuelta atrás más allá del deshacer de la sesión. Por eso todo lo de aquí exige
 * que el documento no esté firmado, y por eso corregir texto **borra de verdad** el
 * original en vez de taparlo: un rectángulo blanco encima deja el texto debajo, y
 * cualquiera lo saca con copiar y pegar.
 */
interface EdicionPdf {
    /** Las imágenes dibujadas en una página, con su contorno exacto. */
    fun imagenesDe(
        id: IdDocumento,
        pagina: Int,
    ): List<ImagenEnPagina>

    /** Pone una imagen en la página, dentro del marco dado. */
    fun anadirImagen(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        imagen: ByteArray,
    )

    /**
     * Quita del contenido lo que haya dentro del marco.
     *
     * Se hace con una redacción del propio motor y no reescribiendo el flujo de
     * dibujo a mano: el flujo puede venir comprimido, partido en trozos y compartido
     * entre páginas, y tocarlo a mano rompe documentos de verdad.
     */
    fun quitarImagen(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
    )

    /**
     * Cambia un texto por otro dentro de un marco.
     *
     * El original **desaparece del documento**, no se tapa: es lo que distingue
     * corregir de disimular, y lo único que se puede llamar corrección en un
     * documento que alguien va a leer con otra herramienta.
     *
     * @return `false` si el texto nuevo no cabe en el hueco del viejo. No se recorta
     *   ni se encoge la letra por su cuenta: quien pide la corrección tiene que poder
     *   decidir qué hacer.
     */
    fun corregirTexto(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        nuevo: String,
        tamano: Float,
    ): Boolean
}
