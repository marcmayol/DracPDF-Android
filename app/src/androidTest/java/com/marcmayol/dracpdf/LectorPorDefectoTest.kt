package com.marcmayol.dracpdf

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.saf.OrigenesDelSistema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Que la aplicación pueda ser el lector de PDF del teléfono.
 *
 * Esto es la mitad automatizable del criterio F11: **el sistema tiene que reconocer
 * los filtros**. Se le pregunta al propio `PackageManager` con los intents que mandan
 * de verdad el gestor de archivos, el correo y el navegador, en vez de leer el
 * manifest como si fuera un fichero de texto: lo que importa no es lo que pone, sino
 * lo que Android entiende.
 *
 * Lo que no se puede automatizar —que el selector ofrezca «Siempre», y la matriz de
 * WhatsApp, Gmail y navegador— está en PRUEBAS-MANUALES.md.
 */
@RunWith(AndroidJUnit4::class)
class LectorPorDefectoTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun el_sistema_nos_ofrece_para_abrir_un_pdf_por_content() {
        val abrir =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.android.providers.downloads/documento/42"), TIPO_PDF)
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        assertTrue("DracPDF no aparece como lector de PDF", nosOfrece(abrir))
    }

    @Test
    fun tambien_para_los_gestores_antiguos_que_mandan_file() {
        val abrir =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("file:///storage/emulated/0/Download/contrato.pdf"), TIPO_PDF)
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        assertTrue("Un file:// de un gestor antiguo no encuentra la aplicación", nosOfrece(abrir))
    }

    @Test
    fun y_para_un_pdf_que_llega_sin_tipo_declarado() {
        // Pasa con descargas y adjuntos: viajan como «octet-stream» y lo único que
        // dice que es un PDF es el nombre.
        val abrir =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(
                    Uri.parse("content://com.android.providers.downloads/public_downloads/factura.pdf"),
                    "application/octet-stream",
                )
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        assertTrue("Un PDF sin tipo declarado no encuentra la aplicación", nosOfrece(abrir))
    }

    @Test
    fun se_puede_compartir_un_pdf_a_la_aplicacion() {
        val enviar =
            Intent(Intent.ACTION_SEND).apply {
                type = TIPO_PDF
                putExtra(Intent.EXTRA_STREAM, Uri.parse("content://correo/adjunto/1"))
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        assertTrue("DracPDF no sale en el menú de compartir de un PDF", nosOfrece(enviar))
    }

    @Test
    fun un_documento_que_no_es_pdf_no_es_asunto_nuestro() {
        val abrir =
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://proveedor/hoja.xlsx"), "application/vnd.ms-excel")
                addCategory(Intent.CATEGORY_DEFAULT)
            }

        assertFalse("La aplicación se ofrece para abrir cosas que no sabe abrir", nosOfrece(abrir))
    }

    @Test
    fun lo_que_llega_compartido_no_trae_permiso_para_siempre() {
        // El de WhatsApp o Gmail: un permiso que muere con el proceso. Pedir que se
        // persista lanzaría `SecurityException`, así que la aplicación pregunta antes.
        val compartido =
            Intent(Intent.ACTION_SEND).apply {
                type = TIPO_PDF
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        assertFalse(OrigenesDelSistema.permisoPersistibleDe(compartido))
        assertFalse("Sin intent no hay permiso que valga", OrigenesDelSistema.permisoPersistibleDe(null))
    }

    @Test
    fun lo_que_da_el_selector_de_documentos_si_lo_trae() {
        val elegido =
            Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }

        assertTrue(OrigenesDelSistema.permisoPersistibleDe(elegido))
    }

    @Test
    fun el_documento_de_un_intent_se_reconoce_venga_como_venga() {
        val uri = Uri.parse("content://proveedor/documento/7")
        val visto = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, TIPO_PDF) }
        val enviado = Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uri) }

        assertEquals(
            uri.toString(),
            OrigenesDelSistema.delIntent(contexto.contentResolver, visto)?.identificador,
        )
        assertEquals(
            uri.toString(),
            OrigenesDelSistema.delIntent(contexto.contentResolver, enviado)?.identificador,
        )
        // Y un intent que no trae documento no se inventa ninguno.
        assertNull(OrigenesDelSistema.delIntent(contexto.contentResolver, Intent(Intent.ACTION_MAIN)))
    }

    /** Si el sistema pondría a DracPDF entre las opciones para este intent. */
    private fun nosOfrece(intent: Intent): Boolean =
        contexto.packageManager
            .queryIntentActivities(intent, 0)
            .any { it.activityInfo.packageName == contexto.packageName }

    private companion object {
        const val TIPO_PDF = "application/pdf"
    }
}
