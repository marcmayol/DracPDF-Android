package com.marcmayol.dracpdf.dominio

import com.marcmayol.dracpdf.dominio.modelo.DocumentoAbierto
import com.marcmayol.dracpdf.dominio.modelo.ErrorDocumento
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.Marca
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.modelo.PaginaRenderizada
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt
import com.marcmayol.dracpdf.dominio.puertos.DocumentRepository

/**
 * Doble del puerto para los tests del dominio. Además de responder, **lleva la
 * cuenta de lo que se le ha pedido**: sin eso no se puede demostrar que el visor
 * renderiza sólo las páginas visibles, que es medio criterio de la Fase 1.
 */
class RepositorioFalso(
    private val paginas: Int = 12,
    private val firmado: Boolean = false,
    private val contrasenaCorrecta: String? = null,
) : DocumentRepository {
    val renderizadas = mutableListOf<Pair<Int, Float>>()
    val cerrados = mutableListOf<IdDocumento>()
    val guardados = mutableListOf<IdDocumento>()
    var abiertos = 0
        private set

    /** Lo que diría el motor: si el documento en memoria difiere del fichero. */
    var cambiosPendientes = false

    /** Para el caso feo: el guardado falla y la marca no se puede quitar. */
    var fallaAlGuardar = false

    override fun abrir(
        id: IdDocumento,
        origen: OrigenDocumento,
        contrasena: String?,
    ): DocumentoAbierto {
        if (contrasenaCorrecta != null) {
            if (contrasena == null) throw ErrorDocumento.NecesitaContrasena(origen)
            if (contrasena != contrasenaCorrecta) throw ErrorDocumento.ContrasenaIncorrecta(origen)
        }
        abiertos++
        return DocumentoAbierto(
            id = id,
            nombre = nombreDe(origen),
            paginas = paginas,
            marcas = if (firmado) setOf(Marca.FIRMADO) else emptySet(),
        )
    }

    override fun tamanoPagina(
        id: IdDocumento,
        pagina: Int,
    ): TamanoPt = TamanoPt(ANCHO_A4_PT, ALTO_A4_PT)

    override fun renderizar(
        id: IdDocumento,
        pagina: Int,
        escala: Float,
    ): PaginaRenderizada {
        renderizadas += pagina to escala
        val ancho = (ANCHO_A4_PT * escala).toInt()
        val alto = (ALTO_A4_PT * escala).toInt()
        return PaginaRenderizada(
            pagina = pagina,
            escala = escala,
            ancho = ancho,
            alto = alto,
            pixeles = ByteArray(ancho * alto * BYTES_POR_PIXEL),
        )
    }

    override fun tieneCambiosSinGuardar(id: IdDocumento): Boolean = cambiosPendientes

    override fun guardarIncremental(id: IdDocumento) {
        if (fallaAlGuardar) throw ErrorDocumento.NoSePuedeAbrirElFichero(OrigenDocumento.Privado("/x", "x.pdf"))
        guardados += id
        cambiosPendientes = false
    }

    override fun cerrar(id: IdDocumento) {
        cerrados += id
    }

    private fun nombreDe(origen: OrigenDocumento): String =
        when (origen) {
            is OrigenDocumento.Externo -> origen.nombre
            is OrigenDocumento.Privado -> origen.nombre
        }

    private companion object {
        const val ANCHO_A4_PT = 595f
        const val ALTO_A4_PT = 842f
        const val BYTES_POR_PIXEL = 4
    }
}
