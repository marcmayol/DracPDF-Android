package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfHerramientas
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.OrganizarPaginas
import com.marcmayol.dracpdf.dominio.modelo.Credencial
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.FirmaDelDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.SelloVisible
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.FormatoImagen
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import com.marcmayol.dracpdf.dominio.puertos.SignatureService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El ciclo completo de la caja de herramientas, **sin interfaz**: es el criterio F6.
 *
 * Cada operación se comprueba por su resultado y no por que no haya explotado. Unir
 * verifica el **orden** leyendo el texto de las páginas —cada una del fixture dice qué
 * número es—, proteger verifica que el resultado ya no abre sin contraseña, y
 * desproteger que lo que sale es el mismo documento y no uno parecido.
 */
@RunWith(AndroidJUnit4::class)
class CicloHerramientasTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var taller: File
    private lateinit var herramientas: MuPdfHerramientas

    @Before
    fun montarElTaller() {
        taller = File(contexto.cacheDir, "ciclo-herramientas").also { it.deleteRecursively() }
        taller.mkdirs()
        herramientas =
            MuPdfHerramientas(
                ficheros = FicherosDeOrigen(contexto.contentResolver, File(taller, "copias")),
                salidas = SalidasDeHerramienta(contexto.contentResolver),
                carpetaTemporal = File(taller, "temporales"),
            )
    }

    // ------------------------------------------------------------------------ unir

    @Test
    fun unir_tres_documentos_respeta_el_orden_en_que_se_eligieron() {
        val primero = documento("uno.pdf", paginas = 2)
        val segundo = documento("dos.pdf", paginas = 3)
        val tercero = documento("tres.pdf", paginas = 1)
        val destino = destino("unido.pdf")

        herramientas.unir(listOf(primero, segundo, tercero), destino)

        assertEquals(6, herramientas.paginasDe(destino))
        // Que estén todas no basta: lo que se comprueba es **en qué orden**, y con tres
        // documentos el del medio es el que delata un orden mal montado.
        assertEquals(
            listOf(
                "Pagina 1 de 2",
                "Pagina 2 de 2",
                "Pagina 1 de 3",
                "Pagina 2 de 3",
                "Pagina 3 de 3",
                "Pagina 1 de 1",
            ),
            textoDe(destino),
        )
    }

    @Test
    fun cancelar_al_unir_no_deja_el_destino_escrito() {
        val destino = destino("cancelado.pdf")

        herramientas.unir(
            listOf(documento("a.pdf", paginas = 3), documento("b.pdf", paginas = 3)),
            destino,
            // Se corta a la segunda página, con el resultado a medio montar.
            progreso = { hechas, _ -> hechas < 2 },
        )

        assertFalse("Cancelar dejó un PDF a medias", File(destino.identificador).exists())
    }

    // -------------------------------------------------------- organizar y dividir

    @Test
    fun reorganizar_extrae_reordena_y_gira_a_la_vez() {
        val origen = documento("original.pdf", paginas = 4)
        val destino = destino("organizado.pdf")

        // Se queda con tres, en otro orden, y una girada un cuarto de vuelta.
        herramientas.reorganizar(
            origen = origen,
            paginas =
                listOf(
                    PaginaOrdenada(original = 3),
                    PaginaOrdenada(original = 0, giro = 90),
                    PaginaOrdenada(original = 2),
                ),
            destino = destino,
        )

        assertEquals(3, herramientas.paginasDe(destino))
        assertEquals(listOf("Pagina 4 de 4", "Pagina 1 de 4", "Pagina 3 de 4"), textoDe(destino))
    }

    @Test
    fun dividir_por_rangos_reparte_todas_las_paginas() {
        val origen = documento("entero.pdf", paginas = 6)
        val trozos = listOf(destino("parte1.pdf"), destino("parte2.pdf"))

        listOf(1..2, 3..6).forEachIndexed { indice, rango ->
            herramientas.reorganizar(
                origen = origen,
                paginas = rango.map { PaginaOrdenada(original = it - 1) },
                destino = trozos[indice],
            )
        }

        assertEquals(2, herramientas.paginasDe(trozos[0]))
        assertEquals(4, herramientas.paginasDe(trozos[1]))
        assertEquals(listOf("Pagina 1 de 6", "Pagina 2 de 6"), textoDe(trozos[0]))
        assertEquals("Pagina 3 de 6", textoDe(trozos[1]).first())
    }

    // ------------------------------------------------- proteger y desproteger

    @Test
    fun proteger_reabrir_y_desproteger_devuelve_el_mismo_documento() {
        val origen = documento("claro.pdf", paginas = 3)
        val protegido = destino("protegido.pdf")
        val liberado = destino("liberado.pdf")

        herramientas.proteger(origen, protegido, CONTRASENA)

        // Ya no se deja leer a pelo: pedirle las páginas exige la contraseña.
        assertThrows(ErrorDocumento.NecesitaContrasena::class.java) { herramientas.paginasDe(protegido) }

        herramientas.desproteger(protegido, liberado, CONTRASENA)

        assertEquals(3, herramientas.paginasDe(liberado))
        // Y es el mismo documento, no uno con las mismas páginas: el texto coincide.
        assertEquals(textoDe(origen), textoDe(liberado))
    }

    @Test
    fun desproteger_con_la_contrasena_equivocada_se_niega() {
        val protegido = destino("con-llave.pdf")
        herramientas.proteger(documento("otro.pdf", paginas = 1), protegido, CONTRASENA)

        assertThrows(ErrorDocumento.ContrasenaIncorrecta::class.java) {
            herramientas.desproteger(protegido, destino("intento.pdf"), "la que no es")
        }
    }

    // ------------------------------------------------------------------ comprimir

    @Test
    fun comprimir_un_pdf_con_imagenes_reporta_la_reduccion() {
        // Las imágenes son lo que de verdad ocupa en un PDF, y lo que la compresión
        // tiene que morder: un documento de puro texto encoge por deduplicación y no
        // demuestra que `compress-images` haga nada.
        val origen = conImagenes("con-fotos.pdf", paginas = 6)
        val destino = destino("con-fotos-apretado.pdf")

        val reduccion = herramientas.comprimir(origen, destino)

        assertEquals(File(origen.identificador).length(), reduccion.antes)
        assertEquals(File(destino.identificador).length(), reduccion.despues)
        assertTrue(
            "No encogió un PDF con imágenes: ${reduccion.antes} → ${reduccion.despues}",
            reduccion.despues < reduccion.antes,
        )
        assertEquals(6, herramientas.paginasDe(destino))
    }

    @Test
    fun comprimir_mide_el_antes_y_el_despues_de_verdad() {
        // Un documento con las mismas páginas repetidas: hay mucho que deduplicar, que
        // es justo lo que la compresión sabe hacer.
        val origen = documento("gordo.pdf", paginas = 40)
        val destino = destino("apretado.pdf")

        val reduccion = herramientas.comprimir(origen, destino)

        assertEquals(File(origen.identificador).length(), reduccion.antes)
        assertEquals(File(destino.identificador).length(), reduccion.despues)
        assertTrue("No encogió: ${reduccion.antes} → ${reduccion.despues}", reduccion.despues < reduccion.antes)
        // Y sigue siendo el mismo documento después de apretarlo.
        assertEquals(40, herramientas.paginasDe(destino))
    }

    // ------------------------------------------------------------------ convertir

    @Test
    fun convertir_a_imagenes_escribe_una_por_pagina_y_con_su_cabecera() {
        val origen = documento("para-imagenes.pdf", paginas = 3)
        val carpeta = OrigenDocumento.Privado(File(taller, "imagenes").absolutePath, "imagenes")

        val escritas =
            herramientas.aImagenes(
                origen = origen,
                paginas = listOf(0, 2),
                carpeta = carpeta,
                ajustes = AjustesImagen(FormatoImagen.PNG, escala = 1f),
            )

        assertEquals(2, escritas.size)
        escritas.forEach { imagen ->
            val bytes = File(imagen.identificador).readBytes()
            assertTrue("«${imagen.identificador}» no es un PNG", bytes.take(4) == CABECERA_PNG)
        }
    }

    @Test
    fun convertir_a_texto_saca_lo_que_pone_en_las_paginas() {
        val origen = documento("con-texto.pdf", paginas = 2)
        val destino = destino("texto.txt")

        val habiaTexto = herramientas.aTexto(origen, destino)

        assertTrue("El fixture tiene texto y dijo que no", habiaTexto)
        val escrito = File(destino.identificador).readText()
        assertTrue("No salió el texto de la primera página", "Pagina 1 de 2" in escrito)
        assertTrue("No salió el texto de la segunda", "Pagina 2 de 2" in escrito)
    }

    // ----------------------------------------------------------- lo que no se toca

    @Test
    fun el_destino_no_puede_ser_el_origen() {
        val origen = documento("mismo.pdf", paginas = 2)

        // Leer mientras se escribe el mismo fichero no da un PDF malo: da un PDF
        // perdido. Lo para el caso de uso, y por eso aquí se comprueba a ese nivel.
        val caso = OrganizarPaginas(herramientas, FirmasQueNoVenNada)
        assertThrows(IllegalArgumentException::class.java) {
            caso(origen, listOf(PaginaOrdenada(0)), origen)
        }
    }

    @Test
    fun comprimir_no_cambia_el_original() {
        val origen = documento("intacto.pdf", paginas = 5)
        val antes = File(origen.identificador).readBytes()

        herramientas.comprimir(origen, destino("copia-apretada.pdf"))

        assertTrue("La compresión tocó el original", antes.contentEquals(File(origen.identificador).readBytes()))
        assertNotEquals(0, antes.size)
    }

    // ------------------------------------------------------------------- utilería

    private fun documento(
        nombre: String,
        paginas: Int,
    ): OrigenDocumento {
        val fichero = GeneradorFixtures.documento(File(taller, nombre), paginas = paginas)
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    private fun destino(nombre: String) = OrigenDocumento.Privado(File(taller, nombre).absolutePath, nombre)

    /**
     * Un PDF con una imagen a toda página en cada hoja, sin comprimir.
     *
     * Se genera aquí y no en el generador común porque sólo lo necesita la compresión:
     * el resto de herramientas no distingue una página con foto de una con texto.
     */
    private fun conImagenes(
        nombre: String,
        paginas: Int,
    ): OrigenDocumento {
        val fichero = GeneradorFixtures.conImagenes(File(taller, nombre), paginas = paginas)
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    /** La primera línea de texto de cada página, que en el fixture dice qué página es. */
    private fun textoDe(origen: OrigenDocumento): List<String> {
        val txt = destino("volcado-${origen.identificador.hashCode()}.txt")
        herramientas.aTexto(origen, txt, Progreso.NINGUNO)
        return File(txt.identificador)
            .readText()
            .lines()
            .filter { it.startsWith("Pagina ") }
    }

    private companion object {
        const val CONTRASENA = "abre-sesamo"
        val CABECERA_PNG = listOf<Byte>(-119, 80, 78, 71)
    }
}

/**
 * Para los casos de uso que piden el servicio de firmas y aquí no viene al caso: los
 * ficheros de este test no llevan ninguna.
 */
private object FirmasQueNoVenNada : SignatureService {
    override fun firmar(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        credencial: Credencial,
        sello: SelloVisible?,
    ) = error("No se firma en este test")

    override fun verificar(origen: OrigenDocumento): List<FirmaDelDocumento> = emptyList()
}
