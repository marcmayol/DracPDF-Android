package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.FormatoTexto
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import com.marcmayol.dracpdf.dominio.modelo.TipoFormulario
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * El servicio de formularios contra MuPDF de verdad, sobre PDF con AcroForm escritos
 * por el propio motor.
 */
@RunWith(AndroidJUnit4::class)
class MuPdfFormServiceTest {
    private lateinit var carpeta: File
    private lateinit var fuente: FuenteDocumentosAndroid
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var formularios: MuPdfFormService

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        carpeta = File(contexto.cacheDir, "fixtures").apply { mkdirs() }
        fuente = FuenteDocumentosAndroid(contexto.contentResolver)
        sesiones = SesionesMuPdf(fuente)
        repositorio = MuPdfDocumentRepository(sesiones, fuente)
        formularios = MuPdfFormService(sesiones)
    }

    @After
    fun recoger() {
        sesiones.cerrarTodo()
    }

    private fun abrir(
        nombre: String,
        xfa: GeneradorFormularios.Xfa = GeneradorFormularios.Xfa.NO,
    ): IdDocumento {
        val fichero = GeneradorFormularios.formulario(File(carpeta, nombre), xfa)
        val id = IdDocumento(nombre)
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, nombre))
        return id
    }

    private fun campo(
        campos: List<CampoFormulario>,
        nombre: String,
    ): CampoFormulario = campos.first { it.nombre == nombre }

    @Test
    fun reconoce_un_acroform_y_cuenta_sus_campos() {
        val id = abrir("acroform.pdf")

        val formulario = formularios.formulario(id)

        assertEquals(TipoFormulario.ACROFORM, formulario.tipo)
        // Siete campos, no ocho: el grupo de radio es un campo con dos botones, y
        // contarlos por separado sería contar apariencias como campos.
        assertEquals(GeneradorFormularios.CAMPOS, formulario.campos)
        assertTrue(formulario.esRellenable)
    }

    @Test
    fun un_pdf_sin_formulario_no_tiene_campos_que_ensenar() {
        val fichero = GeneradorFixtures.documento(File(carpeta, "liso.pdf"), 2)
        val id = IdDocumento("liso")
        repositorio.abrir(id, OrigenDocumento.Privado(fichero.absolutePath, "liso.pdf"))

        val formulario = formularios.formulario(id)

        assertEquals(TipoFormulario.NINGUNO, formulario.tipo)
        assertEquals(0, formulario.campos)
        assertFalse(formulario.esRellenable)
        assertTrue(formularios.camposDePagina(id, 0).isEmpty())
    }

    @Test
    fun los_campos_salen_de_la_pagina_en_la_que_estan() {
        val id = abrir("paginas.pdf")

        val primera = formularios.camposDePagina(id, 0)
        val segunda = formularios.camposDePagina(id, 1)

        assertEquals(GeneradorFormularios.WIDGETS_PRIMERA_PAGINA, primera.size)
        assertTrue("La segunda página no tiene campos y ha devuelto ${segunda.size}", segunda.isEmpty())
        assertTrue(primera.all { it.pagina == 0 })
    }

    @Test
    fun distingue_los_cinco_tipos_de_campo_del_estandar() {
        val campos = formularios.camposDePagina(abrir("tipos.pdf"), 0)

        assertEquals(TipoCampo.TEXTO, campo(campos, GeneradorFormularios.CAMPO_NOMBRE).tipo)
        assertEquals(TipoCampo.CASILLA, campo(campos, GeneradorFormularios.CAMPO_ACEPTA).tipo)
        assertEquals(TipoCampo.RADIO, campo(campos, GeneradorFormularios.CAMPO_SEXO).tipo)
        assertEquals(TipoCampo.COMBO, campo(campos, GeneradorFormularios.CAMPO_PROVINCIA).tipo)
        assertEquals(TipoCampo.LISTA, campo(campos, GeneradorFormularios.CAMPO_IDIOMA).tipo)
    }

    @Test
    fun los_botones_de_un_grupo_de_radio_comparten_nombre_pero_no_identidad() {
        val campos = formularios.camposDePagina(abrir("radio.pdf"), 0)

        val botones = campos.filter { it.nombre == GeneradorFormularios.CAMPO_SEXO }

        assertEquals(2, botones.size)
        assertEquals(2, botones.map { it.id }.toSet().size)
        assertTrue("Ninguno debería nacer marcado", botones.none { it.marcado })
    }

    @Test
    fun las_opciones_de_los_campos_de_eleccion_llegan_en_orden() {
        val campos = formularios.camposDePagina(abrir("opciones.pdf"), 0)

        val combo = campo(campos, GeneradorFormularios.CAMPO_PROVINCIA)
        val lista = campo(campos, GeneradorFormularios.CAMPO_IDIOMA)

        assertEquals(GeneradorFormularios.OPCIONES_PROVINCIA, combo.opciones)
        assertEquals(GeneradorFormularios.OPCIONES_IDIOMA, lista.opciones)
        assertEquals(GeneradorFormularios.OPCIONES_PROVINCIA.first(), combo.valor)
    }

    @Test
    fun las_restricciones_del_emisor_se_respetan() {
        val campos = formularios.camposDePagina(abrir("restricciones.pdf"), 0)

        val direccion = campo(campos, GeneradorFormularios.CAMPO_DIRECCION)
        assertTrue(direccion.multilinea)
        assertTrue(direccion.obligatorio)
        assertEquals(40, direccion.maxLongitud)

        val referencia = campo(campos, GeneradorFormularios.CAMPO_REFERENCIA)
        assertTrue("Un campo de sólo lectura no se puede editar", referencia.soloLectura)
        assertFalse(referencia.esEditable)
        assertEquals("ABC-123", referencia.valor)

        val nombre = campo(campos, GeneradorFormularios.CAMPO_NOMBRE)
        assertFalse(nombre.multilinea)
        assertFalse(nombre.esContrasena)
        assertNull(nombre.maxLongitud)
        assertEquals(FormatoTexto.NINGUNO, nombre.formatoTexto)
        assertTrue(nombre.esEditable)
    }

    @Test
    fun cada_campo_trae_el_marco_en_el_que_hay_que_pintarlo() {
        val campos = formularios.camposDePagina(abrir("marcos.pdf"), 0)

        campos.forEach { campo ->
            val marco = campo.marco
            assertTrue("El campo ${campo.nombre} salió sin ancho", marco.ancho > 0f)
            assertTrue("El campo ${campo.nombre} salió sin alto", marco.alto > 0f)
            assertTrue("El campo ${campo.nombre} se sale de la página", marco.x1 <= 596f && marco.y1 <= 843f)
        }
        // Y no están todos en el mismo sitio, que es lo que pasaría si el marco
        // viniera de la página y no de cada widget.
        assertEquals(campos.size, campos.map { it.marco.y0 }.toSet().size)
    }

    @Test
    fun un_xfa_puro_se_reconoce_y_no_promete_nada() {
        val id = abrir("xfa-puro.pdf", GeneradorFormularios.Xfa.PURO)

        val formulario = formularios.formulario(id)

        assertEquals(TipoFormulario.XFA_PURO, formulario.tipo)
        assertFalse(formulario.esRellenable)
    }

    @Test
    fun un_xfa_hibrido_se_rellena_por_el_acroform_de_debajo() {
        val id = abrir("xfa-hibrido.pdf", GeneradorFormularios.Xfa.HIBRIDO)

        val formulario = formularios.formulario(id)

        assertEquals(TipoFormulario.XFA_HIBRIDO, formulario.tipo)
        assertTrue(formulario.esRellenable)
        assertEquals(GeneradorFormularios.WIDGETS_PRIMERA_PAGINA, formularios.camposDePagina(id, 0).size)
    }

    @Test
    fun preguntar_por_un_documento_cerrado_es_un_error_de_dominio() {
        val id = abrir("cerrado.pdf")
        repositorio.cerrar(id)

        assertThrows(ErrorDocumento.NoEstaAbierto::class.java) { formularios.formulario(id) }
        assertThrows(ErrorDocumento.NoEstaAbierto::class.java) { formularios.camposDePagina(id, 0) }
    }
}
