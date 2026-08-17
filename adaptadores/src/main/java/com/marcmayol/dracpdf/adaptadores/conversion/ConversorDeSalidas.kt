package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ConversorSalidas
import com.marcmayol.dracpdf.dominio.puertos.FormatoSalida
import com.marcmayol.dracpdf.dominio.puertos.FormatoTabla

/**
 * Pone en su sitio lo que escriben los escritores.
 *
 * Es todo lo que hace: elegir el escritor, componer el nombre y pedirle el hueco a
 * [SalidasDeHerramienta], que es quien sabe la diferencia entre un fichero privado y una
 * carpeta del sistema. Los escritores no conocen el SAF ni las rutas, y por eso se pueden
 * probar escribiendo en memoria.
 *
 * **No hay temporal ni volcado al final**, al revés que las herramientas que producen
 * PDF. Aquí se escribe directamente en el destino porque el SAF ya crea el fichero de una
 * pieza y porque estas salidas son pequeñas: montar un temporal para copiarlo enseguida
 * sería atomicidad de mentira, ya que copiar por el flujo del proveedor tampoco es
 * atómico.
 */
class ConversorDeSalidas(
    private val salidas: SalidasDeHerramienta,
) : ConversorSalidas {
    override fun escribir(
        documento: DocumentoEstructurado,
        formato: FormatoSalida,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ): OrigenDocumento =
        salidas.escribirEn(carpeta, "${limpio(nombreBase)}.${formato.extension}") { flujo ->
            escritorDe(formato).escribir(documento, flujo)
        }

    override fun escribirTablas(
        tablas: List<BloqueDeTexto.Tabla>,
        formato: FormatoTabla,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ): List<OrigenDocumento> {
        if (tablas.isEmpty()) return emptyList()
        val base = limpio(nombreBase)
        return when (formato) {
            // Numeradas desde uno y en el orden en que aparecen en el documento, que es el
            // mismo con el que se contaron: si el aviso previo dijo «3 tablas», la tercera
            // del aviso es la del fichero que acaba en 3.
            FormatoTabla.CSV ->
                tablas.mapIndexed { indice, tabla ->
                    salidas.escribirEn(carpeta, "$base-tabla-${indice + 1}.csv") { flujo ->
                        EscritorCsv.escribir(tabla, flujo)
                    }
                }

            FormatoTabla.XLSX ->
                listOf(
                    salidas.escribirEn(carpeta, "$base-tablas.xlsx") { flujo ->
                        EscritorXlsx.escribir(tablas, flujo)
                    },
                )
        }
    }

    private fun escritorDe(formato: FormatoSalida): EscritorDeDocumento =
        when (formato) {
            FormatoSalida.HTML -> EscritorHtml
            FormatoSalida.MARKDOWN -> EscritorMarkdown
            FormatoSalida.TEXTO -> EscritorTexto
            FormatoSalida.ODT -> EscritorOdt
            FormatoSalida.RTF -> EscritorRtf
        }

    /**
     * El nombre del PDF sirve de base para el del resultado, y puede traer cualquier cosa
     * dentro: viene del sistema de ficheros de otro, o del nombre que le puso quien mandó
     * el documento por mensajería. Los caracteres que ningún sistema admite se sustituyen
     * en vez de rechazar el nombre entero, que dejaría al usuario sin poder convertir un
     * documento por cómo se llama.
     */
    private fun limpio(nombre: String): String =
        nombre
            .map { if (it in PROHIBIDOS || it.code < PRIMER_CARACTER_VISIBLE) '-' else it }
            .joinToString("")
            .trim('-', ' ', '.')
            .ifBlank { SIN_NOMBRE }

    private companion object {
        const val PROHIBIDOS = "/\\:*?\"<>|"
        const val PRIMER_CARACTER_VISIBLE = 32
        const val SIN_NOMBRE = "documento"
    }
}
