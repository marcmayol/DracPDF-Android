package com.marcmayol.dracpdf

import android.content.Context
import com.marcmayol.dracpdf.adaptadores.ajustes.AjustesDeInterfaz
import com.marcmayol.dracpdf.adaptadores.firma.EspacioTemporalAndroid
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.firma.PdfBoxSignatureService
import com.marcmayol.dracpdf.adaptadores.firmas.AlmacenFirmasFichero
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfHerramientas
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfStampService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.CerrarDocumento
import com.marcmayol.dracpdf.dominio.casos.ComprimirDocumento
import com.marcmayol.dracpdf.dominio.casos.ConvertirDocumento
import com.marcmayol.dracpdf.dominio.casos.DesprotegerDocumento
import com.marcmayol.dracpdf.dominio.casos.DividirDocumento
import com.marcmayol.dracpdf.dominio.casos.EstamparFirma
import com.marcmayol.dracpdf.dominio.casos.FirmarDocumento
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.OrganizarPaginas
import com.marcmayol.dracpdf.dominio.casos.ProtegerDocumento
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.casos.RevisarAntesDeOperar
import com.marcmayol.dracpdf.dominio.casos.UnirDocumentos
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import com.marcmayol.dracpdf.ui.visor.CachePaginas
import com.marcmayol.dracpdf.ui.visor.CasosDelVisor
import java.io.File

/**
 * Las dependencias de la aplicación, montadas a mano.
 *
 * No hay marco de inyección y no hace falta: son seis objetos y una sola forma de
 * construirlos. Un grafo explícito que se lee de arriba abajo cuesta menos de
 * entender que la anotación que lo habría escondido.
 *
 * Vive en el [android.app.Application] porque el registro de documentos tiene que
 * sobrevivir a que se recree la actividad al girar la pantalla.
 */
class Grafo(
    contexto: Context,
) {
    val registro = RegistroDocumentos()

    /**
     * Los documentos que MuPDF tiene abiertos. Es una sola instancia a propósito: el
     * repositorio y el servicio de formularios tienen que entrar al mismo documento
     * por el mismo hilo.
     */
    private val fuente = FuenteDocumentosAndroid(contexto.contentResolver)
    private val sesiones = SesionesMuPdf(fuente)

    val repositorio = MuPdfDocumentRepository(sesiones, fuente)
    val formularios = MuPdfFormService(sesiones)
    val sellos = MuPdfStampService(sesiones)

    /**
     * Quien firma. Es otro motor —PDFBox— y trabaja sobre ficheros, no sobre el
     * documento abierto: una firma PAdES cubre los bytes del disco.
     */
    val firmaDigital =
        PdfBoxSignatureService(
            contexto,
            FicherosDeOrigen(contexto.contentResolver, File(contexto.cacheDir, CARPETA_COPIAS)),
        )
    val temporales = EspacioTemporalAndroid(File(contexto.cacheDir, CARPETA_TEMPORALES), fuente)

    /** Las firmas viven en el almacenamiento privado: son del usuario, no del sistema. */
    val almacenFirmas = AlmacenFirmasFichero(File(contexto.filesDir, CARPETA_FIRMAS))

    /**
     * Lo que el usuario eligió sobre la interfaz. Se lee aquí, en el arranque de la
     * aplicación, porque el tema tiene que estar decidido antes del primer fotograma.
     */
    val ajustesDeInterfaz = AjustesDeInterfaz(contexto)
    val temaInicial: PreferenciaTema = PreferenciaTema.de(ajustesDeInterfaz.temaGuardado())

    /**
     * La caja de herramientas. Trabaja sobre ficheros y no sobre las sesiones abiertas:
     * unir toma cinco que nadie está mirando y dividir produce otros tantos.
     */
    val herramientas =
        MuPdfHerramientas(
            ficheros = FicherosDeOrigen(contexto.contentResolver, File(contexto.cacheDir, CARPETA_COPIAS)),
            salidas = SalidasDeHerramienta(contexto.contentResolver),
            carpetaTemporal = File(contexto.cacheDir, CARPETA_TEMPORALES),
        )

    val revisarAntesDeOperar = RevisarAntesDeOperar(firmaDigital, registro, repositorio)
    val unirDocumentos = UnirDocumentos(herramientas, firmaDigital)
    val organizarPaginas = OrganizarPaginas(herramientas, firmaDigital)
    val dividirDocumento = DividirDocumento(herramientas, firmaDigital)
    val protegerDocumento = ProtegerDocumento(herramientas, firmaDigital)
    val desprotegerDocumento = DesprotegerDocumento(herramientas)
    val comprimirDocumento = ComprimirDocumento(herramientas, firmaDigital)
    val convertirDocumento = ConvertirDocumento(herramientas)

    val abrirDocumento = AbrirDocumento(repositorio, registro)
    val renderizarPagina = RenderizarPagina(repositorio, registro)
    val cerrarDocumento = CerrarDocumento(repositorio, registro)
    val listarCampos = ListarCampos(formularios, registro)
    val rellenarCampo = RellenarCampo(formularios, registro)
    val guardarDocumento = GuardarDocumento(repositorio, registro)
    val estamparFirma = EstamparFirma(sellos, almacenFirmas, repositorio, registro)
    val firmarDocumento = FirmarDocumento(firmaDigital, repositorio, temporales, registro, guardarDocumento)

    /** Lo que el visor necesita, ya montado. */
    val casosDelVisor =
        CasosDelVisor(renderizarPagina, listarCampos, rellenarCampo, guardarDocumento, estamparFirma)

    val cachePaginas = CachePaginas(CachePaginas.presupuestoPara(contexto))

    fun alTerminar() {
        repositorio.cerrarTodo()
    }

    private companion object {
        const val CARPETA_FIRMAS = "firmas"
        const val CARPETA_COPIAS = "para-firmar"
        const val CARPETA_TEMPORALES = "temporales"
    }
}
