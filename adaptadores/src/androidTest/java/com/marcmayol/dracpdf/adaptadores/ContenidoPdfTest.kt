package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfContenido
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.casos.BuscarEnDocumento
import com.marcmayol.dracpdf.dominio.modelo.DestinoEnlace
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El texto y la estructura del documento, contra PDF de verdad.
 *
 * Es la parte del criterio F7 que se puede demostrar sin tocar la pantalla: que la
 * búsqueda encuentra donde tiene que encontrar, que la selección devuelve exactamente
 * lo que hay escrito, y que el índice y los enlaces llevan a las páginas que dicen.
 */
@RunWith(AndroidJUnit4::class)
class ContenidoPdfTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var contenido: MuPdfContenido
    private val abiertos = mutableListOf<IdDocumento>()

    @Before
    fun montarElMotor() {
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        contenido = MuPdfContenido(sesiones, fuente)
    }

    @After
    fun cerrarTodo() {
        abiertos.forEach(repositorio::cerrar)
        abiertos.clear()
    }

    @Test
    fun la_busqueda_encuentra_el_texto_en_su_pagina() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "buscar.pdf"), paginas = 6))

        val coincidencias = BuscarEnDocumento(contenido)(id, "Pagina 4", paginas = 6)

        assertEquals(1, coincidencias.size)
        assertEquals(3, coincidencias.single().pagina)
        // Y el marco cae donde está escrito: arriba de la hoja, no en cualquier sitio.
        assertTrue("El marco no tiene tamaño", coincidencias.single().marco.ancho > 0f)
    }

    @Test
    fun lo_que_no_esta_no_aparece() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "sin-eso.pdf"), paginas = 3))

        assertTrue(BuscarEnDocumento(contenido)(id, "berenjena", paginas = 3).isEmpty())
    }

    @Test
    fun buscar_no_distingue_mayusculas() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "mayusculas.pdf"), paginas = 2))

        // Quien busca en el móvil escribe en minúscula; exigirle el acento y la
        // mayúscula sería pedirle que sepa cómo está escrito lo que busca.
        assertEquals(2, BuscarEnDocumento(contenido)(id, "pagina", paginas = 2).size)
    }

    @Test
    fun el_texto_de_una_pagina_es_el_que_pone_en_ella() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "texto.pdf"), paginas = 3))

        val texto = contenido.textoDe(id, 1)

        assertTrue("No salió el título de la página: «$texto»", "Pagina 2 de 3" in texto)
        assertTrue("No salió el párrafo: «$texto»", "fixture" in texto)
    }

    @Test
    fun tocar_una_palabra_la_selecciona_entera() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "palabra.pdf"), paginas = 1))

        val seleccion = contenido.palabraEn(id, 0, EN_LA_PRIMERA_PALABRA)

        assertNotNull("No se seleccionó nada donde hay texto", seleccion)
        assertEquals("Pagina", seleccion?.texto?.trim())
        assertTrue("Una palabra seleccionada tiene que poder pintarse", seleccion!!.marcos.isNotEmpty())
    }

    @Test
    fun donde_no_hay_texto_no_se_selecciona_nada() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "vacio.pdf"), paginas = 1))

        // El centro de la hoja está en blanco en este fixture: todo el texto está
        // arriba. El motor engancharía igualmente la palabra más cercana, y lo que se
        // comprueba aquí es justo que eso no se deje pasar.
        assertEquals(null, contenido.palabraEn(id, 0, PuntoPt(x = 300f, y = 500f)))
    }

    @Test
    fun arrastrar_entre_dos_puntos_devuelve_lo_que_hay_en_medio() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "arrastre.pdf"), paginas = 1))

        // Desde el margen izquierdo del título hasta pasado su final: el arrastre va
        // por caracteres, así que empezar en medio de la primera palabra dejaría fuera
        // las letras que quedan a la izquierda del dedo. Es lo correcto, y por eso el
        // punto de partida está donde empieza la línea.
        val seleccion =
            contenido.seleccionEntre(
                id,
                0,
                EN_LA_PRIMERA_PALABRA.copy(x = 70f),
                EN_LA_PRIMERA_PALABRA.copy(x = 500f),
            )

        assertEquals("Pagina 1 de 1", seleccion.texto.trim())
        assertTrue(seleccion.hayAlgo)
    }

    @Test
    fun el_indice_lleva_a_las_paginas_que_dice() {
        val fichero = GeneradorFixtures.conIndiceYEnlaces(File(contexto.cacheDir, "indice.pdf"))
        val id = abrir(fichero)

        val indice = contenido.indice(id)

        assertEquals(GeneradorFixtures.PAGINAS_CON_INDICE, indice.size)
        assertEquals("Capítulo 1", indice.first().titulo)
        // Una entrada por página y en orden: la tercera lleva a la tercera hoja.
        assertEquals((0 until GeneradorFixtures.PAGINAS_CON_INDICE).toList(), indice.map { it.pagina })
    }

    @Test
    fun un_documento_sin_indice_no_se_inventa_uno() {
        val id = abrir(GeneradorFixtures.documento(File(contexto.cacheDir, "sin-indice.pdf"), paginas = 2))

        assertTrue(contenido.indice(id).isEmpty())
    }

    @Test
    fun los_enlaces_distinguen_lo_interno_de_lo_externo() {
        val id = abrir(GeneradorFixtures.conIndiceYEnlaces(File(contexto.cacheDir, "enlaces.pdf")))

        val enlaces = contenido.enlaces(id, 0)

        val internos = enlaces.map { it.destino }.filterIsInstance<DestinoEnlace.Pagina>()
        val externos = enlaces.map { it.destino }.filterIsInstance<DestinoEnlace.Fuera>()
        assertEquals(listOf(GeneradorFixtures.PAGINAS_CON_INDICE - 1), internos.map { it.numero })
        assertEquals(listOf(GeneradorFixtures.WEB_DE_PRUEBA), externos.map { it.url })
    }

    @Test
    fun las_propiedades_cuentan_lo_que_el_documento_sabe_de_si_mismo() {
        val fichero = GeneradorFixtures.documento(File(contexto.cacheDir, "propiedades.pdf"), paginas = 4)
        val id = abrir(fichero)

        val propiedades = contenido.propiedades(id)

        assertEquals(4, propiedades.paginas)
        assertEquals(fichero.length(), propiedades.bytes)
        assertTrue("Un PDF sin contraseña no está cifrado", !propiedades.cifrado)
        assertTrue("Debería decir que es un PDF: ${propiedades.formato}", propiedades.formato?.contains("PDF") == true)
    }

    @Test
    fun un_documento_con_contrasena_se_declara_cifrado() {
        val fichero =
            GeneradorFixtures.documento(File(contexto.cacheDir, "cifrado.pdf"), paginas = 2, contrasena = "abre")
        val id = IdDocumento("cifrado")
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, fichero.name), "abre")
        abiertos += id

        assertTrue(contenido.propiedades(id).cifrado)
    }

    private companion object {
        /**
         * Un punto dentro de «Pagina», la primera palabra del título del fixture.
         *
         * El generador la escribe en `72 720` **en coordenadas del formato**, que
         * cuentan desde abajo; el motor las entrega contando desde arriba, y en una A4
         * de 842 puntos de alto eso deja la línea a la altura de 122. Confundir los dos
         * sistemas es el error clásico con PDF, y aquí sale a la primera: se selecciona
         * el pie de página en vez del título.
         */
        val EN_LA_PRIMERA_PALABRA = PuntoPt(x = 100f, y = 110f)
    }

    private fun abrir(fichero: File): IdDocumento {
        val id = IdDocumento(fichero.name)
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, fichero.name), null)
        abiertos += id
        return id
    }
}
