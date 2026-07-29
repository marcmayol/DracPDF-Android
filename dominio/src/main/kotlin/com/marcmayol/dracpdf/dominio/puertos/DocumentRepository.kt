package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt

/**
 * El puerto del documento. Su implementación real es MuPDF, pero aquí no aparece:
 * el dominio no sabe qué motor hay debajo, igual que en el escritorio el núcleo no
 * importa PyMuPDF.
 *
 * Todas las operaciones son síncronas y **no son seguras entre hilos**: MuPDF exige
 * que un documento se toque siempre desde el mismo hilo. Quien lo implementa se
 * encarga de serializar; quien lo usa no debe llamarlo desde varios hilos a la vez.
 */
interface DocumentRepository {
    /**
     * Abre un documento bajo el [id] que le da el registro y devuelve su ficha, con
     * las marcas que ya trae de fábrica (un PDF firmado nace
     * [com.marcmayol.dracpdf.dominio.modelo.Marca.FIRMADO]).
     *
     * El identificador lo impone quien llama, y no lo inventa el repositorio, porque
     * es el mismo que sobrevive a que el fichero se reemplace por una revisión
     * firmada: el documento sigue siendo el mismo para quien lo está mirando.
     *
     * @throws com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento.NecesitaContrasena si está cifrado.
     */
    fun abrir(
        id: IdDocumento,
        origen: OrigenDocumento,
        contrasena: String? = null,
    ): DocumentoAbierto

    /**
     * Tamaño de una página sin rasterizarla. Es barato, y es lo que permite que la
     * lista de páginas reserve el hueco exacto antes de tener el render: sin esto el
     * scroll de un documento largo daría saltos.
     */
    fun tamanoPagina(
        id: IdDocumento,
        pagina: Int,
    ): TamanoPt

    /** Rasteriza una página a la escala pedida. */
    fun renderizar(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ): PaginaRenderizada

    /**
     * Si el documento en memoria tiene cambios que aún no están en el fichero.
     *
     * Lo sabe el motor, no el registro: el registro anota que alguien tocó algo, pero
     * el motor sabe si ese algo llegó a cambiar el documento de verdad.
     */
    fun tieneCambiosSinGuardar(id: IdDocumento): Boolean

    /**
     * Escribe los cambios en el propio fichero, **como revisión nueva** y sin tocar
     * lo que ya había.
     *
     * El guardado incremental no es una optimización, es un requisito: un PDF firmado
     * conserva sus firmas válidas sólo si las revisiones anteriores siguen ahí, byte
     * a byte. Reescribir el fichero entero rompería toda firma previa, incluidas las
     * de terceros, y eso no se puede deshacer.
     */
    fun guardarIncremental(id: IdDocumento)

    /**
     * De dónde salió el documento.
     *
     * Hace falta para firmar y para guardar una copia: las dos cosas trabajan sobre el
     * fichero, y el fichero lo sabe quien lo abrió.
     */
    fun origenDe(id: IdDocumento): OrigenDocumento

    /**
     * Copia el documento tal como está en el fichero a otro sitio.
     *
     * Es lo que sostiene la «copia editable» que se ofrece cuando alguien intenta
     * cambiar un documento firmado. Se copia el fichero y no el documento en memoria a
     * propósito: así la copia arranca siendo idéntica al original, firmas incluidas, y
     * es el usuario quien decide qué hacer con ella.
     */
    fun copiarA(
        id: IdDocumento,
        destino: OrigenDocumento,
    )

    /** Libera el documento. Después de esto su [IdDocumento] ya no vale. */
    fun cerrar(id: IdDocumento)
}
