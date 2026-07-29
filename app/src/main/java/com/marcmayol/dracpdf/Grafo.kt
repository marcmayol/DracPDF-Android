package com.marcmayol.dracpdf

import android.content.Context
import com.marcmayol.dracpdf.adaptadores.firmas.AlmacenFirmasFichero
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfStampService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.CerrarDocumento
import com.marcmayol.dracpdf.dominio.casos.EstamparFirma
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos
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

    /** Las firmas viven en el almacenamiento privado: son del usuario, no del sistema. */
    val almacenFirmas = AlmacenFirmasFichero(File(contexto.filesDir, CARPETA_FIRMAS))

    val abrirDocumento = AbrirDocumento(repositorio, registro)
    val renderizarPagina = RenderizarPagina(repositorio, registro)
    val cerrarDocumento = CerrarDocumento(repositorio, registro)
    val listarCampos = ListarCampos(formularios, registro)
    val rellenarCampo = RellenarCampo(formularios, registro)
    val guardarDocumento = GuardarDocumento(repositorio, registro)
    val estamparFirma = EstamparFirma(sellos, almacenFirmas, repositorio, registro)

    /** Lo que el visor necesita, ya montado. */
    val casosDelVisor =
        CasosDelVisor(renderizarPagina, listarCampos, rellenarCampo, guardarDocumento, estamparFirma)

    val cachePaginas = CachePaginas(CachePaginas.presupuestoPara(contexto))

    fun alTerminar() {
        repositorio.cerrarTodo()
    }

    private companion object {
        const val CARPETA_FIRMAS = "firmas"
    }
}
