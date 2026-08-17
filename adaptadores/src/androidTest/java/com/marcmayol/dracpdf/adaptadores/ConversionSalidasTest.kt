package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.conversion.ConversorDeSalidas
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfEstructura
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.ConvertirEstructura
import com.marcmayol.dracpdf.dominio.casos.ResultadoConversion
import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.FormatoSalida
import com.marcmayol.dracpdf.dominio.puertos.FormatoTabla
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipEntry

/**
 * Las conversiones salientes de punta a punta: del PDF al fichero, y del fichero de
 * vuelta.
 *
 * Todo lo que se escribe **se vuelve a leer**, que es lo que el criterio F9 pide y lo que
 * distingue una prueba de una conversión de comprobar que el fichero pesa más que cero.
 * El ODT y el XLSX se abren como los paquetes que son, el RTF se descifra y el CSV se
 * compara celda a celda contra la tabla que el fixture dibujó.
 */
@RunWith(AndroidJUnit4::class)
class ConversionSalidasTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var convertir: ConvertirEstructura
    private val abiertos = mutableListOf<IdDocumento>()

    @Before
    fun montarLaPila() {
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        convertir =
            ConvertirEstructura(
                lector = MuPdfEstructura(sesiones),
                conversor = ConversorDeSalidas(SalidasDeHerramienta(contexto.contentResolver)),
            )
    }

    @After
    fun cerrarTodo() {
        abiertos.forEach(repositorio::cerrar)
        abiertos.clear()
    }

    @Test
    fun el_html_lleva_los_titulos_como_titulos_y_la_tabla_como_tabla() {
        val fichero = convertido(FormatoSalida.HTML, "html")
        val html = fichero.readText()

        assertTrue("Falta el título: $html", "<h1>${GeneradorFixtures.TITULO_CON_TABLA}</h1>" in html)
        assertTrue("Falta el párrafo", GeneradorFixtures.PARRAFO_CON_TABLA in html)
        assertTrue("La tabla no salió como tabla", "<th>Producto</th>" in html && "<td>Grapas</td>" in html)
    }

    @Test
    fun el_markdown_usa_almohadillas_y_barras() {
        val markdown = convertido(FormatoSalida.MARKDOWN, "md").readText()

        assertTrue("Falta el título: $markdown", "# ${GeneradorFixtures.TITULO_CON_TABLA}" in markdown)
        assertTrue("Falta la cabecera de la tabla", "| Producto | Unidades | Precio |" in markdown)
        assertTrue("Falta la fila de guiones que hace que sea una tabla", "| --- | --- | --- |" in markdown)
    }

    @Test
    fun el_texto_plano_lleva_el_contenido_sin_adornos() {
        val texto = convertido(FormatoSalida.TEXTO, "txt").readText()

        assertTrue("Falta el título: $texto", GeneradorFixtures.TITULO_CON_TABLA in texto)
        assertTrue("La tabla tendría que salir en columnas", "Grapas\t40\t1,10" in texto)
        assertTrue("El texto plano no lleva almohadillas", "#" !in texto)
    }

    @Test
    fun el_odt_se_relee_y_devuelve_el_texto_esperado() {
        val odt = convertido(FormatoSalida.ODT, "odt")

        assertEquals(listOf(GeneradorFixtures.TITULO_CON_TABLA to 1), LecturaDeSalidas.titulosDeOdt(odt))
        assertTrue(
            "El párrafo no volvió: ${LecturaDeSalidas.parrafosDeOdt(odt)}",
            LecturaDeSalidas.parrafosDeOdt(odt).any { GeneradorFixtures.PARRAFO_CON_TABLA in it },
        )
        assertTrue("La tabla no volvió", "Grapas" in LecturaDeSalidas.textoDeOdt(odt))
    }

    @Test
    fun el_odt_lleva_el_mimetype_primero_y_sin_comprimir() {
        val entradas = LecturaDeSalidas.entradasDe(convertido(FormatoSalida.ODT, "odt"))

        // Lo exige OpenDocument, y sin ello el sistema no reconoce el fichero por sus
        // primeros bytes aunque LibreOffice lo abra igual.
        assertEquals("mimetype", entradas.first().first)
        assertEquals(ZipEntry.STORED, entradas.first().second)
    }

    @Test
    fun el_rtf_se_relee_y_devuelve_el_texto_esperado() {
        val texto = LecturaDeSalidas.textoDeRtf(convertido(FormatoSalida.RTF, "rtf"))

        assertTrue("Falta el título: $texto", GeneradorFixtures.TITULO_CON_TABLA in texto)
        assertTrue("Falta el párrafo", GeneradorFixtures.PARRAFO_CON_TABLA in texto)
        assertTrue("Falta la tabla", "Grapas" in texto && "1,10" in texto)
    }

    @Test
    fun los_acentos_sobreviven_al_rtf_y_al_odt() {
        val carpeta = carpeta("acentos")
        val documento =
            DocumentoEstructurado(
                listOf(
                    BloqueDeTexto.Titulo(TEXTO_CON_ACENTOS, nivel = 2),
                    BloqueDeTexto.Parrafo("Menos de 5 & más de 3 <o eso dicen>"),
                ),
            )

        val rtf = escrito(convertir.aDocumento(documento, FormatoSalida.RTF, carpeta, "acentos"))
        val odt = escrito(convertir.aDocumento(documento, FormatoSalida.ODT, carpeta, "acentos"))

        // RTF es ASCII puro: si el fichero trae bytes altos, es que no se escapó nada.
        assertTrue("El RTF tiene que ser ASCII", rtf.readBytes().all { it >= 0 })
        assertTrue("Los acentos no volvieron del RTF", TEXTO_CON_ACENTOS in LecturaDeSalidas.textoDeRtf(rtf))
        assertEquals(listOf(TEXTO_CON_ACENTOS to 2), LecturaDeSalidas.titulosDeOdt(odt))
        // Y el `&` y los `<>` no rompen el XML: si el escapado fallara, ni se abriría.
        assertTrue("Falta el párrafo con símbolos", "más de 3 <o eso dicen>" in LecturaDeSalidas.textoDeOdt(odt))
    }

    @Test
    fun el_csv_coincide_celda_a_celda_con_la_tabla_del_pdf() {
        val carpeta = carpeta("csv")
        val documento = convertir.leer(abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "csv.pdf"))))

        val ficheros = escritos(convertir.aTablas(documento, FormatoTabla.CSV, carpeta, "informe"))

        assertEquals(1, ficheros.size)
        assertEquals("informe-tabla-1.csv", ficheros.single().name)
        assertEquals(GeneradorFixtures.TABLA_ESPERADA, LecturaDeSalidas.celdasDeCsv(ficheros.single()))
        assertTrue(
            "Sin la marca de orden, Excel abre los acentos rotos",
            LecturaDeSalidas.tieneMarcaDeOrden(ficheros.single()),
        )
    }

    @Test
    fun el_xlsx_coincide_celda_a_celda_con_la_tabla_del_pdf() {
        val carpeta = carpeta("xlsx")
        val documento = convertir.leer(abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "xlsx.pdf"))))

        val ficheros = escritos(convertir.aTablas(documento, FormatoTabla.XLSX, carpeta, "informe"))

        assertEquals(1, ficheros.size)
        assertEquals("informe-tablas.xlsx", ficheros.single().name)
        assertEquals(GeneradorFixtures.TABLA_ESPERADA, LecturaDeSalidas.celdasDeXlsx(ficheros.single(), hoja = 1))
        // La pestaña dice de dónde salió: en un libro de ocho hojas es lo único que las
        // distingue.
        assertEquals(listOf("Tabla 1 (pag 1)"), LecturaDeSalidas.pestanasDeXlsx(ficheros.single()))
    }

    @Test
    fun el_recuento_dice_cuantas_tablas_hay_y_en_que_pagina_antes_de_convertir() {
        val documento = convertir.leer(abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "recuento.pdf"))))

        val recuento = convertir.recuentoDeTablas(documento)

        assertEquals(1, recuento.total)
        assertEquals(listOf(0), recuento.paginasDistintas)
        assertTrue("Deducidas por posición: hay que decirlo", recuento.algunaAproximada)
    }

    @Test
    fun un_documento_sin_tablas_lo_dice_y_no_escribe_nada() {
        val carpeta = carpeta("sin-tablas")
        val documento = convertir.leer(abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "liso.pdf"), 2)))

        val resultado = convertir.aTablas(documento, FormatoTabla.CSV, carpeta, "liso")

        assertEquals(ResultadoConversion.SinTablas, resultado)
        assertEquals(0, convertir.recuentoDeTablas(documento).total)
        assertTrue("No se escribe un CSV vacío para decir que no hay tablas", carpetaVacia(carpeta))
    }

    @Test
    fun un_escaneado_avisa_y_no_deja_ficheros_vacios() {
        val carpeta = carpeta("escaneado")
        val documento = convertir.leer(abrir(GeneradorFixtures.conImagenes(File(contexto.cacheDir, "fotos.pdf"), 2)))

        FormatoSalida.entries.forEach { formato ->
            assertEquals(
                "El formato $formato tendría que avisar en vez de escribir",
                ResultadoConversion.PareceEscaneado,
                convertir.aDocumento(documento, formato, carpeta, "fotos"),
            )
        }
        assertTrue("Un escaneado no produce ficheros: se dice y ya está", carpetaVacia(carpeta))
    }

    // -- Andamiaje ------------------------------------------------------------

    /** Convierte el fixture de la tabla al formato pedido y devuelve el fichero escrito. */
    private fun convertido(
        formato: FormatoSalida,
        etiqueta: String,
    ): File {
        val id = abrir(GeneradorFixtures.conTabla(File(contexto.cacheDir, "conversion-$etiqueta.pdf")))
        val documento = convertir.leer(id)
        return escrito(convertir.aDocumento(documento, formato, carpeta(etiqueta), "informe"))
    }

    private fun escrito(resultado: ResultadoConversion): File = escritos(resultado).single()

    private fun escritos(resultado: ResultadoConversion): List<File> {
        val escrito =
            resultado as? ResultadoConversion.Escrito ?: throw AssertionError("No se escribió nada: $resultado")
        return escrito.ficheros.map { File(it.identificador) }
    }

    /** Una carpeta limpia por prueba: si sobrara algo de la anterior, no se notaría. */
    private fun carpeta(etiqueta: String): OrigenDocumento {
        val destino = File(contexto.cacheDir, "salidas-$etiqueta")
        destino.deleteRecursively()
        destino.mkdirs()
        return OrigenDocumento.Privado(destino.absolutePath, destino.name)
    }

    private fun carpetaVacia(carpeta: OrigenDocumento): Boolean =
        File(carpeta.identificador).listFiles().orEmpty().isEmpty()

    private fun abrir(fichero: File): IdDocumento {
        val id = IdDocumento(fichero.name)
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, fichero.name), null)
        abiertos += id
        return id
    }

    private companion object {
        const val TEXTO_CON_ACENTOS = "Camión, ñandú y «€»"
    }
}
