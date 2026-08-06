package com.marcmayol.dracpdf.dominio.puertos

import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento

/**
 * Aviso de por dónde va una operación larga, y la única manera de pararla.
 *
 * Devolver `false` cancela: no hay banderas compartidas ni hilos que interrumpir, y
 * quien ejecuta sólo tiene que mirar lo que le contestan. Cancelar deja el destino
 * **sin escribir**, no a medias, porque la salida se monta aparte y sólo se pone en
 * su sitio cuando está entera.
 */
fun interface Progreso {
    /**
     * @param hechas cuántas unidades van completadas —páginas, casi siempre—.
     * @param total cuántas hay en total; 0 si todavía no se sabe.
     * @return `false` para cancelar la operación.
     */
    fun paso(
        hechas: Int,
        total: Int,
    ): Boolean

    companion object {
        /** El que no mira ni cancela, para las llamadas que no enseñan progreso. */
        val NINGUNO = Progreso { _, _ -> true }
    }
}

/** Lo que ocupaba antes y lo que ocupa después, en bytes. */
data class Reduccion(
    val antes: Long,
    val despues: Long,
) {
    /** Lo que se ha ahorrado, entre 0 y 1. Negativo si el fichero ha crecido. */
    val fraccion: Float get() = if (antes <= 0L) 0f else (antes - despues).toFloat() / antes
}

/**
 * Una página del documento de origen, con lo que hay que hacerle.
 *
 * Reordenar, eliminar y rotar son la misma operación descrita de tres maneras: se
 * declara la lista de páginas que tendrá el resultado, en el orden que tendrá, y cada
 * una dice cuánto gira. Lo que no aparece en la lista, desaparece.
 */
data class PaginaOrdenada(
    /** Índice en el documento de origen, base cero. */
    val original: Int,
    /** Giro en grados, múltiplo de 90 y positivo en sentido horario. */
    val giro: Int = 0,
) {
    init {
        require(original >= 0) { "La página $original no existe" }
        require(giro % GRADOS_POR_CUARTO == 0) { "Un PDF sólo gira en cuartos de vuelta, y se pidió $giro" }
    }

    private companion object {
        const val GRADOS_POR_CUARTO = 90
    }
}

/** Los formatos de imagen a los que se puede convertir una página. */
enum class FormatoImagen {
    PNG,
    JPEG,
    WEBP,
}

/**
 * Cómo se quieren las imágenes: en qué formato, a qué tamaño y con cuánta pérdida.
 *
 * Van juntos porque siempre se deciden juntos —la calidad no significa nada sin el
 * formato— y porque separados convertían la llamada en una fila de siete argumentos
 * donde el orden era lo único que distinguía la escala de la calidad.
 */
data class AjustesImagen(
    val formato: FormatoImagen,
    /** 1 es el tamaño natural de la página; 2 dobla el número de píxeles por lado. */
    val escala: Float,
    /** Sólo la miran los formatos con pérdida. */
    val calidad: Int = CALIDAD_POR_DEFECTO,
) {
    init {
        require(escala > 0f) { "La escala tiene que ser positiva, y llegó $escala" }
        require(calidad in 1..CALIDAD_MAXIMA) { "La calidad va de 1 a $CALIDAD_MAXIMA, y llegó $calidad" }
    }

    companion object {
        const val CALIDAD_POR_DEFECTO = 85
        const val CALIDAD_MAXIMA = 100
    }
}

/**
 * Las operaciones que producen un documento nuevo a partir de otros.
 *
 * **Todas trabajan sobre orígenes, no sobre documentos abiertos.** Unir toma varios
 * ficheros y dividir produce varios, así que el identificador de sesión —que existe
 * para lo que se está mirando— aquí no sirve de nada. Quien tenga uno de esos ficheros
 * abierto y con cambios sin guardar es cosa del caso de uso, que avisa antes de operar
 * sobre una versión del disco que no es la que el usuario ve en pantalla.
 *
 * **El destino se escribe entero o no se escribe.** Ninguna de estas operaciones puede
 * dejar un PDF a medias: se construye aparte y se pone en su sitio de una vez.
 */
interface HerramientasPdf {
    /**
     * Cuántas páginas tiene, sin abrirlo como sesión. Lo necesita quien va a dividir o
     * a reorganizar para saber contra qué valida.
     */
    fun paginasDe(origen: OrigenDocumento): Int

    /**
     * Pega los documentos, uno detrás de otro, en el orden en que llegan.
     *
     * @throws IllegalArgumentException si no hay al menos dos.
     */
    fun unir(
        origenes: List<OrigenDocumento>,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    )

    /**
     * Escribe en [destino] las páginas pedidas de [origen], en ese orden y con los
     * giros que digan.
     *
     * Es la única operación de páginas que hay, y de ella salen las cuatro que ve el
     * usuario: extraer es quedarse con unas pocas, eliminar es no nombrarlas,
     * reordenar es nombrarlas en otro orden y rotar es darles giro. Una sola
     * implementación que no puede contradecirse consigo misma.
     */
    fun reorganizar(
        origen: OrigenDocumento,
        paginas: List<PaginaOrdenada>,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    )

    /**
     * Pone contraseña de apertura. La de propietario, si no se da, es la misma: un
     * PDF con permisos que cualquiera puede levantar sabiendo la de usuario no protege
     * de nada, y prometerlo sería peor que no ofrecerlo.
     */
    fun proteger(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
        contrasenaPropietario: String = contrasena,
    )

    /**
     * Quita la contraseña, **sabiéndola**. No hay ni habrá camino que la adivine: esto
     * sirve para quitarle la llave a un documento propio, no para abrir el ajeno.
     *
     * @throws com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento.NecesitaContrasena si la contraseña no abre.
     */
    fun desproteger(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
    )

    /**
     * Reescribe el documento apretándolo: objetos repetidos que pasan a ser uno,
     * flujos recomprimidos y basura fuera.
     *
     * Devuelve el antes y el después medidos, no estimados. Un PDF ya optimizado puede
     * crecer unos bytes, y eso hay que poder decirlo en vez de fingir una mejora.
     */
    fun comprimir(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Reduccion

    /**
     * Rasteriza las páginas pedidas a [carpeta], una imagen por página.
     *
     * @return los ficheros escritos, en el orden de las páginas.
     */
    fun aImagenes(
        origen: OrigenDocumento,
        paginas: List<Int>,
        carpeta: OrigenDocumento,
        ajustes: AjustesImagen,
        progreso: Progreso = Progreso.NINGUNO,
    ): List<OrigenDocumento>

    /**
     * Vuelca el texto del documento.
     *
     * @return `false` si no había texto que sacar, que en un PDF escaneado es lo
     *   normal y hay que poder avisarlo en vez de entregar un fichero vacío.
     */
    fun aTexto(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Boolean
}
