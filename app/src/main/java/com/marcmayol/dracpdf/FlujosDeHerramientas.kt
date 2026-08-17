package com.marcmayol.dracpdf

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.marcmayol.dracpdf.adaptadores.impresion.ImpresionPdf
import com.marcmayol.dracpdf.adaptadores.saf.OrigenesDelSistema
import com.marcmayol.dracpdf.dominio.casos.AvisosDeHerramienta
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.EntradaAConvertir
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.dominio.puertos.TipoDeEntrada
import com.marcmayol.dracpdf.ui.herramientas.DestinoDeConversion
import com.marcmayol.dracpdf.ui.herramientas.DestinoDeTabla
import com.marcmayol.dracpdf.ui.herramientas.DialogoAvisos
import com.marcmayol.dracpdf.ui.herramientas.DialogoContrasena
import com.marcmayol.dracpdf.ui.herramientas.DialogoConvertir
import com.marcmayol.dracpdf.ui.herramientas.DialogoDividir
import com.marcmayol.dracpdf.ui.herramientas.EstadoHerramienta
import com.marcmayol.dracpdf.ui.herramientas.Herramienta
import com.marcmayol.dracpdf.ui.herramientas.HerramientasViewModel
import com.marcmayol.dracpdf.ui.herramientas.HojaOrganizar
import com.marcmayol.dracpdf.ui.herramientas.HojaProgreso
import com.marcmayol.dracpdf.ui.tema.HojaTema
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema

// Todo lo que pasa entre elegir una herramienta y verla hecha.
//
// Vive fuera de la actividad porque es un flujo y no una pantalla: cada herramienta
// pregunta lo suyo —rangos, contraseña, formato—, algunas piden ficheros de entrada y
// casi todas acaban en un selector del sistema. Junto en la actividad, ese ir y venir
// enterraba lo único que allí importa, que es qué pantalla se está viendo.

/**
 * Qué nombre proponer al guardar el resultado. Se propone y no se impone: el selector del sistema
 * deja cambiarlo, y con dos documentos parecidos abiertos el nombre de partida es lo
 * único que distingue un resultado de otro.
 */
internal fun nombreSugerido(
    herramienta: Herramienta,
    nombreDelDocumento: String,
    pedido: LoPedido,
): String {
    val base = nombreDelDocumento.substringBeforeLast('.', nombreDelDocumento)
    return when (herramienta) {
        Herramienta.COMPRIMIR -> "$base-comprimido.pdf"
        Herramienta.CONVERTIR -> "$base.txt"
        Herramienta.PROTEGER -> if (pedido.quitando) "$base-sin-contrasena.pdf" else "$base-protegido.pdf"
        Herramienta.DIVIDIR -> "$base-parte1.pdf"
        Herramienta.UNIR -> "documentos-unidos.pdf"
        Herramienta.ORGANIZAR -> "$base-organizado.pdf"
        else -> "$base.pdf"
    }
}

/**
 * Las hojas que no dependen de qué pantalla hay debajo: el progreso de una herramienta
 * y el tema.
 *
 * Van juntas y aparte porque se dibujan **encima de lo que haya**, así que meterlas en
 * el `when` de la pantalla obligaría a repetirlas en cada rama.
 */
@Composable
internal fun HojasSueltas(
    trabajo: EstadoHerramienta,
    temaAbierto: Boolean,
    preferenciaTema: PreferenciaTema,
    alCancelarTrabajo: () -> Unit,
    alCerrarTrabajo: () -> Unit,
    alElegirTema: (PreferenciaTema) -> Unit,
    alCerrarTema: () -> Unit,
) {
    if (trabajo.herramienta != null) {
        HojaProgreso(estado = trabajo, alCancelar = alCancelarTrabajo, alCerrar = alCerrarTrabajo)
    }
    if (temaAbierto) {
        HojaTema(elegida = preferenciaTema, alElegir = alElegirTema, alCerrar = alCerrarTema)
    }
}

/**
 * Por dónde empieza cada herramienta.
 *
 * Son tres arranques distintos y conviene verlos juntos: unas sólo necesitan saber
 * dónde dejar el resultado, otra pide varios ficheros de entrada, y otras preguntan
 * algo —los rangos, la contraseña— antes incluso de saber qué se va a guardar.
 */
internal fun arrancar(
    herramienta: Herramienta,
    alPedirDestino: (Herramienta) -> Unit,
    alElegirVarios: () -> Unit,
    alPreguntar: (Herramienta) -> Unit,
) {
    when (herramienta) {
        Herramienta.COMPRIMIR -> alPedirDestino(herramienta)
        Herramienta.UNIR -> alElegirVarios()
        Herramienta.DIVIDIR, Herramienta.PROTEGER, Herramienta.CONVERTIR, Herramienta.ORGANIZAR ->
            alPreguntar(herramienta)

        else -> Unit
    }
}

/**
 * Qué hace cada herramienta una vez se sabe dónde va el resultado.
 *
 * Está fuera de la pantalla porque es un despacho y no interfaz: aquí no se dibuja
 * nada, sólo se decide qué caso de uso corre con lo que se ha ido recogiendo por el
 * camino —los rangos, la contraseña, los ficheros elegidos—.
 */
internal fun ejecutarConDestino(
    herramienta: Herramienta,
    modelo: HerramientasViewModel,
    origen: OrigenDocumento,
    destino: OrigenDocumento,
    pedido: LoPedido,
) {
    when (herramienta) {
        Herramienta.COMPRIMIR -> modelo.comprimir(origen, destino)
        Herramienta.CONVERTIR -> modelo.convertirATexto(origen, destino)
        // La misma herramienta en las dos direcciones: lo que cambia es lo que el
        // usuario eligió en el diálogo, no la entrada de la rejilla.
        Herramienta.PROTEGER ->
            pedido.clave?.let { clave ->
                if (pedido.quitando) {
                    modelo.desproteger(origen, destino, clave)
                } else {
                    modelo.proteger(origen, destino, clave)
                }
            }

        Herramienta.UNIR -> modelo.unir(pedido.paraUnir, destino)
        Herramienta.ORGANIZAR -> pedido.orden?.let { modelo.organizar(origen, it, destino) }
        // Dividir escribe varios ficheros y el sistema sólo da uno por ronda: el resto
        // se numeran a partir del elegido.
        Herramienta.DIVIDIR ->
            pedido.rangos?.let { modelo.dividir(origen, it, destinosNumerados(destino, it.size)) }

        else -> Unit
    }
}

/**
 * Abre el diálogo de impresión del sistema con el documento dentro.
 *
 * El fichero se prepara **cuando el sistema lo pide** y no ahora: hasta que el usuario
 * no elige el rango en ese diálogo no se sabe qué páginas hay que entregar.
 */
internal fun imprimir(
    contexto: android.content.Context,
    grafo: Grafo,
    id: IdDocumento,
    nombre: String,
) {
    ImpresionPdf.lanzar(
        contexto,
        ImpresionPdf(
            nombre = nombre,
            paginas = grafo.paginasDelDocumento(id),
            ficheroDe = { paginas -> grafo.paraImprimir(id, paginas) },
        ),
    )
}

/**
 * Cómo se llama la copia que se propone guardar.
 *
 * Un documento **prestado** —el que llega de un chat o del correo y vive con un
 * permiso que muere al cerrar— se guarda con su propio nombre, porque lo que se está
 * haciendo es quedárselo. Uno que ya es tuyo se guarda como copia, porque el original
 * sigue estando donde estaba.
 */
internal fun nombreDeLaCopia(
    nombre: String,
    prestado: Boolean,
): String {
    val base = nombre.substringBeforeLast('.', nombre)
    return if (prestado) "$base.pdf" else "$base-copia.pdf"
}

/**
 * Los dos selectores de crear un PDF desde otros ficheros, montados juntos.
 *
 * Son dos idas y vueltas encadenadas —primero qué se convierte, después dónde se
 * guarda— y viven aquí fuera para que la pantalla no tenga que llevar la cuenta de lo
 * elegido entre una y otra.
 */
@Composable
internal fun recordarCreacionDePdf(
    resolver: ContentResolver,
    modelo: HerramientasViewModel,
): ManagedActivityResultLauncher<Array<String>, List<Uri>> {
    var elegidos by remember { mutableStateOf<List<EntradaAConvertir>>(emptyList()) }

    val destino =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(TIPO_PDF)) { uri: Uri? ->
            val entradas = elegidos
            elegidos = emptyList()
            if (uri != null && entradas.isNotEmpty()) {
                modelo.crearPdfDesde(entradas, OrigenDocumento.Externo(uri.toString(), nombreDeUri(uri)))
            }
        }

    return rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        // Lo que el sistema no sepa clasificar se queda fuera aquí y no a mitad de la
        // conversión: es la diferencia entre «esto no lo sé convertir» y un PDF al que
        // le faltan páginas sin decir por qué.
        val entradas =
            uris.mapNotNull { uri ->
                val nombre = OrigenesDelSistema.nombreDe(resolver, uri)
                TipoDeEntrada.de(nombre, resolver.getType(uri))?.let { tipo ->
                    EntradaAConvertir(OrigenDocumento.Externo(uri.toString(), nombre), tipo)
                }
            }
        if (entradas.isNotEmpty()) {
            elegidos = entradas
            destino.launch("documento.pdf")
        }
    }
}

/**
 * El documento sobre el que se está convirtiendo, con las tres cosas que hacen falta:
 * quién es para el motor, de dónde salió y cómo llamar a lo que se escriba.
 */
internal data class DocumentoEnCurso(
    val id: IdDocumento,
    val origen: OrigenDocumento,
    val nombreBase: String,
)

internal fun convertirEnLaCarpeta(
    modelo: HerramientasViewModel,
    documento: DocumentoEnCurso,
    carpeta: OrigenDocumento,
    pedido: LoPedido,
) {
    val (id, origen, nombreBase) = documento
    when (pedido.destinoDeConversion) {
        // Las tablas y los documentos de texto trabajan sobre el documento abierto:
        // hace falta su estructura deducida, no sus bytes.
        DestinoDeConversion.TABLAS ->
            pedido.formatoDeTabla?.let { modelo.convertirTablas(id, it.formato, carpeta, nombreBase) }

        null, DestinoDeConversion.TEXTO, DestinoDeConversion.WORD, DestinoDeConversion.IMAGENES -> {
            val paginas = pedido.paginasDeImagen ?: return
            val ajustes = pedido.ajustesDeImagen ?: return
            modelo.convertirAImagenes(origen = origen, paginas = paginas, carpeta = carpeta, ajustes = ajustes)
        }

        else ->
            pedido.destinoDeConversion.formato?.let { modelo.convertirDocumentoA(id, it, carpeta, nombreBase) }
    }
}

/**
 * Lo que el usuario ha ido diciendo antes de llegar al destino.
 *
 * Va junto porque se consume junto: cada herramienta mira lo suyo y el resto viene
 * vacío, que es más honesto que ocho parámetros de los que siete sobran siempre.
 */
internal data class LoPedido(
    val rangos: List<IntRange>? = null,
    val clave: String? = null,
    /** Si la contraseña es para quitarla en vez de para ponerla. */
    val quitando: Boolean = false,
    val paraUnir: List<OrigenDocumento> = emptyList(),
    /** Cómo queda el documento tras organizarlo: qué páginas, en qué orden y giradas cómo. */
    val orden: List<PaginaOrdenada>? = null,
    val paginasDeImagen: List<Int>? = null,
    val ajustesDeImagen: AjustesImagen? = null,
    /** A qué formato se convierte, cuando no es ni texto ni imágenes. */
    val destinoDeConversion: DestinoDeConversion? = null,
    val formatoDeTabla: DestinoDeTabla? = null,
)

/**
 * Lo que una herramienta pregunta antes de poder empezar.
 *
 * Vive fuera de la pantalla principal porque son cuatro flujos con su propio orden
 * —preguntar, avisar, elegir destino— y mezclados ahí dentro convertían la función en
 * algo que ya no se leía de arriba abajo.
 */
@Composable
internal fun FlujosDeHerramienta(
    preguntando: Herramienta?,
    abierto: DatosDelAbierto,
    avisos: AvisosDeHerramienta?,
    acciones: AccionesDeFlujo,
) {
    when (preguntando) {
        Herramienta.DIVIDIR ->
            DialogoDividir(
                paginas = abierto.paginas,
                alAceptar = { rangos ->
                    acciones.alDejarDePreguntar()
                    acciones.alRecoger { it.copy(rangos = rangos) }
                    acciones.alPedirDestino(Herramienta.DIVIDIR)
                },
                alCancelar = acciones.alDejarDePreguntar,
            )

        Herramienta.PROTEGER ->
            DialogoContrasena(
                quitandoAlEmpezar = false,
                alAceptar = { clave, quitando ->
                    acciones.alDejarDePreguntar()
                    acciones.alRecoger { it.copy(clave = clave, quitando = quitando) }
                    acciones.alPedirDestino(Herramienta.PROTEGER)
                },
                alCancelar = acciones.alDejarDePreguntar,
            )

        Herramienta.CONVERTIR ->
            DialogoConvertir(
                paginas = abierto.paginas,
                alElegirTexto = {
                    acciones.alDejarDePreguntar()
                    acciones.alPedirDestino(Herramienta.CONVERTIR)
                },
                alElegirImagenes = { paginas, ajustes ->
                    acciones.alDejarDePreguntar()
                    acciones.alRecoger { it.copy(paginasDeImagen = paginas, ajustesDeImagen = ajustes) }
                    acciones.alPedirCarpeta()
                },
                // HTML, Markdown, ODT, RTF y las tablas escriben en una carpeta: unas
                // tablas son varios ficheros, y pedir un nombre para «el fichero» sería
                // mentir sobre lo que va a salir.
                alElegirDocumento = { destino ->
                    acciones.alDejarDePreguntar()
                    acciones.alRecoger { it.copy(destinoDeConversion = destino) }
                    if (destino.vaACarpeta) {
                        acciones.alPedirCarpeta()
                    } else {
                        acciones.alPedirDestino(
                            Herramienta.CONVERTIR,
                        )
                    }
                },
                alElegirTablas = { formato ->
                    acciones.alDejarDePreguntar()
                    acciones.alRecoger {
                        it.copy(destinoDeConversion = DestinoDeConversion.TABLAS, formatoDeTabla = formato)
                    }
                    acciones.alPedirCarpeta()
                },
                alCancelar = acciones.alDejarDePreguntar,
            )

        Herramienta.ORGANIZAR ->
            HojaOrganizar(
                paginas = abierto.paginas,
                miniaturas = abierto.miniaturas,
                alPedirMiniatura = abierto.alPedirMiniatura,
                alGuardar = { orden ->
                    acciones.alDejarDePreguntar()
                    acciones.alRecoger { it.copy(orden = orden) }
                    acciones.alPedirDestino(Herramienta.ORGANIZAR)
                },
                alCerrar = acciones.alDejarDePreguntar,
            )

        else -> Unit
    }

    // Lo visto en los ficheros elegidos para unir. Un firmado impide seguir; uno abierto
    // con cambios sólo advierte de qué versión se va a leer.
    if (avisos != null && avisos.hayAlguno) {
        DialogoAvisos(
            firmados = avisos.firmados.map(::nombreDe),
            abiertosConCambios = avisos.abiertosConCambios.map(::nombreDe),
            alSeguir = {
                acciones.alSeguirTrasAvisos()
                acciones.alPedirDestino(Herramienta.UNIR)
            },
            alCancelar = acciones.alOlvidarAvisos,
        )
    } else if (avisos != null) {
        // Nada que advertir: derecho a elegir dónde guardar.
        LaunchedEffect(avisos) {
            acciones.alSeguirTrasAvisos()
            acciones.alPedirDestino(Herramienta.UNIR)
        }
    }
}

/**
 * Lo que los flujos necesitan saber del documento que hay abierto.
 *
 * Las miniaturas viajan hasta aquí porque organizar es mirar páginas: sin verlas, un
 * reordenamiento sería mover números.
 */
internal data class DatosDelAbierto(
    val paginas: Int,
    val miniaturas: Map<Int, ImageBitmap>,
    val alPedirMiniatura: (Int) -> Unit,
)

/**
 * Lo que un flujo puede pedir que pase después.
 *
 * Van agrupadas y no sueltas porque son seis y siempre viajan juntas: sueltas
 * convertían la llamada en una fila de lambdas donde el orden era lo único que
 * distinguía una de otra.
 */
internal class AccionesDeFlujo(
    val alDejarDePreguntar: () -> Unit,
    val alPedirDestino: (Herramienta) -> Unit,
    val alPedirCarpeta: () -> Unit,
    /** Guarda lo que el flujo acaba de recoger, sin tocar lo que ya había. */
    val alRecoger: ((LoPedido) -> LoPedido) -> Unit,
    val alOlvidarAvisos: () -> Unit,
    val alSeguirTrasAvisos: () -> Unit,
)

/**
 * Los destinos de las partes al dividir, numerados a partir del que eligió el usuario.
 *
 * El selector del sistema entrega un fichero por ronda y aquí hacen falta varios, así
 * que el resto se escribe al lado del primero: «contrato-parte1.pdf» elegido a mano,
 * y «-parte2», «-parte3»… detrás.
 */
internal fun destinosNumerados(
    primero: OrigenDocumento,
    cuantos: Int,
): List<OrigenDocumento> =
    (1..cuantos).map { numero ->
        if (numero == 1) {
            primero
        } else {
            val base = primero.identificador.substringBeforeLast(".pdf")
            OrigenDocumento.Externo("$base-parte$numero.pdf", "parte$numero.pdf")
        }
    }

/** El nombre que el proveedor le ha puesto de verdad al fichero recién creado. */
internal fun nombreDeUri(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: "resultado.pdf"

internal fun nombreDe(origen: OrigenDocumento): String =
    when (origen) {
        is OrigenDocumento.Externo -> origen.nombre
        is OrigenDocumento.Privado -> origen.nombre
    }
