package com.marcmayol.dracpdf.adaptadores

import android.print.PageRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.impresion.ImpresionPdf
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfHerramientas
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Lo que se le entrega al sistema de impresión.
 *
 * El criterio F7 pide imprimir **con el rango pedido**, y este es el punto donde eso
 * se cumple o se incumple en silencio: el sistema no protesta si le das el documento
 * entero cuando el usuario eligió tres páginas, simplemente imprime de más.
 *
 * Se prueban las dos mitades por separado —la traducción del rango y el recorte del
 * PDF— porque la costura entre ellas es el propio API de Android, cuyo
 * `WriteResultCallback` ni siquiera se puede construir desde un test.
 */
@RunWith(AndroidJUnit4::class)
class ImpresionRangoTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    private val adaptador = ImpresionPdf(nombre = "prueba.pdf", paginas = 8, ficheroDe = { error("no hace falta") })

    @Test
    fun un_rango_se_traduce_a_sus_paginas_contando_desde_cero() {
        // El sistema cuenta desde cero en `PageRange` aunque el usuario escriba «3-5».
        assertEquals(listOf(2, 3, 4), adaptador.paginasDe(arrayOf(PageRange(2, 4))))
    }

    @Test
    fun varios_rangos_sueltos_se_juntan_en_orden() {
        assertEquals(listOf(0, 1, 5), adaptador.paginasDe(arrayOf(PageRange(0, 1), PageRange(5, 5))))
    }

    @Test
    fun todas_las_paginas_no_es_una_lista_de_paginas() {
        // `null` es lo que permite entregar el fichero original tal cual, sin
        // reescribirlo para nada.
        assertNull(adaptador.paginasDe(arrayOf(PageRange.ALL_PAGES)))
        assertNull(adaptador.paginasDe(null))
        assertNull("Pedir las ocho de ocho es pedirlas todas", adaptador.paginasDe(arrayOf(PageRange(0, 7))))
    }

    @Test
    fun el_pdf_que_se_entrega_lleva_solo_las_paginas_del_rango() {
        val original = GeneradorFixtures.documento(File(contexto.cacheDir, "para-imprimir.pdf"), paginas = 8)
        val recorte = File(contexto.cacheDir, "recorte-impresion.pdf")
        val pedidas = adaptador.paginasDe(arrayOf(PageRange(2, 4)))!!

        herramientas().reorganizar(
            origen = OrigenDocumento.Privado(original.absolutePath, original.name),
            paginas = pedidas.map { PaginaOrdenada(original = it) },
            destino = OrigenDocumento.Privado(recorte.absolutePath, recorte.name),
        )

        assertEquals(3, paginasDe(recorte))
    }

    private fun herramientas() =
        MuPdfHerramientas(
            ficheros = FicherosDeOrigen(contexto.contentResolver, contexto.cacheDir),
            salidas = SalidasDeHerramienta(contexto.contentResolver),
            carpetaTemporal = contexto.cacheDir,
        )

    private fun paginasDe(pdf: File): Int {
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        val repositorio = MuPdfDocumentRepository(SesionesMuPdf(fuente), fuente)
        val id = IdDocumento("contar-${pdf.name}")
        val abierto = repositorio.abrir(id, OrigenDocumento.Privado(pdf.absolutePath, pdf.name), null)
        return abierto.paginas.also { repositorio.cerrar(id) }
    }
}
