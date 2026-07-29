package com.marcmayol.dracpdf.adaptadores.firma

import com.tom_roush.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.util.Selector
import org.bouncycastle.util.Store
import java.io.InputStream
import java.security.cert.X509Certificate

/**
 * Construye la firma CMS que va dentro del PDF.
 *
 * PDFBox se encarga del PDF —reservar el hueco, calcular el rango de bytes— y llama
 * aquí con el contenido que hay que firmar. Lo que se devuelve es una firma CMS
 * **separada**: no lleva el documento dentro, sólo el resumen firmado y el
 * certificado, que es lo que pide PAdES.
 */
internal class FirmanteCms(
    private val identidad: Identidad,
) : SignatureInterface {
    override fun sign(contenido: InputStream): ByteArray {
        // Se lee entero en memoria: un PDF de móvil cabe, y CMS necesita el contenido
        // completo para calcular el resumen. Si algún día hay que firmar documentos
        // enormes, aquí hay que pasar a un CMSTypedData que vaya leyendo del flujo.
        val bytes = contenido.readBytes()

        val generador = CMSSignedDataGenerator()
        val firmante = JcaContentSignerBuilder(ALGORITMO).build(identidad.clave)
        generador.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                .build(firmante, identidad.certificado),
        )
        // La cadena entera, no sólo el certificado del firmante: sin ella, quien
        // valida no puede reconstruir el camino hasta la raíz de confianza.
        generador.addCertificates(JcaCertStore(identidad.cadena))

        return generador.generate(CMSProcessableByteArray(bytes), false).encoded
    }

    private companion object {
        const val ALGORITMO = "SHA256WithRSA"
    }
}

/**
 * Una firma CMS que ya está en un documento, para poder comprobarla.
 *
 * La comprobación es una sola operación y no dos, aunque se cuenten como dos: al
 * verificar la firma con el certificado, Bouncy Castle comprueba a la vez que el
 * resumen del contenido cuadra y que la firma de ese resumen es del dueño de la clave.
 * Separarlas aquí sería fingir un detalle que no existe.
 */
internal class FirmaCms(
    private val cms: ByteArray,
) {
    /**
     * El certificado del firmante, si viene dentro.
     *
     * Puede no venir: una firma puede referirse a un certificado por su emisor y
     * número de serie sin incluirlo. Entonces no se puede decir de quién es.
     */
    fun certificadoDe(contenidoFirmado: ByteArray): X509Certificate? =
        runCatching {
            val datos = CMSSignedData(CMSProcessableByteArray(contenidoFirmado), cms)
            val firmante = datos.signerInfos.signers.firstOrNull() ?: return null
            // El almacén de certificados es genérico y hay que decirle qué tipo se
            // espera: el SignerId identifica al firmante por emisor y número de serie.
            val almacen: Store<X509CertificateHolder> = datos.certificates
            val titular = almacen.getMatches(firmante.sid as Selector<X509CertificateHolder>).firstOrNull()
            if (titular == null) null else JcaX509CertificateConverter().getCertificate(titular)
        }.getOrNull()

    /**
     * Si la firma cubre exactamente estos bytes y la hizo el dueño del certificado.
     *
     * Un `false` aquí significa que el documento se ha tocado por debajo de la firma o
     * que la firma está corrupta. No admite matices: es el único caso en el que hay que
     * decirle a alguien que no se fíe.
     */
    fun esIntegra(
        contenidoFirmado: ByteArray,
        certificado: X509Certificate,
    ): Boolean =
        runCatching {
            val datos = CMSSignedData(CMSProcessableByteArray(contenidoFirmado), cms)
            val firmante = datos.signerInfos.signers.first()
            firmante.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificado))
        }.getOrDefault(false)
}
