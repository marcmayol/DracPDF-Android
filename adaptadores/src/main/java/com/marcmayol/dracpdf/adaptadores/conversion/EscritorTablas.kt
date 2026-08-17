package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import java.io.OutputStream

/**
 * Una tabla a CSV.
 *
 * **Un fichero por tabla**, no uno con todas pegadas: un CSV es una sola rejilla por
 * definición, y meterle tres separadas por líneas en blanco produce algo que ninguna
 * hoja de cálculo abre bien y que sólo se entiende mirándolo con un editor de texto.
 *
 * Se conservan dos decisiones del escritorio, las dos por el mismo motivo —que el
 * destino real de estos ficheros es Excel—: el separador es el punto y coma, que es el
 * que Excel espera con la configuración regional española, y delante va la marca de
 * orden de bytes, sin la cual Excel abre los acentos rotos al hacer doble clic.
 */
internal object EscritorCsv {
    fun escribir(
        tabla: BloqueDeTexto.Tabla,
        salida: OutputStream,
    ) {
        val texto =
            buildString {
                append(MARCA_DE_ORDEN)
                tabla.filasCuadradas().forEach { fila ->
                    append(fila.joinToString(SEPARADOR.toString(), transform = ::celdaDe))
                    append(FIN_DE_LINEA)
                }
            }
        salida.write(texto.toByteArray(Charsets.UTF_8))
    }

    /** Entrecomillada sólo si hace falta, y con las comillas de dentro dobladas. */
    private fun celdaDe(celda: String): String =
        if (necesitaComillas(celda)) "\"" + celda.replace("\"", "\"\"") + "\"" else celda

    /**
     * Cuándo hay que entrecomillar una celda.
     *
     * Las tres primeras son las que rompen el formato —el separador, la comilla y el
     * salto de línea— y la cuarta es la que rompe a quien luego lo lee: un espacio al
     * principio o al final que sin comillas se pierde por el camino.
     */
    private fun necesitaComillas(celda: String): Boolean {
        val caracteresQueRompen = celda.any { it == SEPARADOR || it == '"' || it == '\n' || it == '\r' }
        return caracteresQueRompen || celda != celda.trim()
    }

    private const val SEPARADOR = ';'

    /** CRLF, que es lo que dice el RFC 4180 y lo que Excel escribe. */
    private const val FIN_DE_LINEA = "\r\n"
    private const val MARCA_DE_ORDEN = ""
}

/**
 * Todas las tablas a un solo XLSX, con una hoja por tabla.
 *
 * Es un ZIP con XML dentro, como el ODT, y se escribe igual de a mano. Se usan **cadenas
 * en línea** (`inlineStr`) en vez de la tabla de cadenas compartidas que Excel produce:
 * la tabla compartida ahorra espacio cuando el mismo texto se repite mil veces, que es
 * el caso de una hoja de cálculo de verdad y no el de unas tablas sacadas de un PDF, y a
 * cambio obliga a mantener dos ficheros coherentes entre sí y a numerar índices.
 *
 * **Todas las celdas son texto.** Un «3,50» de un PDF puede ser un precio o puede ser una
 * referencia; convertirlo a número por si acaso es cómo Excel se come los ceros a la
 * izquierda de los códigos postales, y aquí lo que se ha extraído es texto.
 */
internal object EscritorXlsx {
    fun escribir(
        tablas: List<BloqueDeTexto.Tabla>,
        salida: OutputStream,
    ) {
        val hojas = tablas.mapIndexed { indice, tabla -> Hoja(nombreDe(indice, tabla), tabla.filasCuadradas()) }
        PaqueteZip(salida).use { paquete ->
            paquete.comprimido("[Content_Types].xml", tiposDe(hojas.size))
            paquete.comprimido("_rels/.rels", RELACIONES_RAIZ)
            paquete.comprimido("xl/workbook.xml", libroDe(hojas))
            paquete.comprimido("xl/_rels/workbook.xml.rels", relacionesDe(hojas.size))
            paquete.comprimido("xl/styles.xml", ESTILOS)
            hojas.forEachIndexed { indice, hoja ->
                paquete.comprimido("xl/worksheets/sheet${indice + 1}.xml", contenidoDe(hoja))
            }
        }
    }

    private class Hoja(
        val nombre: String,
        val filas: List<List<String>>,
    )

    /**
     * El nombre de la pestaña dice de qué página salió la tabla.
     *
     * Excel no admite más de 31 caracteres ni los que usa en sus fórmulas de rango, así
     * que el nombre se recorta y se limpia: pasarse deja un fichero que Excel declara
     * corrupto y se ofrece a reparar, que es peor que un nombre feo.
     */
    private fun nombreDe(
        indice: Int,
        tabla: BloqueDeTexto.Tabla,
    ): String =
        "Tabla ${indice + 1} (pag ${tabla.pagina + 1})"
            .filterNot { it in PROHIBIDOS_EN_PESTANA }
            .take(MAXIMO_DE_PESTANA)

    private fun contenidoDe(hoja: Hoja): String =
        buildString {
            append(CABECERA_XML)
            append("<worksheet xmlns=\"$ESPACIO_HOJA\"><sheetData>")
            hoja.filas.forEachIndexed { fila, celdas ->
                append("<row r=\"${fila + 1}\">")
                celdas.forEachIndexed { columna, celda ->
                    append("<c r=\"${referencia(columna, fila)}\" t=\"inlineStr\">")
                    append("<is><t xml:space=\"preserve\">${celda.escapadoXml()}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

    /** «B3»: la letra de la columna y el número de la fila, las dos contando desde uno. */
    private fun referencia(
        columna: Int,
        fila: Int,
    ): String = letraDe(columna) + (fila + 1)

    /** A, B… Z, AA, AB… Es base 26 **sin cero**, que es lo que la hace incómoda. */
    private fun letraDe(columna: Int): String {
        var resto = columna
        val letras = StringBuilder()
        do {
            letras.insert(0, 'A' + resto % LETRAS_DEL_ABECEDARIO)
            resto = resto / LETRAS_DEL_ABECEDARIO - 1
        } while (resto >= 0)
        return letras.toString()
    }

    private fun tiposDe(hojas: Int): String =
        buildString {
            append(CABECERA_XML)
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"$TIPO_RELACIONES\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"$TIPO_LIBRO\"/>")
            append("<Override PartName=\"/xl/styles.xml\" ContentType=\"$TIPO_ESTILOS\"/>")
            repeat(hojas) { indice ->
                append("<Override PartName=\"/xl/worksheets/sheet${indice + 1}.xml\" ContentType=\"$TIPO_HOJA\"/>")
            }
            append("</Types>")
        }

    private fun libroDe(hojas: List<Hoja>): String =
        buildString {
            append(CABECERA_XML)
            append("<workbook xmlns=\"$ESPACIO_HOJA\" xmlns:r=\"$ESPACIO_RELACIONES\"><sheets>")
            hojas.forEachIndexed { indice, hoja ->
                append("<sheet name=\"${hoja.nombre.escapadoXml()}\" ")
                append("sheetId=\"${indice + 1}\" r:id=\"rId${indice + 1}\"/>")
            }
            append("</sheets></workbook>")
        }

    private fun relacionesDe(hojas: Int): String =
        buildString {
            append(CABECERA_XML)
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            repeat(hojas) { indice ->
                append("<Relationship Id=\"rId${indice + 1}\" Type=\"$RELACION_HOJA\" ")
                append("Target=\"worksheets/sheet${indice + 1}.xml\"/>")
            }
            append("<Relationship Id=\"rId${hojas + 1}\" Type=\"$RELACION_ESTILOS\" Target=\"styles.xml\"/>")
            append("</Relationships>")
        }

    private const val CABECERA_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val ESPACIO_HOJA = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val ESPACIO_RELACIONES = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val RELACION_HOJA = "$ESPACIO_RELACIONES/worksheet"
    private const val RELACION_ESTILOS = "$ESPACIO_RELACIONES/styles"
    private const val TIPO_RELACIONES = "application/vnd.openxmlformats-package.relationships+xml"
    private const val TIPO_OFIMATICA = "application/vnd.openxmlformats-officedocument.spreadsheetml"
    private const val TIPO_LIBRO = "$TIPO_OFIMATICA.sheet.main+xml"
    private const val TIPO_HOJA = "$TIPO_OFIMATICA.worksheet+xml"
    private const val TIPO_ESTILOS = "$TIPO_OFIMATICA.styles+xml"

    private const val RELACIONES_RAIZ =
        "$CABECERA_XML<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"$ESPACIO_RELACIONES/officeDocument\" Target=\"xl/workbook.xml\"/>" +
            "</Relationships>"

    /**
     * La hoja de estilos mínima que Excel exige.
     *
     * No se usa ninguno de estos estilos: están porque Excel declara corrupto el libro si
     * falta la parte, y porque quiere los dos rellenos de fábrica —el vacío y el
     * `gray125`— aunque nadie los aplique.
     */
    private const val ESTILOS =
        "$CABECERA_XML<styleSheet xmlns=\"$ESPACIO_HOJA\">" +
            "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
            "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill>" +
            "<fill><patternFill patternType=\"gray125\"/></fill></fills>" +
            "<borders count=\"1\"><border/></borders>" +
            "<cellStyleXfs count=\"1\"><xf/></cellStyleXfs>" +
            "<cellXfs count=\"1\"><xf xfId=\"0\"/></cellXfs>" +
            "</styleSheet>"

    private const val LETRAS_DEL_ABECEDARIO = 26
    private const val MAXIMO_DE_PESTANA = 31
    private const val PROHIBIDOS_EN_PESTANA = "[]:*?/\\"
}
