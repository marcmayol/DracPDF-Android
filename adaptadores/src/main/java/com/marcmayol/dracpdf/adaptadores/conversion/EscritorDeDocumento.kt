package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * Lo que hace un escritor: coger la estructura ya deducida y volcarla.
 *
 * Recibe un flujo y no un fichero **porque el destino puede ser un `content://`** del
 * SAF, donde no hay ruta que abrir: el sistema entrega un flujo y punto. Además deja los
 * escritores sin nada que limpiar —quien abrió el flujo lo cierra— y hace que probarlos
 * sea escribir en memoria.
 */
internal interface EscritorDeDocumento {
    fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    )
}

/**
 * Las filas de una tabla, todas con el mismo número de celdas.
 *
 * Las tablas deducidas por posición pueden traer una fila con una celda de menos —una
 * celda vacía no deja rastro en el PDF: simplemente no hay texto ahí—, y casi todos los
 * formatos de destino se rompen o se descuadran con filas desiguales. Rellenar con
 * vacíos es lo que un lector humano haría al mirarla.
 */
internal fun BloqueDeTexto.Tabla.filasCuadradas(): List<List<String>> {
    val anchura = columnas
    return filas.map { fila -> List(anchura) { indice -> fila.getOrElse(indice) { "" }.limpiaDeSaltos() } }
}

/**
 * Una celda es una celda aunque dentro tuviera dos líneas: los saltos crudos dentro de
 * una celda son lo que hace que un CSV se lea mal en la mitad de los programas.
 */
internal fun String.limpiaDeSaltos(): String =
    split('\n', '\r', Char(CODIGO_SALTO_DE_PAGINA)).joinToString(" ") { it.trim() }.trim()

/** El texto de un bloque, sea lo que sea, para los formatos que no distinguen. */
internal fun BloqueDeTexto.textoLlano(): String =
    when (this) {
        is BloqueDeTexto.Titulo -> texto
        is BloqueDeTexto.Parrafo -> texto
        is BloqueDeTexto.Tabla -> filasCuadradas().joinToString("\n") { it.joinToString("\t") }
        is BloqueDeTexto.SaltoDePagina -> ""
    }

/**
 * Escapado de XML, que sirve igual para HTML, ODT y XLSX porque los tres son XML.
 *
 * Las comillas también se escapan aunque el texto vaya entre etiquetas y no en un
 * atributo: cuesta lo mismo y evita tener que acordarse de cuál de las dos funciones
 * tocaba en cada sitio.
 */
internal fun String.escapadoXml(): String =
    buildString(length) {
        this@escapadoXml.forEach { caracter ->
            when (caracter) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                // XML 1.0 no admite caracteres de control ni siquiera escapados: colarlos
                // deja un fichero que ningún analizador abre, y en un PDF son basura.
                else -> append(if (caracter.admisibleEnXml()) caracter else ' ')
            }
        }
    }

private fun Char.admisibleEnXml(): Boolean = code >= PRIMER_CARACTER_ADMISIBLE || this in BLANCOS_ADMISIBLES

/** El primer título del documento, que es lo más parecido a un nombre que tiene. */
internal fun DocumentoEstructurado.tituloProbable(): String =
    bloques
        .filterIsInstance<BloqueDeTexto.Titulo>()
        .firstOrNull()
        ?.texto
        ?.takeIf { it.isNotBlank() }
        ?: SIN_TITULO

private const val PRIMER_CARACTER_ADMISIBLE = 32
private const val BLANCOS_ADMISIBLES = "\t\n"

/** El 0x0C con el que MuPDF cierra cada página; dentro de una celda no pinta nada. */
private const val CODIGO_SALTO_DE_PAGINA = 12

private const val SIN_TITULO = "Documento"
