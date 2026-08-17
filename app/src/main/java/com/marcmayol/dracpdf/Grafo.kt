package com.marcmayol.dracpdf

import android.content.Context
import com.marcmayol.dracpdf.adaptadores.ajustes.AjustesDeInterfaz
import com.marcmayol.dracpdf.adaptadores.conversion.ComponedorPdfAndroid
import com.marcmayol.dracpdf.adaptadores.conversion.ConversorDeSalidas
import com.marcmayol.dracpdf.adaptadores.conversion.ConversorWordDocx
import com.marcmayol.dracpdf.adaptadores.conversion.EntradasAPdf
import com.marcmayol.dracpdf.adaptadores.firma.EspacioTemporalAndroid
import com.marcmayol.dracpdf.adaptadores.firma.FicherosDeOrigen
import com.marcmayol.dracpdf.adaptadores.firma.PdfBoxSignatureService
import com.marcmayol.dracpdf.adaptadores.firmas.AlmacenFirmasFichero
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfAnotaciones
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfContenido
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfDocumentRepository
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfEdicion
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfEstructura
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfFormService
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfHerramientas
import com.marcmayol.dracpdf.adaptadores.mupdf.MuPdfStampService
import com.marcmayol.dracpdf.adaptadores.mupdf.SesionesMuPdf
import com.marcmayol.dracpdf.adaptadores.recientes.RecientesEnFichero
import com.marcmayol.dracpdf.adaptadores.saf.FuenteDocumentosAndroid
import com.marcmayol.dracpdf.adaptadores.saf.SalidasDeHerramienta
import com.marcmayol.dracpdf.dominio.casos.AbrirDocumento
import com.marcmayol.dracpdf.dominio.casos.BuscarEnDocumento
import com.marcmayol.dracpdf.dominio.casos.CerrarDocumento
import com.marcmayol.dracpdf.dominio.casos.ComprimirDocumento
import com.marcmayol.dracpdf.dominio.casos.ConvertirAPdf
import com.marcmayol.dracpdf.dominio.casos.ConvertirDocumento
import com.marcmayol.dracpdf.dominio.casos.ConvertirEstructura
import com.marcmayol.dracpdf.dominio.casos.DesprotegerDocumento
import com.marcmayol.dracpdf.dominio.casos.DividirDocumento
import com.marcmayol.dracpdf.dominio.casos.EditarContenido
import com.marcmayol.dracpdf.dominio.casos.EstamparFirma
import com.marcmayol.dracpdf.dominio.casos.FirmarDocumento
import com.marcmayol.dracpdf.dominio.casos.GuardarDocumento
import com.marcmayol.dracpdf.dominio.casos.ListarCampos
import com.marcmayol.dracpdf.dominio.casos.MarcarDocumento
import com.marcmayol.dracpdf.dominio.casos.OrganizarPaginas
import com.marcmayol.dracpdf.dominio.casos.ProtegerDocumento
import com.marcmayol.dracpdf.dominio.casos.RecordarDocumentos
import com.marcmayol.dracpdf.dominio.casos.RellenarCampo
import com.marcmayol.dracpdf.dominio.casos.RenderizarPagina
import com.marcmayol.dracpdf.dominio.casos.RevisarAntesDeOperar
import com.marcmayol.dracpdf.dominio.casos.UnirDocumentos
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
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
    private val contexto: Context,
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

    /** El texto y la estructura del documento: buscar, seleccionar, índice y ficha. */
    val contenido = MuPdfContenido(sesiones, fuente)
    val buscarEnDocumento = BuscarEnDocumento(contenido)

    /** Un documento como fichero de verdad, copiándolo de la nube si hace falta. */
    val ficheros = FicherosDeOrigen(contexto.contentResolver, File(contexto.cacheDir, CARPETA_COPIAS))

    /**
     * Quien firma. Es otro motor —PDFBox— y trabaja sobre ficheros, no sobre el
     * documento abierto: una firma PAdES cubre los bytes del disco.
     */
    val firmaDigital = PdfBoxSignatureService(contexto, ficheros)
    val temporales = EspacioTemporalAndroid(File(contexto.cacheDir, CARPETA_TEMPORALES), fuente)

    /** Las firmas viven en el almacenamiento privado: son del usuario, no del sistema. */
    val almacenFirmas = AlmacenFirmasFichero(File(contexto.filesDir, CARPETA_FIRMAS))

    /**
     * Lo que el usuario eligió sobre la interfaz. Se lee aquí, en el arranque de la
     * aplicación, porque el tema tiene que estar decidido antes del primer fotograma.
     */
    val ajustesDeInterfaz = AjustesDeInterfaz(contexto)
    val temaInicial: PreferenciaTema = PreferenciaTema.de(ajustesDeInterfaz.temaGuardado())

    /** Por dónde salen los ficheros que produce la aplicación: SAF o carpeta propia. */
    val salidasDeHerramienta = SalidasDeHerramienta(contexto.contentResolver)

    /**
     * La caja de herramientas. Trabaja sobre ficheros y no sobre las sesiones abiertas:
     * unir toma cinco que nadie está mirando y dividir produce otros tantos.
     */
    val herramientas =
        MuPdfHerramientas(
            ficheros = ficheros,
            salidas = salidasDeHerramienta,
            carpetaTemporal = File(contexto.cacheDir, CARPETA_TEMPORALES),
        )

    /**
     * Las conversiones, cada una con su motor.
     *
     * La estructura la lee MuPDF —es quien tiene el documento abierto— y de escribirla
     * se encargan piezas sin motor: un ODT, un RTF o un DOCX son ficheros de texto
     * dentro de un ZIP, y no hacen falta bibliotecas para eso.
     */
    val lectorDeEstructura = MuPdfEstructura(sesiones)
    val conversorDeSalidas = ConversorDeSalidas(salidasDeHerramienta)
    val convertirEstructura = ConvertirEstructura(lectorDeEstructura, conversorDeSalidas)

    private val carpetaTemporal = File(contexto.cacheDir, CARPETA_TEMPORALES)
    val conversorWord = ConversorWordDocx(contexto.contentResolver, salidasDeHerramienta, carpetaTemporal)
    val componedorDePdf = ComponedorPdfAndroid(salidasDeHerramienta, carpetaTemporal)
    val conversorEntradas = EntradasAPdf(contexto.contentResolver, salidasDeHerramienta, carpetaTemporal)
    val convertirAPdf = ConvertirAPdf(conversorEntradas)

    /** Marcar y editar el contenido: lo de la Fase 8. */
    val anotaciones = MuPdfAnotaciones(sesiones)
    val marcarDocumento = MarcarDocumento(anotaciones, registro)
    val edicionDeContenido = MuPdfEdicion(sesiones)
    val editarContenido = EditarContenido(edicionDeContenido, registro)

    val revisarAntesDeOperar = RevisarAntesDeOperar(firmaDigital, registro, repositorio)
    val unirDocumentos = UnirDocumentos(herramientas, firmaDigital)
    val organizarPaginas = OrganizarPaginas(herramientas, firmaDigital)
    val dividirDocumento = DividirDocumento(herramientas, firmaDigital)
    val protegerDocumento = ProtegerDocumento(herramientas, firmaDigital)
    val desprotegerDocumento = DesprotegerDocumento(herramientas)
    val comprimirDocumento = ComprimirDocumento(herramientas, firmaDigital)
    val convertirDocumento = ConvertirDocumento(herramientas)

    /** Los recientes viven en el almacenamiento privado, como las firmas. */
    val recientes = RecientesEnFichero(File(contexto.filesDir, FICHERO_RECIENTES))
    val recordarDocumentos = RecordarDocumentos(recientes, registro, repositorio)

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
        CasosDelVisor(
            renderizar = renderizarPagina,
            listarCampos = listarCampos,
            rellenar = rellenarCampo,
            guardar = guardarDocumento,
            estamparFirma = estamparFirma,
            buscar = buscarEnDocumento,
            texto = contenido,
            estructura = contenido,
        )

    val cachePaginas = CachePaginas(CachePaginas.presupuestoPara(contexto))

    /**
     * El fichero que se le entrega al servicio de impresión del sistema.
     *
     * Con [paginas] a `null` se imprime el documento tal cual, y entonces basta con el
     * fichero de origen —copiado a la caché si venía de un `content://`, porque el
     * sistema de impresión necesita un fichero de verdad—. Con un rango se escribe un
     * PDF nuevo con esas páginas.
     *
     * Se llama al puerto y no al caso de uso **a propósito**: `OrganizarPaginas` se
     * niega a tocar un documento firmado, y con razón, porque escribe una copia
     * editable. Imprimir no reescribe nada del original ni promete conservar su firma:
     * negarse aquí sería confundir «no romper la firma» con «no dejar imprimir».
     */
    fun paraImprimir(
        id: IdDocumento,
        paginas: List<Int>?,
    ): File {
        val origen = repositorio.origenDe(id)
        if (paginas == null) return ficheros.comoFichero(origen)

        val recorte = File(File(contexto.cacheDir, CARPETA_TEMPORALES).also(File::mkdirs), "impresion.pdf")
        herramientas.reorganizar(
            origen = origen,
            paginas = paginas.map { PaginaOrdenada(original = it) },
            destino = OrigenDocumento.Privado(recorte.absolutePath, recorte.name),
        )
        return recorte
    }

    fun alTerminar() {
        repositorio.cerrarTodo()
    }

    private companion object {
        const val CARPETA_FIRMAS = "firmas"
        const val FICHERO_RECIENTES = "recientes.json"
        const val CARPETA_COPIAS = "para-firmar"
        const val CARPETA_TEMPORALES = "temporales"
    }
}
