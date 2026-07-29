package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.Credencial
import com.marcmayol.dracpdf.dominio.modelo.FirmaDelDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.SelloVisible

/**
 * Firmar y verificar de verdad: criptografía, no tinta.
 *
 * **Trabaja sobre ficheros, no sobre documentos abiertos**, y ésa es la decisión que
 * gobierna toda la fase. Una firma PAdES cubre unos bytes concretos: los que están en
 * el disco. Firmar «el documento que hay en memoria» no significa nada, porque lo que
 * se entrega es el fichero, y si no coinciden, la firma no vale. Así que el que firma
 * lee el fichero, escribe otro, y quien tenía el documento abierto lo recarga después.
 */
interface SignatureService {
    /**
     * Firma [origen] y deja el resultado en [destino], **como revisión nueva**.
     *
     * El destino es otro fichero y no el mismo a propósito: si algo falla a mitad de
     * escribir, el original sigue intacto. Quien llama se encarga de poner el
     * resultado en su sitio cuando ya está completo.
     */
    fun firmar(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        credencial: Credencial,
        sello: SelloVisible?,
    )

    /**
     * Las firmas que tiene un fichero, con su estado.
     *
     * Se pregunta al fichero y no al documento en memoria por el mismo motivo que
     * arriba: lo que se verifica son los bytes que hay escritos.
     */
    fun verificar(origen: OrigenDocumento): List<FirmaDelDocumento>
}
