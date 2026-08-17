package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Leer y escribir documentos de Word, en los dos sentidos y sin bibliotecas.
 *
 * El puerto habla de [DocumentoEstructurado] y no de `.docx` a propósito: lo que se
 * promete es el **contenido** —títulos, párrafos y tablas—, y ése es exactamente el
 * vocabulario del modelo. La maquetación no entra, ni de ida ni de vuelta; por eso todo
 * lo que pase por aquí se etiqueta «reformateado» en la interfaz.
 *
 * Que no haya dependencias detrás tampoco es casualidad: un `.docx` es un ZIP con XML
 * dentro, y `java.util.zip` más el analizador XML de la plataforma bastan. No existe un
 * pdf2docx ni un mammoth para Android, así que la alternativa a escribirlo a mano no
 * era una biblioteca: era no ofrecer el formato.
 */
interface ConversorWord {
    /**
     * Escribe [documento] como `.docx` en [destino].
     *
     * El fichero se monta aparte y sólo se pone en su sitio cuando está entero, como el
     * resto de salidas de la aplicación: un `.docx` a medias es un ZIP corrupto que
     * ningún programa sabe abrir, y el usuario no tendría manera de saber por qué.
     */
    fun escribir(
        documento: DocumentoEstructurado,
        destino: OrigenDocumento,
    )

    /**
     * Lee un `.docx` **ajeno** —hecho con Word o con LibreOffice— y devuelve lo que se
     * entiende de él.
     *
     * @throws java.io.IOException si el fichero no es un `.docx` que se pueda abrir.
     */
    fun leer(origen: OrigenDocumento): DocumentoEstructurado
}

/**
 * Componer un PDF a partir de contenido que no tiene páginas todavía.
 *
 * Va aparte de [ConversorWord] porque no es asunto de Word: el mismo servicio hace
 * falta para cualquier entrante que llegue como texto —Markdown, HTML o un `.txt`— y
 * atarlo al `.docx` obligaría a pasar por Word para convertir un fichero de texto.
 */
interface ComponedorDePdf {
    /**
     * Maqueta [documento] en un PDF y lo deja en [destino].
     *
     * Parte en páginas por su cuenta: el contenido de origen no las tiene, y quien
     * compone es el único que sabe cuánto ocupa cada línea con la letra elegida.
     *
     * @return cuántas páginas se han escrito; 0 si se canceló, y entonces el destino
     *   queda **sin tocar**.
     */
    fun componer(
        documento: DocumentoEstructurado,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Int
}
