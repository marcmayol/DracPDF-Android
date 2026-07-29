package com.marcmayol.dracpdf.adaptadores.firma

import android.content.Context
import com.marcmayol.dracpdf.dominio.modelo.Credencial
import com.marcmayol.dracpdf.dominio.modelo.EstadoDeFirma
import com.marcmayol.dracpdf.dominio.modelo.FirmaDelDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.SelloVisible
import com.marcmayol.dracpdf.dominio.puertos.SignatureService
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.PDSignature
import java.io.IOException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Calendar

/**
 * La firma PAdES con PDFBox.
 *
 * **Dos motores, un fichero.** MuPDF rellena y dibuja pero no sabe construir una
 * firma; PDFBox sabe firmar pero no es el motor con el que se ve el documento. Se
 * reparten el trabajo por fichero y no por documento abierto, igual que en el
 * escritorio: MuPDF deja los cambios escritos, PDFBox lee ese fichero y produce otro
 * con la revisión firmada encima.
 *
 * La firma se añade **como revisión incremental**: los bytes anteriores no se tocan.
 * Es lo que permite que un PDF lleve varias firmas y que las anteriores sigan siendo
 * válidas, y es también por lo que el guardado de la Fase 2 tuvo que ser incremental.
 */
class PdfBoxSignatureService(
    contexto: Context,
    private val ficheros: FicherosDeOrigen,
) : SignatureService {
    init {
        // PDFBox-Android necesita esto antes de nada: carga sus recursos de fuentes
        // desde los assets. Sin ello, la primera firma falla con un error que no dice
        // en absoluto lo que pasa.
        PDFBoxResourceLoader.init(contexto)
    }

    override fun firmar(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        credencial: Credencial,
        sello: SelloVisible?,
    ) {
        val pkcs12 = credencial as? Credencial.Pkcs12 ?: error("Sólo se firma con PKCS#12 por ahora")
        val identidad = cargarIdentidad(pkcs12)

        val entrada = ficheros.comoFichero(origen)
        val salida = ficheros.comoFichero(destino)

        PDDocument.load(entrada).use { documento ->
            val firma =
                PDSignature().apply {
                    setFilter(PDSignature.FILTER_ADOBE_PPKLITE)
                    // ETSI.CAdES.detached es lo que hace que esto sea PAdES y no la
                    // firma antigua de Adobe: es el subfiltro que exige la norma
                    // europea, y el que hace que un validador la reconozca como tal.
                    setSubFilter(COSName.getPDFName(SUBFILTRO_PADES))
                    name = nombreDe(identidad.certificado)
                    signDate = Calendar.getInstance()
                }

            // El sello visible llega en la Fase 4 tarea 1 sobre lo que ya hace la
            // Fase 3: la firma dibujada se estampa antes de firmar, así queda **dentro**
            // de lo firmado. Un sello añadido después de la firma no está cubierto por
            // ella, que es justo lo contrario de lo que la gente espera al verlo.
            documento.addSignature(firma, FirmanteCms(identidad))

            salida.outputStream().use { flujo ->
                // Incremental: PDFBox escribe el original tal cual y añade la revisión
                // firmada al final.
                documento.saveIncremental(flujo)
            }
        }
    }

    override fun verificar(origen: OrigenDocumento): List<FirmaDelDocumento> {
        val fichero = ficheros.comoFichero(origen)
        if (!fichero.exists()) throw IOException("No se ha podido leer ${origen.identificador}")

        return PDDocument.load(fichero).use { documento ->
            val contenido = fichero.readBytes()
            documento.signatureDictionaries.mapIndexed { indice, firma ->
                fichaDe(firma, contenido, indice)
            }
        }
    }

    /**
     * Comprueba una firma y decide en qué estado está.
     *
     * Lo que se hace aquí es lo mismo que hace cualquier validador serio, en este
     * orden: sacar los bytes que la firma dice cubrir, comprobar que el resumen
     * criptográfico cuadra, y sólo entonces mirar de quién es el certificado. Si el
     * primer paso falla, de quién sea da igual.
     */
    private fun fichaDe(
        firma: PDSignature,
        contenido: ByteArray,
        indice: Int,
    ): FirmaDelDocumento {
        val estado =
            runCatching { estadoDe(firma, contenido) }
                // Un error al verificar no es una firma válida ni una inválida: es una
                // firma que no se ha podido comprobar, y eso es «desconocida».
                .getOrDefault(EstadoDeFirma.DESCONOCIDA)

        return FirmaDelDocumento(
            campo = firma.name ?: "Firma ${indice + 1}",
            firmante = firma.name,
            estado = estado,
            pagina = null,
            // Si el rango firmado no llega al final del fichero, hay bytes escritos
            // después de la firma: cambios posteriores.
            huboCambiosDespues = finDelRango(firma) < contenido.size,
        )
    }

    private fun estadoDe(
        firma: PDSignature,
        contenido: ByteArray,
    ): EstadoDeFirma {
        val firmado = firma.getSignedContent(contenido)
        val cms = FirmaCms(firma.contents)
        val certificado = cms.certificadoDe(firmado) ?: return EstadoDeFirma.DESCONOCIDA

        return when {
            // El único caso en el que hay que decirle a alguien que no se fíe.
            !cms.esIntegra(firmado, certificado) -> EstadoDeFirma.INVALIDA
            // Autofirmado, o de un emisor que no está entre los de confianza: íntegra,
            // pero sin poder afirmar de quién es.
            esAutofirmado(certificado) -> EstadoDeFirma.DESCONOCIDA
            !esVigente(certificado) -> EstadoDeFirma.DESCONOCIDA
            else -> EstadoDeFirma.VALIDA
        }
    }

    private fun finDelRango(firma: PDSignature): Int {
        val rango = firma.byteRange
        // El ByteRange son pares (desde, cuántos): el final de lo firmado es el final
        // del último par.
        if (rango.size < PARES_DEL_RANGO) return 0
        return rango[rango.size - 2] + rango[rango.size - 1]
    }

    private fun esAutofirmado(certificado: X509Certificate): Boolean =
        certificado.subjectX500Principal == certificado.issuerX500Principal

    /**
     * Si el certificado estaba en vigor.
     *
     * Las dos excepciones no se registran porque **son la respuesta**: la API de Java
     * contesta esta pregunta lanzando, y traducir eso a un booleano es justo lo que
     * hace esta función. Un certificado caducado no es un error del programa.
     */
    @Suppress("SwallowedException")
    private fun esVigente(certificado: X509Certificate): Boolean =
        try {
            certificado.checkValidity()
            true
        } catch (e: CertificateExpiredException) {
            false
        } catch (e: CertificateNotYetValidException) {
            false
        }

    private fun nombreDe(certificado: X509Certificate): String =
        certificado.subjectX500Principal.name
            .split(",")
            .firstOrNull { it.trim().startsWith("CN=") }
            ?.substringAfter("CN=")
            ?.trim()
            ?: certificado.subjectX500Principal.name

    private fun cargarIdentidad(credencial: Credencial.Pkcs12): Identidad {
        val almacen = KeyStore.getInstance("PKCS12")
        ficheros.comoFichero(credencial.origen).inputStream().use { flujo ->
            almacen.load(flujo, credencial.contrasena.toCharArray())
        }

        val alias =
            almacen.aliases().toList().firstOrNull { almacen.isKeyEntry(it) }
                ?: throw IOException("El certificado no tiene ninguna clave privada dentro")

        val clave = almacen.getKey(alias, credencial.contrasena.toCharArray()) as PrivateKey
        val cadena = almacen.getCertificateChain(alias).map { it as X509Certificate }
        return Identidad(clave, cadena)
    }

    private companion object {
        const val SUBFILTRO_PADES = "ETSI.CAdES.detached"

        /** Un ByteRange de una firma trae al menos dos pares (desde, cuántos). */
        const val PARES_DEL_RANGO = 4
    }
}

/** La clave privada y su cadena de certificados, ya sacadas del PKCS#12. */
internal class Identidad(
    val clave: PrivateKey,
    val cadena: List<X509Certificate>,
) {
    val certificado: X509Certificate get() = cadena.first()

    fun firmaDe(datos: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run {
            initSign(clave)
            update(datos)
            sign()
        }
}
