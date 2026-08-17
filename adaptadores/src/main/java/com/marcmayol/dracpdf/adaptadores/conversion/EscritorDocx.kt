package com.marcmayol.dracpdf.adaptadores.conversion

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import java.io.OutputStream

/**
 * Escribe un `.docx` a mano, que es lo mismo que el DracPDF de escritorio hace con ODT
 * y con RTF: un ZIP con XML dentro y ninguna dependencia nueva.
 *
 * El paquete que se monta es **el mínimo que Word y LibreOffice aceptan sin quejarse**,
 * y ni una pieza más: los tipos de contenido, la relación que dice cuál es el documento
 * principal, la que dice dónde están los estilos, el cuerpo y los estilos. Un `.docx`
 * de Word trae otras quince piezas —fuentes, temas, ajustes, propiedades— que ninguno
 * de los dos programas exige para abrirlo, y escribirlas sería inventar contenido.
 *
 * Lo que se promete es el contenido, no la maquetación: títulos con su nivel, párrafos
 * y tablas. Ni saltos de línea forzados dentro de una celda, ni negritas sueltas, ni
 * imágenes; el modelo del que se parte tampoco los tiene.
 *
 * **El orden de los elementos importa.** El esquema de OOXML es una secuencia, no un
 * conjunto: `w:pStyle` antes que `w:spacing`, `w:tblGrid` después de `w:tblPr` y
 * `w:sectPr` al final del cuerpo. Word abre igualmente casi cualquier cosa, pero
 * LibreOffice no siempre, y aquí tienen que abrirlo los dos.
 */
class EscritorDocx {
    /**
     * Vuelca [documento] como paquete `.docx` en [salida].
     *
     * El flujo se cierra al salir: el ZIP no está completo hasta que se escribe su
     * índice final, y un `.docx` sin índice no lo abre nadie.
     */
    fun escribir(
        documento: DocumentoEstructurado,
        salida: OutputStream,
    ) {
        val flujo = salida.buffered()
        PaqueteZip(flujo).use { paquete ->
            // Todo comprimido. Al contrario que ODF —de ahí el `crudo` del paquete—,
            // OOXML no exige ninguna entrada sin comprimir al principio: no hay
            // `mimetype` que respetar, el tipo lo dice `[Content_Types].xml`.
            paquete.comprimido("[Content_Types].xml", TIPOS_DE_CONTENIDO)
            paquete.comprimido("_rels/.rels", RELACIONES_DEL_PAQUETE)
            paquete.comprimido("word/_rels/document.xml.rels", RELACIONES_DEL_DOCUMENTO)
            paquete.comprimido("word/styles.xml", estilos())
            paquete.comprimido("word/document.xml", cuerpo(documento))
        }
        flujo.flush()
    }

    /** `word/document.xml`: el cuerpo, bloque a bloque, y la sección al final. */
    private fun cuerpo(documento: DocumentoEstructurado): String =
        buildString {
            append(PROLOGO)
            append("<w:document xmlns:w=\"$NS_WORD\"><w:body>")
            documento.bloques.forEach { bloque -> append(bloqueXml(bloque)) }
            append(SECCION_A4)
            append("</w:body></w:document>")
        }

    private fun bloqueXml(bloque: BloqueDeTexto): String =
        when (bloque) {
            is BloqueDeTexto.Titulo ->
                parrafoXml(bloque.texto, "Heading${bloque.nivel.coerceIn(1, NIVELES_DE_TITULO)}")

            is BloqueDeTexto.Parrafo -> parrafoXml(bloque.texto, estilo = null)
            is BloqueDeTexto.Tabla -> tablaXml(bloque)
            // Word no tiene «página»: tiene un salto que se mete dentro de un párrafo
            // vacío. Es la única manera de decirle «a partir de aquí, hoja nueva».
            is BloqueDeTexto.SaltoDePagina -> "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"
        }

    private fun parrafoXml(
        texto: String,
        estilo: String?,
    ): String =
        buildString {
            append("<w:p>")
            if (estilo != null) append("<w:pPr><w:pStyle w:val=\"$estilo\"/></w:pPr>")
            append(carreraXml(texto))
            append("</w:p>")
        }

    /**
     * El texto de un párrafo, en «carreras» (`w:r`).
     *
     * Se parte por saltos de línea y no por otra cosa: dentro de un párrafo, un `\n` es
     * un `w:br`, y un `\t` un `w:tab`. Todo lo demás va en un solo `w:t` con
     * `xml:space="preserve"`, porque sin ese atributo Word se come los espacios de los
     * extremos y «hola » y «hola» dejan de ser lo mismo al volver a leerlo.
     */
    private fun carreraXml(texto: String): String =
        buildString {
            append("<w:r>")
            texto.split('\n').forEachIndexed { indice, linea ->
                if (indice > 0) append("<w:br/>")
                linea.split('\t').forEachIndexed { trozo, parte ->
                    if (trozo > 0) append("<w:tab/>")
                    if (parte.isNotEmpty()) append("<w:t xml:space=\"preserve\">${escapar(parte)}</w:t>")
                }
            }
            append("</w:r>")
        }

    /**
     * Una tabla, rellenada hasta ser rectangular.
     *
     * Las filas cortas se completan con celdas vacías: la detección de tablas de un PDF
     * devuelve filas de distinto tamaño con frecuencia, y una tabla con la última fila a
     * medias se dibuja mal en Word y peor en LibreOffice.
     */
    private fun tablaXml(tabla: BloqueDeTexto.Tabla): String {
        val columnas = tabla.columnas
        if (columnas == 0) return ""
        val anchoColumna = ANCHO_UTIL_TWIPS / columnas
        return buildString {
            append("<w:tbl>")
            append("<w:tblPr><w:tblStyle w:val=\"TableGrid\"/><w:tblW w:w=\"0\" w:type=\"auto\"/>")
            append("<w:tblLayout w:type=\"fixed\"/></w:tblPr>")
            append("<w:tblGrid>")
            repeat(columnas) { append("<w:gridCol w:w=\"$anchoColumna\"/>") }
            append("</w:tblGrid>")
            tabla.filas.forEach { fila ->
                append("<w:tr>")
                for (columna in 0 until columnas) {
                    append("<w:tc><w:tcPr><w:tcW w:w=\"$anchoColumna\" w:type=\"dxa\"/></w:tcPr>")
                    append(parrafoXml(fila.getOrElse(columna) { "" }, estilo = null))
                    append("</w:tc>")
                }
                append("</w:tr>")
            }
            append("</w:tbl>")
            // Un párrafo vacío detrás, y no es adorno: dos tablas seguidas sin nada en
            // medio se funden en una sola, y una tabla al final del cuerpo deja el
            // documento sin sitio donde poner el cursor. Al releerlo se descarta solo,
            // porque los párrafos en blanco no son contenido.
            append("<w:p/>")
        }
    }

    /**
     * `word/styles.xml`, con **sólo los estilos que se usan**.
     *
     * Cada título lleva su `w:outlineLvl`, que es lo que hace que el nivel sobreviva:
     * el identificador del estilo lo traduce cada Word a su idioma —«Ttulo1» en el
     * español, «berschrift1» en el alemán— y no se puede confiar en él, mientras que el
     * nivel de esquema es un número igual en todas partes. El lector de esta misma
     * aplicación lo aprovecha para reconocer los títulos ajenos.
     */
    private fun estilos(): String =
        buildString {
            append(PROLOGO)
            append("<w:styles xmlns:w=\"$NS_WORD\">")
            append("<w:docDefaults><w:rPrDefault><w:rPr>")
            append("<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:cs=\"Calibri\"/>")
            append("<w:sz w:val=\"$CUERPO_MEDIOS_PUNTOS\"/><w:szCs w:val=\"$CUERPO_MEDIOS_PUNTOS\"/>")
            append("</w:rPr></w:rPrDefault>")
            append("<w:pPrDefault><w:pPr><w:spacing w:after=\"$ESPACIO_TRAS_PARRAFO\"/></w:pPr></w:pPrDefault>")
            append("</w:docDefaults>")
            append("<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">")
            append("<w:name w:val=\"Normal\"/><w:qFormat/></w:style>")
            for (nivel in 1..NIVELES_DE_TITULO) append(estiloDeTitulo(nivel))
            append("<w:style w:type=\"table\" w:styleId=\"TableGrid\"><w:name w:val=\"Table Grid\"/>")
            append("<w:tblPr>${bordesDeTabla()}</w:tblPr></w:style>")
            append("</w:styles>")
        }

    private fun estiloDeTitulo(nivel: Int): String =
        buildString {
            append("<w:style w:type=\"paragraph\" w:styleId=\"Heading$nivel\">")
            // El nombre siempre en inglés y en minúscula: es el nombre canónico que Word
            // usa internamente para todos los idiomas.
            append("<w:name w:val=\"heading $nivel\"/>")
            append("<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>")
            append("<w:pPr><w:keepNext/>")
            append("<w:spacing w:before=\"$ESPACIO_ANTES_DE_TITULO\" w:after=\"$ESPACIO_TRAS_PARRAFO\"/>")
            append("<w:outlineLvl w:val=\"${nivel - 1}\"/></w:pPr>")
            append("<w:rPr><w:b/><w:sz w:val=\"${mediosPuntosDeTitulo(nivel)}\"/>")
            append("<w:szCs w:val=\"${mediosPuntosDeTitulo(nivel)}\"/></w:rPr>")
            append("</w:style>")
        }

    private fun bordesDeTabla(): String =
        buildString {
            append("<w:tblBorders>")
            listOf("top", "left", "bottom", "right", "insideH", "insideV").forEach { lado ->
                append("<w:$lado w:val=\"single\" w:sz=\"$GROSOR_BORDE\" w:space=\"0\" w:color=\"auto\"/>")
            }
            append("</w:tblBorders>")
        }

    /** El tamaño va en medios puntos, que es como OOXML mide la letra. */
    private fun mediosPuntosDeTitulo(nivel: Int): Int =
        (MEDIOS_PUNTOS_TITULO_MAYOR - (nivel - 1) * ESCALON_DE_TITULO)
            .coerceAtLeast(CUERPO_MEDIOS_PUNTOS)

    /**
     * Lo que XML no perdona: los tres caracteres con significado propio y los de control
     * que ni siquiera con entidad son válidos en XML 1.0.
     *
     * Los de control llegan de verdad: el texto sacado de un PDF trae saltos de página
     * (U+000C) y separadores raros, y uno solo de ésos deja el `.docx` ilegible con un
     * error que no dice nada.
     */
    private fun escapar(texto: String): String =
        buildString(texto.length) {
            texto.forEach { caracter ->
                when {
                    caracter == '&' -> append("&amp;")
                    caracter == '<' -> append("&lt;")
                    caracter == '>' -> append("&gt;")
                    caracter == '\t' || caracter == '\n' || caracter == '\r' -> append(caracter)
                    caracter.code < PRIMER_CARACTER_VALIDO -> Unit
                    else -> append(caracter)
                }
            }
        }

    private companion object {
        const val NS_WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        const val PROLOGO = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\r\n"

        /** Word llega a nueve, pero de nivel cuatro en adelante ya nadie los distingue. */
        const val NIVELES_DE_TITULO = 6

        const val PRIMER_CARACTER_VALIDO = 0x20
        const val CUERPO_MEDIOS_PUNTOS = 22
        const val MEDIOS_PUNTOS_TITULO_MAYOR = 40
        const val ESCALON_DE_TITULO = 4
        const val ESPACIO_TRAS_PARRAFO = 120
        const val ESPACIO_ANTES_DE_TITULO = 240
        const val GROSOR_BORDE = 4

        /** A4 (11906 twips) menos dos centímetros de margen a cada lado (1134). */
        const val ANCHO_UTIL_TWIPS = 9638

        const val TIPOS_DE_CONTENIDO =
            PROLOGO +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" " +
                "ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd." +
                "openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
                "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd." +
                "openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
                "</Types>"

        const val RELACIONES_DEL_PAQUETE =
            PROLOGO +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/" +
                "relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                "</Relationships>"

        const val RELACIONES_DEL_DOCUMENTO =
            PROLOGO +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/" +
                "relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>"

        /** A4 vertical con dos centímetros de margen, en twips (1/1440 de pulgada). */
        const val SECCION_A4 =
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
                "<w:pgMar w:top=\"1134\" w:right=\"1134\" w:bottom=\"1134\" w:left=\"1134\" " +
                "w:header=\"708\" w:footer=\"708\" w:gutter=\"0\"/></w:sectPr>"
    }
}
