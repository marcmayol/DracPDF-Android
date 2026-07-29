package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/**
 * Un formulario oficial de verdad, no uno escrito aquí.
 *
 * El fixture que genera MuPDF es limpio y previsible, que es exactamente lo que un
 * impreso real no es. Este test existe porque el W-9 del IRS trae tres cosas que el
 * fixture no podía traer: nombres de campo jerárquicos de tres niveles, XFA por
 * encima de su AcroForm, y un árbol de campos donde los terminales vienen fusionados
 * con su widget. Lo tercero destapó un fallo: contar campos mirando el `/Subtype`
 * daba **un** campo donde hay veintitrés.
 *
 * El documento no está en el repositorio —es de terceros y no se versionan
 * binarios—, así que si no se ha descargado, este test se salta y dice cómo.
 */
@RunWith(AndroidJUnit4::class)
class FormularioOficialTest {
    private lateinit var carpeta: File
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var formularios: MuPdfFormService

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        carpeta = File(contexto.cacheDir, "oficiales").apply { mkdirs() }
        val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        formularios = MuPdfFormService(sesiones)
    }

    @After
    fun recoger() {
        sesiones.cerrarTodo()
    }

    /**
     * Saca el formulario de los assets del APK de test a un fichero de verdad, o
     * salta el test si no se descargó.
     */
    private fun oficial(nombre: String): File {
        val destino = File(carpeta, nombre)
        if (destino.exists()) return destino

        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val existe =
            try {
                assets.open(nombre).use { entrada -> destino.outputStream().use(entrada::copyTo) }
                true
            } catch (e: FileNotFoundException) {
                false
            }

        assumeTrue(
            "Falta $nombre. Ejecuta: python scripts/descargar_formularios_oficiales.py",
            existe,
        )
        return destino
    }

    private fun abrir(fichero: File): IdDocumento =
        IdDocumento(fichero.name).also {
            repositorio.abrir(it, OrigenDocumento.Privado(fichero.absolutePath, fichero.name))
        }

    private fun campos(id: IdDocumento): List<CampoFormulario> = formularios.camposDePagina(id, 0)

    @Test
    fun el_w9_es_un_xfa_hibrido_y_se_avisa_de_ello() {
        val id = abrir(oficial(W9))

        val formulario = formularios.formulario(id)

        // Es el caso que la Fase 2 decidió no tratar como binario: lleva XFA y lleva
        // AcroForm debajo, así que se rellena por la capa compatible y se avisa de
        // que Adobe podría enseñar la otra.
        assertEquals(TipoFormulario.XFA_HIBRIDO, formulario.tipo)
        assertTrue("Un híbrido sí se rellena", formulario.esRellenable)
    }

    @Test
    fun cuenta_los_campos_del_arbol_jerarquico_y_no_su_raiz() {
        val id = abrir(oficial(W9))

        // Veintitrés, no uno. El árbol es topmostSubform → Page1 → los campos, con un
        // nivel más para el grupo de casillas, y todos los terminales son a la vez
        // widgets. Ésta es la aserción que habría faltado sin un formulario real.
        assertEquals(CAMPOS_W9, formularios.formulario(id).campos)
    }

    @Test
    fun los_nombres_jerarquicos_llegan_enteros() {
        val id = abrir(oficial(W9))

        val nombres = campos(id).map { it.nombre }

        assertEquals(WIDGETS_PRIMERA_PAGINA_W9, nombres.size)
        assertTrue(
            "Los nombres tienen que venir completos, con su ruta: ${nombres.first()}",
            nombres.first().startsWith("topmostSubform[0].Page1[0]."),
        )
        // Y son distintos entre sí, que es lo que permite identificarlos.
        assertEquals(nombres.size, nombres.toSet().size)
    }

    @Test
    fun distingue_los_textos_de_las_casillas_del_impreso() {
        val id = abrir(oficial(W9))
        val delaPagina = campos(id)

        val textos = delaPagina.filter { it.tipo == TipoCampo.TEXTO }
        val casillas = delaPagina.filter { it.tipo == TipoCampo.CASILLA }

        assertTrue("El W-9 tiene campos de texto y salieron ${textos.size}", textos.size >= 10)
        assertTrue("El W-9 tiene casillas y salieron ${casillas.size}", casillas.size >= 5)
        assertTrue("Ninguna casilla nace marcada en un impreso en blanco", casillas.none { it.marcado })
    }

    @Test
    fun cada_campo_cae_dentro_de_su_pagina() {
        val id = abrir(oficial(W9))
        val tamano = repositorio.tamanoPagina(id, 0)

        campos(id).forEach { campo ->
            val marco = campo.marco
            assertTrue("«${campo.nombre}» tiene ancho cero", marco.ancho > 0f)
            assertTrue("«${campo.nombre}» tiene alto cero", marco.alto > 0f)
            assertTrue(
                "«${campo.nombre}» se sale de la página (${marco.x1} x ${marco.y1})",
                marco.x1 <= tamano.ancho + 1f && marco.y1 <= tamano.alto + 1f,
            )
        }
    }

    @Test
    fun se_rellena_se_guarda_y_lo_escrito_sigue_ahi_al_reabrirlo() {
        val fichero = oficial(W9)
        // Sobre una copia: el original bajado se deja intacto para los demás tests.
        val copia = File(carpeta, "w9-relleno.pdf").apply { fichero.copyTo(this, overwrite = true) }
        val id = abrir(copia)

        val nombre = campos(id).first { it.tipo == TipoCampo.TEXTO && it.esEditable }
        val casilla = campos(id).first { it.tipo == TipoCampo.CASILLA && it.esEditable }

        formularios.escribirTexto(id, nombre.id, "Marc Mayol Orell")
        formularios.alternar(id, casilla.id)
        assertTrue(repositorio.tieneCambiosSinGuardar(id))
        repositorio.guardarIncremental(id)

        sesiones.cerrarTodo()
        preparar()
        val reabierto = abrir(copia)
        val despues = campos(reabierto)

        assertEquals("Marc Mayol Orell", despues.first { it.nombre == nombre.nombre }.valor)
        assertTrue(despues.first { it.nombre == casilla.nombre }.marcado)
        // Y el resto del impreso sigue en blanco: no se ha tocado lo que no se tocó.
        assertFalse(despues.first { it.nombre != nombre.nombre && it.tipo == TipoCampo.TEXTO }.valor.isNotEmpty())
    }

    private companion object {
        const val W9 = "w9.pdf"

        /** Los campos del impreso, contando el árbol entero de `/Fields`. */
        const val CAMPOS_W9 = 23

        /** Todos caen en la primera página; las otras cinco son instrucciones. */
        const val WIDGETS_PRIMERA_PAGINA_W9 = 23
    }
}
