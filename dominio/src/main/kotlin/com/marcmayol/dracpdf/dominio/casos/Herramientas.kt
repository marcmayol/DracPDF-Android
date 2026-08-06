package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.AjustesImagen
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository
import com.marcmayol.dracpdf.dominio.puertos.HerramientasPdf
import com.marcmayol.dracpdf.dominio.puertos.PaginaOrdenada
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import com.marcmayol.dracpdf.dominio.puertos.Reduccion
import com.marcmayol.dracpdf.dominio.puertos.SignatureService
import com.marcmayol.dracpdf.dominio.registro.RegistroDocumentos

/**
 * Lo que hay que decirle al usuario **antes** de empezar una operación larga.
 *
 * Se consulta y se enseña; no bloquea nada. La diferencia entre los dos avisos es la
 * que importa: uno es un no —un documento firmado no se puede reescribir sin romperle
 * la firma— y el otro es un «ojo, esto no es lo que estás viendo en pantalla».
 */
data class AvisosDeHerramienta(
    /** Ficheros firmados: sobre estos no se opera. */
    val firmados: List<OrigenDocumento> = emptyList(),
    /** Ficheros abiertos con cambios que aún no están en el disco. */
    val abiertosConCambios: List<OrigenDocumento> = emptyList(),
) {
    val impiden: Boolean get() = firmados.isNotEmpty()
    val hayAlguno: Boolean get() = impiden || abiertosConCambios.isNotEmpty()
}

/**
 * Revisa los ficheros elegidos antes de operar sobre ellos.
 *
 * Existe separado de las operaciones a propósito: la interfaz necesita saber qué va a
 * pasar **antes** de que el usuario confirme, y las operaciones necesitan negarse por
 * su cuenta aunque nadie las haya revisado. Las dos cosas miran lo mismo, y por eso lo
 * mira un solo sitio.
 */
class RevisarAntesDeOperar(
    private val firmas: SignatureService,
    private val registro: RegistroDocumentos,
    private val repositorio: DocumentRepository,
) {
    operator fun invoke(origenes: List<OrigenDocumento>): AvisosDeHerramienta {
        // El registro sabe qué documentos tienen cambios; de dónde salió cada uno lo
        // sabe el motor, que es quien lo abrió.
        val conCambios =
            registro
                .abiertos()
                .filter { Marca.CAMBIOS_SIN_GUARDAR in it.documento.marcas }
                .map { repositorio.origenDe(it.id).identificador }
                .toSet()

        return AvisosDeHerramienta(
            firmados = origenes.filter { firmas.verificar(it).isNotEmpty() },
            // Lo que se va a leer es el fichero del disco, no lo que el usuario tiene a
            // medio rellenar en pantalla. Decirlo antes evita el «pero si yo lo había
            // escrito» de después.
            abiertosConCambios = origenes.filter { it.identificador in conCambios },
        )
    }
}

/**
 * Comprueba lo que ninguna herramienta puede saltarse: no se escribe encima del
 * origen, y no se reescribe un documento firmado.
 *
 * Lo primero es una salvaguarda de las de verdad: estas operaciones leen el origen
 * mientras escriben el destino, así que apuntar los dos al mismo sitio no da un
 * documento mal hecho, da un documento perdido.
 */
private fun SignatureService.exigirQueSePuedaOperar(
    origenes: List<OrigenDocumento>,
    destino: OrigenDocumento,
) {
    require(origenes.none { it.identificador == destino.identificador }) {
        "El resultado no puede ser uno de los ficheros de origen: se leería mientras se escribe"
    }
    origenes.firstOrNull { verificar(it).isNotEmpty() }?.let { throw ErrorDocumento.FicheroFirmado(it) }
}

/** Pega varios PDF en uno, en el orden en que se eligieron. */
class UnirDocumentos(
    private val herramientas: HerramientasPdf,
    private val firmas: SignatureService,
) {
    operator fun invoke(
        origenes: List<OrigenDocumento>,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ) {
        require(origenes.size >= MINIMO_PARA_UNIR) { "Unir uno solo no es unir" }
        firmas.exigirQueSePuedaOperar(origenes, destino)
        herramientas.unir(origenes, destino, progreso)
    }

    private companion object {
        const val MINIMO_PARA_UNIR = 2
    }
}

/**
 * Extraer, eliminar, reordenar y rotar: las cuatro son declarar cómo queda el
 * documento y dejar fuera lo que no se nombra.
 */
class OrganizarPaginas(
    private val herramientas: HerramientasPdf,
    private val firmas: SignatureService,
) {
    operator fun invoke(
        origen: OrigenDocumento,
        paginas: List<PaginaOrdenada>,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ) {
        require(paginas.isNotEmpty()) { "Un PDF sin páginas no es un PDF" }
        val total = herramientas.paginasDe(origen)
        paginas.firstOrNull { it.original >= total }?.let {
            throw IllegalArgumentException("Se pidió la página ${it.original + 1} de un documento de $total")
        }
        firmas.exigirQueSePuedaOperar(listOf(origen), destino)
        herramientas.reorganizar(origen, paginas, destino, progreso)
    }
}

/**
 * Parte un documento en trozos por rangos de páginas.
 *
 * Los rangos van en base **uno y con los dos extremos dentro**, porque es como los
 * escribe quien los pide: «1-3» son tres páginas, no dos.
 */
class DividirDocumento(
    private val herramientas: HerramientasPdf,
    private val firmas: SignatureService,
) {
    operator fun invoke(
        origen: OrigenDocumento,
        rangos: List<IntRange>,
        destinos: List<OrigenDocumento>,
        progreso: Progreso = Progreso.NINGUNO,
    ) {
        require(rangos.isNotEmpty()) { "No se ha pedido ningún trozo" }
        require(rangos.size == destinos.size) { "Hay ${rangos.size} rangos y ${destinos.size} destinos" }
        val total = herramientas.paginasDe(origen)
        rangos.forEach { rango ->
            require(!rango.isEmpty() && rango.first >= 1 && rango.last <= total) {
                "El rango $rango no cabe en un documento de $total páginas"
            }
        }
        firmas.exigirQueSePuedaOperar(listOf(origen), destinos.first())

        var hechas = 0
        val paginasTotales = rangos.sumOf { it.count() }
        rangos.forEachIndexed { indice, rango ->
            herramientas.reorganizar(
                origen = origen,
                paginas = rango.map { PaginaOrdenada(original = it - 1) },
                destino = destinos[indice],
            )
            hechas += rango.count()
            if (!progreso.paso(hechas, paginasTotales)) return
        }
    }
}

/** Pone contraseña de apertura a una copia del documento. */
class ProtegerDocumento(
    private val herramientas: HerramientasPdf,
    private val firmas: SignatureService,
) {
    operator fun invoke(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
    ) {
        require(contrasena.isNotBlank()) { "Una contraseña en blanco no protege nada" }
        firmas.exigirQueSePuedaOperar(listOf(origen), destino)
        herramientas.proteger(origen, destino, contrasena)
    }
}

/** Quita la contraseña de un documento propio, sabiéndola. */
class DesprotegerDocumento(
    private val herramientas: HerramientasPdf,
) {
    operator fun invoke(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        contrasena: String,
    ) {
        require(origen.identificador != destino.identificador) {
            "El resultado no puede ser el propio fichero de origen"
        }
        herramientas.desproteger(origen, destino, contrasena)
    }
}

/** Aprieta el documento y dice cuánto ha encogido de verdad. */
class ComprimirDocumento(
    private val herramientas: HerramientasPdf,
    private val firmas: SignatureService,
) {
    operator fun invoke(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Reduccion {
        firmas.exigirQueSePuedaOperar(listOf(origen), destino)
        return herramientas.comprimir(origen, destino, progreso)
    }
}

/**
 * Sacar el documento a otra cosa: imágenes o texto.
 *
 * **No comprueba si está firmado, y es a propósito**: convertir no reescribe el PDF ni
 * promete conservar nada suyo. Sacar el texto de un contrato firmado para leerlo en
 * otro sitio es legítimo, y negarlo sería confundir «no romper la firma» con «no dejar
 * mirar».
 */
class ConvertirDocumento(
    private val herramientas: HerramientasPdf,
) {
    fun aImagenes(
        origen: OrigenDocumento,
        paginas: List<Int>,
        carpeta: OrigenDocumento,
        ajustes: AjustesImagen,
        progreso: Progreso = Progreso.NINGUNO,
    ): List<OrigenDocumento> {
        require(paginas.isNotEmpty()) { "No se ha pedido ninguna página" }
        return herramientas.aImagenes(origen, paginas, carpeta, ajustes, progreso)
    }

    /** @return `false` si el documento no tenía texto: un escaneado, casi siempre. */
    fun aTexto(
        origen: OrigenDocumento,
        destino: OrigenDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): Boolean {
        require(origen.identificador != destino.identificador) { "El texto no puede sobrescribir el PDF" }
        return herramientas.aTexto(origen, destino, progreso)
    }
}
