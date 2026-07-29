package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.Estampado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.RectPt

/**
 * Estampar una imagen sobre una página.
 *
 * Es lo que pone la firma dibujada encima del documento. No tiene nada que ver con
 * la firma digital de la Fase 4: esto es tinta, y aquello es criptografía. Se
 * parecen en el nombre y en nada más, y confundirlas es prometerle a alguien que su
 * garabato tiene valor legal.
 */
interface StampService {
    /**
     * Pone la imagen [png] dentro de [marco], en coordenadas de la página.
     *
     * La imagen se estampa **como anotación**, no fundida en el contenido: así sigue
     * siendo un objeto identificable que otro visor entiende, que se puede quitar, y
     * que no destruye lo que había debajo. Y el canal alfa se respeta, que es lo que
     * distingue una firma de un recuadro blanco con una firma dentro.
     */
    fun estampar(
        id: IdDocumento,
        pagina: Int,
        marco: RectPt,
        png: ByteArray,
    ): Estampado
}
