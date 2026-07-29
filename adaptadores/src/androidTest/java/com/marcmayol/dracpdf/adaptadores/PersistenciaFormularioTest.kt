package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFormularios
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.modelo.CampoFormulario
import com.marcmayol.dracpdf.dominio.modelo.IdCampo
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.TipoCampo
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Rellenar, guardar, **cerrar**, y volver a abrir el fichero desde cero.
 *
 * Éste es el test que sostiene el criterio de la fase, y el cierre de por medio es
 * lo que le da valor: releer el documento que sigue abierto sólo demostraría que el
 * valor está en memoria. Lo que hay que demostrar es que está en el fichero, que es
 * lo que verá la administración que lo reciba.
 *
 * Se comprueba además que el fichero **crece y no se reescribe**: el guardado es
 * incremental, así que los bytes de la revisión anterior tienen que seguir donde
 * estaban. De eso depende, en la Fase 4, que una firma previa siga siendo válida.
 */
@RunWith(AndroidJUnit4::class)
class PersistenciaFormularioTest {
    private lateinit var carpeta: File
    private lateinit var fuente: FuenteDocumentosAndroid
    private lateinit var sesiones: SesionesMuPdf
    private lateinit var repositorio: MuPdfDocumentRepository
    private lateinit var formularios: MuPdfFormService

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        carpeta = File(contexto.cacheDir, "persistencia").apply { mkdirs() }
        montar()
    }

    /** Monta la pila entera de nuevo, como si la aplicación acabara de arrancar. */
    private fun montar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
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
        fichero: File,
        id: String,
    ): IdDocumento =
        IdDocumento(id).also {
            repositorio.abrir(it, OrigenDocumento.Privado(fichero.absolutePath, fichero.name))
        }

    /** Cierra todo y vuelve a abrir el fichero con una pila nueva. */
    private fun reabrir(
        fichero: File,
        id: String,
    ): IdDocumento {
        sesiones.cerrarTodo()
        montar()
        return abrir(fichero, id)
    }

    private fun campo(
        id: IdDocumento,
        nombre: String,
    ): CampoFormulario {
        val delaPagina = formularios.camposDePagina(id, 0)
        return delaPagina.firstOrNull { it.nombre == nombre }
            ?: throw AssertionError("No hay ningún campo «$nombre». Hay: ${delaPagina.map { it.nombre }}")
    }

    private fun idDe(
        id: IdDocumento,
        nombre: String,
    ): IdCampo = campo(id, nombre).id

    @Test
    fun un_campo_de_texto_sobrevive_a_cerrar_y_reabrir() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "texto.pdf"))
        val id = abrir(fichero, "texto")

        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_NOMBRE), "Marc Mayol")
        assertTrue("El motor tiene que saber que hay cambios", repositorio.tieneCambiosSinGuardar(id))
        repositorio.guardarIncremental(id)

        val reabierto = reabrir(fichero, "texto-2")
        assertEquals("Marc Mayol", campo(reabierto, GeneradorFormularios.CAMPO_NOMBRE).valor)
    }

    @Test
    fun una_casilla_marcada_sobrevive_a_cerrar_y_reabrir() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "casilla.pdf"))
        val id = abrir(fichero, "casilla")

        assertFalse(campo(id, GeneradorFormularios.CAMPO_ACEPTA).marcado)
        formularios.alternar(id, idDe(id, GeneradorFormularios.CAMPO_ACEPTA))
        repositorio.guardarIncremental(id)

        val reabierto = reabrir(fichero, "casilla-2")
        assertTrue("La casilla tenía que quedar marcada", campo(reabierto, GeneradorFormularios.CAMPO_ACEPTA).marcado)
    }

    @Test
    fun elegir_un_radio_deja_marcado_uno_solo_del_grupo() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "radio.pdf"))
        val id = abrir(fichero, "radio")

        val botones = formularios.camposDePagina(id, 0).filter { it.tipo == TipoCampo.RADIO }
        formularios.alternar(id, botones.first().id)
        repositorio.guardarIncremental(id)

        val reabierto = reabrir(fichero, "radio-2")
        val despues = formularios.camposDePagina(reabierto, 0).filter { it.tipo == TipoCampo.RADIO }

        // Uno marcado y sólo uno: elegir en un grupo de radio significa desmarcar a
        // los hermanos, y de eso se encarga el motor porque el grupo lo sabe el PDF.
        assertEquals(1, despues.count { it.marcado })
        assertTrue(despues.first().marcado)
    }

    @Test
    fun un_combo_y_una_lista_guardan_la_opcion_elegida() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "eleccion.pdf"))
        val id = abrir(fichero, "eleccion")

        val provincia = GeneradorFormularios.OPCIONES_PROVINCIA.last()
        val idioma = GeneradorFormularios.OPCIONES_IDIOMA.last()
        formularios.elegirOpcion(id, idDe(id, GeneradorFormularios.CAMPO_PROVINCIA), provincia)
        formularios.elegirOpcion(id, idDe(id, GeneradorFormularios.CAMPO_IDIOMA), idioma)
        repositorio.guardarIncremental(id)

        val reabierto = reabrir(fichero, "eleccion-2")
        assertEquals(provincia, campo(reabierto, GeneradorFormularios.CAMPO_PROVINCIA).valor)
        assertEquals(idioma, campo(reabierto, GeneradorFormularios.CAMPO_IDIOMA).valor)
    }

    @Test
    fun el_formulario_entero_relleno_de_una_vez_sobrevive() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "entero.pdf"))
        val id = abrir(fichero, "entero")

        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_NOMBRE), "Marc")
        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_DIRECCION), "Carrer Major 1")
        formularios.alternar(id, idDe(id, GeneradorFormularios.CAMPO_ACEPTA))
        formularios.elegirOpcion(
            id,
            idDe(id, GeneradorFormularios.CAMPO_PROVINCIA),
            GeneradorFormularios.OPCIONES_PROVINCIA[1],
        )
        repositorio.guardarIncremental(id)

        val reabierto = reabrir(fichero, "entero-2")
        assertEquals("Marc", campo(reabierto, GeneradorFormularios.CAMPO_NOMBRE).valor)
        assertEquals("Carrer Major 1", campo(reabierto, GeneradorFormularios.CAMPO_DIRECCION).valor)
        assertTrue(campo(reabierto, GeneradorFormularios.CAMPO_ACEPTA).marcado)
        assertEquals(
            GeneradorFormularios.OPCIONES_PROVINCIA[1],
            campo(reabierto, GeneradorFormularios.CAMPO_PROVINCIA).valor,
        )
        // Y lo que no se tocó sigue como estaba.
        assertEquals("ABC-123", campo(reabierto, GeneradorFormularios.CAMPO_REFERENCIA).valor)
    }

    @Test
    fun el_guardado_anade_al_final_y_no_reescribe_lo_de_antes() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "incremental.pdf"))
        val original = fichero.readBytes()
        val id = abrir(fichero, "incremental")

        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_NOMBRE), "Marc")
        repositorio.guardarIncremental(id)

        val despues = fichero.readBytes()
        assertTrue(
            "El fichero tenía que crecer y midió ${despues.size} frente a ${original.size}",
            despues.size > original.size,
        )
        // Byte a byte: la revisión anterior sigue intacta al principio del fichero.
        // Es lo que permitirá que una firma previa siga siendo válida en la Fase 4.
        assertArrayEqualsMensaje(original, despues.copyOfRange(0, original.size))
    }

    @Test
    fun guardar_dos_veces_seguidas_deja_los_dos_cambios() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "dosveces.pdf"))
        val id = abrir(fichero, "dosveces")

        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_NOMBRE), "Primero")
        repositorio.guardarIncremental(id)
        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_DIRECCION), "Segundo")
        repositorio.guardarIncremental(id)

        val reabierto = reabrir(fichero, "dosveces-2")
        assertEquals("Primero", campo(reabierto, GeneradorFormularios.CAMPO_NOMBRE).valor)
        assertEquals("Segundo", campo(reabierto, GeneradorFormularios.CAMPO_DIRECCION).valor)
    }

    @Test
    fun sin_cambios_el_motor_lo_dice() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "sincambios.pdf"))
        val id = abrir(fichero, "sincambios")

        assertFalse("Recién abierto no hay nada que guardar", repositorio.tieneCambiosSinGuardar(id))
        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_NOMBRE), "algo")
        assertTrue(repositorio.tieneCambiosSinGuardar(id))
    }

    @Test
    fun el_valor_guardado_cambia_lo_que_se_dibuja() {
        val fichero = GeneradorFormularios.formulario(File(carpeta, "render.pdf"))
        val id = abrir(fichero, "render")

        val antes = repositorio.renderizar(id, 0, 0.5f).pixeles.toList()
        formularios.escribirTexto(id, idDe(id, GeneradorFormularios.CAMPO_NOMBRE), "MARC MAYOL")
        val despues = repositorio.renderizar(id, 0, 0.5f).pixeles.toList()

        // Si esto sale igual, el valor está en el PDF pero la apariencia del campo no
        // se ha regenerado: el usuario escribiría y no vería nada.
        assertNotEquals("La página tenía que cambiar al rellenar el campo", antes, despues)
    }

    private fun assertArrayEqualsMensaje(
        esperado: ByteArray,
        real: ByteArray,
    ) {
        val donde = esperado.indices.firstOrNull { esperado[it] != real[it] }
        assertTrue(
            "La revisión anterior se ha modificado en el byte $donde",
            donde == null,
        )
    }
}
