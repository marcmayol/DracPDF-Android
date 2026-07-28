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

    /** Libera el documento. Después de esto su [IdDocumento] ya no vale. */
    fun cerrar(id: IdDocumento)
}
