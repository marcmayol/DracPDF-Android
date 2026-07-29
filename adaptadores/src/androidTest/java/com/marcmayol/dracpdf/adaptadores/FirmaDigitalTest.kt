package com.marcmayol.dracpdf.adaptadores

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.firma.PdfBoxSignatureService
import com.marcmayol.dracpdf.adaptadores.fixtures.GeneradorFixtures
import com.marcmayol.dracpdf.dominio.modelo.Credencial
import com.marcmayol.dracpdf.dominio.modelo.EstadoDeFirma
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException

/**
 * Firmar de verdad y verificar de verdad.
 *
 * El criterio de la fase pide que Adobe dé la firma por buena, y eso sólo lo puede
 * comprobar una persona. Lo que **sí** se puede demostrar aquí es lo que sostiene esa
 * comprobación: que la firma se construye sobre los bytes correctos, que distingue un
 * documento íntegro de uno manipulado, y que un documento firmado por otro se
 * reconoce como firmado. Si algo de eso falla, no hace falta abrir Adobe para saber
 * que está mal.
 *
 * El certificado no está en el repositorio —es material criptográfico— así que si no
 * se ha generado, estos tests se saltan diciendo cómo.
 */
@RunWith(AndroidJUnit4::class)
class FirmaDigitalTest {
    private lateinit var carpeta: File
    private lateinit var servicio: PdfBoxSignatureService

    @Before
    fun preparar() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        carpeta = File(contexto.cacheDir, "firma-digital").apply { mkdirs() }
        servicio =
            PdfBoxSignatureService(
                contexto,
                FicherosDeOrigen(contexto.contentResolver, File(carpeta, "copias")),
            )
    }

    /** El PKCS#12 de pruebas, sacado de los assets del APK de test. */
    private fun certificado(): Credencial.Pkcs12 {
        val destino = File(carpeta, CERTIFICADO)
        if (!destino.exists()) {
            val assets = InstrumentationRegistry.getInstrumentation().context.assets
            val existe =
                try {
                    assets.open(CERTIFICADO).use { entrada -> destino.outputStream().use(entrada::copyTo) }
                    true
                } catch (e: FileNotFoundException) {
                    false
                }
            assumeTrue(
                "Falta $CERTIFICADO. Ejecuta: python scripts/certificado_de_prueba.py",
                existe,
            )
        }
        return Credencial.Pkcs12(
            origen = OrigenDocumento.Privado(destino.absolutePath, CERTIFICADO),
            contrasena = CONTRASENA,
        )
    }

    private fun documento(nombre: String): OrigenDocumento {
        val fichero = GeneradorFixtures.documento(File(carpeta, nombre), paginas = 2)
        return OrigenDocumento.Privado(fichero.absolutePath, nombre)
    }

    private fun temporal(nombre: String) = OrigenDocumento.Privado(File(carpeta, nombre).absolutePath, nombre)

    @Test
    fun un_documento_sin_firmar_no_tiene_ninguna_firma() {
        assertTrue(servicio.verificar(documento("sinfirmar.pdf")).isEmpty())
    }

    @Test
    fun firmar_deja_una_firma_que_se_puede_encontrar() {
        val credencial = certificado()
        val origen = documento("firmado.pdf")
        val destino = temporal("firmado-salida.pdf")

        servicio.firmar(origen, destino, credencial, sello = null)

        val firmas = servicio.verificar(destino)
        assertEquals(1, firmas.size)
        assertTrue(
            "El firmante tenía que salir del certificado y llegó «${firmas.first().firmante}»",
            firmas.first().firmante?.contains("DracPDF") == true,
        )
    }

    @Test
    fun la_firma_de_un_certificado_autofirmado_es_desconocida_y_no_invalida() {
        val credencial = certificado()
        val destino = temporal("autofirmado.pdf")
        servicio.firmar(documento("auto.pdf"), destino, credencial, sello = null)

        // Es la distinción que más se malinterpreta: las cuentas cuadran, pero no se
        // puede decir de quién es. Presentarlo como inválido asustaría sin motivo.
        assertEquals(EstadoDeFirma.DESCONOCIDA, servicio.verificar(destino).first().estado)
    }

    @Test
    fun manipular_el_documento_despues_de_firmarlo_invalida_la_firma() {
        val credencial = certificado()
        val destino = temporal("manipulado.pdf")
        servicio.firmar(documento("intacto.pdf"), destino, credencial, sello = null)
        assertEquals(EstadoDeFirma.DESCONOCIDA, servicio.verificar(destino).first().estado)

        // Se cambia un byte **dentro de lo que la firma cubre**, y eso hay que
        // acertarlo: el hueco reservado para la propia firma ocupa la mayor parte del
        // fichero y está excluido del rango firmado, así que tocar «por la mitad» no
        // altera nada de lo firmado y la firma sigue cuadrando. El documento original
        // está al principio, detrás de la cabecera, y ahí sí duele.
        val fichero = File(destino.identificador)
        val bytes = fichero.readBytes()
        val donde = OFFSET_DENTRO_DEL_DOCUMENTO
        bytes[donde] = (bytes[donde].toInt() xor 0xFF).toByte()
        fichero.writeBytes(bytes)

        val estado = servicio.verificar(destino).first().estado
        assertEquals(
            "Un documento manipulado tiene que dar la firma por inválida",
            EstadoDeFirma.INVALIDA,
            estado,
        )
    }

    @Test
    fun la_firma_cubre_hasta_el_final_del_fichero_recien_firmado() {
        val credencial = certificado()
        val destino = temporal("cobertura.pdf")
        servicio.firmar(documento("cobertura-origen.pdf"), destino, credencial, sello = null)

        // Recién firmado no hay nada escrito después de la firma.
        assertFalse(servicio.verificar(destino).first().huboCambiosDespues)
    }

    @Test
    fun escribir_detras_de_la_firma_se_detecta_como_cambio_posterior() {
        val credencial = certificado()
        val destino = temporal("posterior.pdf")
        servicio.firmar(documento("posterior-origen.pdf"), destino, credencial, sello = null)

        // Se añade basura al final: no rompe la firma —lo firmado sigue igual— pero lo
        // que se está viendo ya no es exactamente lo que se firmó, y hay que decirlo.
        File(destino.identificador).appendBytes("\n% algo escrito despues\n".toByteArray())

        val firma = servicio.verificar(destino).first()
        assertTrue("Tenía que detectarse que hay bytes posteriores", firma.huboCambiosDespues)
        // Y la firma en sí sigue estando íntegra: son dos cosas distintas.
        assertEquals(EstadoDeFirma.DESCONOCIDA, firma.estado)
    }

    @Test
    fun firmar_dos_veces_deja_las_dos_firmas() {
        val credencial = certificado()
        val primera = temporal("dos-1.pdf")
        val segunda = temporal("dos-2.pdf")

        servicio.firmar(documento("dos-origen.pdf"), primera, credencial, sello = null)
        servicio.firmar(primera, segunda, credencial, sello = null)

        // Un PDF puede llevar varias firmas, y la primera tiene que seguir ahí: es lo
        // que garantiza el guardado incremental.
        assertEquals(2, servicio.verificar(segunda).size)
    }

    @Test
    fun una_contrasena_equivocada_no_firma_nada() {
        certificado()
        val mala =
            Credencial.Pkcs12(
                origen = OrigenDocumento.Privado(File(carpeta, CERTIFICADO).absolutePath, CERTIFICADO),
                contrasena = "esta-no-es",
            )

        assertThrows(Exception::class.java) {
            servicio.firmar(documento("mala.pdf"), temporal("mala-salida.pdf"), mala, sello = null)
        }
    }

    private companion object {
        const val CERTIFICADO = "certificado-de-prueba.p12"

        /** La que pone el script; no es un secreto, es un certificado de juguete. */
        const val CONTRASENA = "dracpdf"

        /**
         * Un punto del documento original, pasada la cabecera `%PDF` y dentro del
         * primer tramo del rango firmado.
         */
        const val OFFSET_DENTRO_DEL_DOCUMENTO = 400
    }
}
