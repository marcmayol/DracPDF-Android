package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.firma.PdfBoxSignatureService
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfHerramientas
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.ComprimirDocumento
import com.marcmayol.dracpdf.dominio.casos.ConvertirDocumento
import com.marcmayol.dracpdf.dominio.casos.DividirDocumento
import com.marcmayol.dracpdf.dominio.casos.OrganizarPaginas
import com.marcmayol.dracpdf.dominio.casos.ProtegerDocumento
import com.marcmayol.dracpdf.dominio.casos.UnirDocumentos
import com.marcmayol.dracpdf.dominio.modelo.Credencial
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.FormatoImagen
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/**
 * Las herramientas frente a un documento **firmado de verdad**.
 *
 * El fixture se firma con el certificado de pruebas y se comprueba que lleva firma
 * antes de nada: un test que diera por firmado un PDF que no lo está pasaría en verde
 * sin demostrar absolutamente nada, que es la trampa clásica de esta comprobación.
 *
 * La regla que se verifica no es «todo prohibido», sino la del plan: lo que
 * **reescribiría** el PDF se rechaza con el error de dominio, y lo que sólo lee
 * —convertir— sigue permitido.
 */
@RunWith(AndroidJUnit4::class)
class HerramientasSobreFirmadoTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var taller: File
    private lateinit var herramientas: MuPdfHerramientas
    private lateinit var firmas: PdfBoxSignatureService

    @Before
    fun montar() {
        taller = File(contexto.cacheDir, "herramientas-firmado").also { it.deleteRecursively() }
        taller.mkdirs()
        val ficheros = FicherosDeOrigen(contexto.contentResolver, File(taller, "copias"))
        herramientas =
            MuPdfHerramientas(ficheros, SalidasDeHerramienta(contexto.contentResolver), File(taller, "temporales"))
        firmas = PdfBoxSignatureService(contexto, ficheros)
    }

    @Test
    fun unir_con_un_firmado_dentro_se_rechaza() {
        val firmado = documentoFirmado("para-unir.pdf")
        val normal = documento("acompanante.pdf")
        val destino = destino("union-imposible.pdf")

        val fallo =
            assertThrows(ErrorDocumento.FicheroFirmado::class.java) {
                UnirDocumentos(herramientas, firmas)(listOf(normal, firmado), destino)
            }

        // Con cinco ficheros elegidos, «uno está firmado» no sirve: hay que decir cuál.
        assertEquals(firmado.identificador, fallo.origen.identificador)
        assertTrue("Se escribió el destino pese al rechazo", !File(destino.identificador).exists())
    }

    @Test
    fun organizar_dividir_proteger_y_comprimir_se_niegan_sobre_un_firmado() {
        val firmado = documentoFirmado("intocable.pdf")

        assertThrows(ErrorDocumento.FicheroFirmado::class.java) {
            OrganizarPaginas(herramientas, firmas)(firmado, listOf(PaginaOrdenada(0)), destino("org.pdf"))
        }
        assertThrows(ErrorDocumento.FicheroFirmado::class.java) {
            DividirDocumento(herramientas, firmas)(firmado, listOf(1..1), listOf(destino("div.pdf")))
        }
        assertThrows(ErrorDocumento.FicheroFirmado::class.java) {
            ProtegerDocumento(herramientas, firmas)(firmado, destino("prot.pdf"), "clave")
        }
        assertThrows(ErrorDocumento.FicheroFirmado::class.java) {
            ComprimirDocumento(herramientas, firmas)(firmado, destino("comp.pdf"))
        }
    }

    @Test
    fun convertir_si_se_permite_sobre_un_firmado_porque_solo_lee() {
        val firmado = documentoFirmado("legible.pdf")
        val convertir = ConvertirDocumento(herramientas)

        // Sacar el texto de un contrato firmado para leerlo en otro sitio es legítimo:
        // no toca el original ni promete conservar nada suyo. Negarlo confundiría «no
        // romper la firma» con «no dejar mirar».
        val texto = destino("legible.txt")
        assertTrue(convertir.aTexto(firmado, texto))
        assertTrue("Pagina 1 de 2" in File(texto.identificador).readText())

        val carpeta = OrigenDocumento.Privado(File(taller, "imagenes").absolutePath, "imagenes")
        val imagenes =
            convertir.aImagenes(firmado, listOf(0), carpeta, AjustesImagen(FormatoImagen.PNG, escala = 1f))
        assertEquals(1, imagenes.size)
    }

    @Test
    fun el_original_firmado_sigue_intacto_despues_de_los_intentos() {
        val firmado = documentoFirmado("verificable.pdf")
        val antes = File(firmado.identificador).readBytes()

        runCatching { ComprimirDocumento(herramientas, firmas)(firmado, destino("no-va.pdf")) }

        assertTrue("El intento tocó el original", antes.contentEquals(File(firmado.identificador).readBytes()))
        // Y su firma sigue ahí, que es lo que se estaba protegiendo.
        assertEquals(1, firmas.verificar(firmado).size)
    }

    // ------------------------------------------------------------------- utilería

    private fun documento(nombre: String): OrigenDocumento {
        val fichero = GeneradorFixtures.documento(File(taller, nombre), paginas = 2)
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    private fun destino(nombre: String) = OrigenDocumento.Privado(File(taller, nombre).absolutePath, nombre)

    /**
     * Un fixture firmado de verdad, y comprobado: si el firmado fallara en silencio,
     * todo este test pasaría sin demostrar nada.
     */
    private fun documentoFirmado(nombre: String): OrigenDocumento {
        val sinFirmar = documento("sin-firmar-$nombre")
        val firmado = destino(nombre)
        firmas.firmar(sinFirmar, firmado, certificado(), sello = null)

        assertEquals("El fixture tenía que quedar firmado", 1, firmas.verificar(firmado).size)
        return firmado
    }

    /** El PKCS#12 de pruebas, sacado de los assets del APK de test. */
    private fun certificado(): Credencial.Pkcs12 {
        val destino = File(taller, CERTIFICADO)
        if (!destino.exists()) {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            val existe =
                try {
                    assets.open(CERTIFICADO).use { entrada -> destino.outputStream().use(entrada::copyTo) }
                    true
                } catch (e: FileNotFoundException) {
                    false
                }
            assumeTrue("Falta $CERTIFICADO. Ejecuta: python scripts/certificado_de_prueba.py", existe)
        }
        return Credencial.Pkcs12(
            origen = OrigenDocumento.Privado(destino.absolutePath, CERTIFICADO),
            contrasena = CONTRASENA,
        )
    }

    private companion object {
        const val CERTIFICADO = "certificado-de-prueba.p12"
        const val CONTRASENA = "dracpdf"
    }
}
