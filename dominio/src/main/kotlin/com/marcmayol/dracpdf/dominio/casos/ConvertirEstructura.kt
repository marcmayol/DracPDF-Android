package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.BloqueDeTexto
import com.marcmayol.dracpdf.dominio.modelo.DocumentoEstructurado
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.modelo.OrigenDocumento
import com.marcmayol.dracpdf.dominio.puertos.ConversorSalidas
import com.marcmayol.dracpdf.dominio.puertos.FormatoSalida
import com.marcmayol.dracpdf.dominio.puertos.FormatoTabla
import com.marcmayol.dracpdf.dominio.puertos.LectorDeEstructura
import com.marcmayol.dracpdf.dominio.puertos.Progreso
import com.marcmayol.dracpdf.dominio.puertos.RecuentoDeTablas

/**
 * Cómo acabó una conversión.
 *
 * Los dos «no» son valores y no excepciones porque **no son errores**: un PDF escaneado
 * y un PDF sin tablas están perfectamente bien, y lo único que pasa es que no hay nada
 * que escribir. Lanzar aquí obligaría a la interfaz a capturar para enseñar un aviso
 * corriente, y acabaría enseñándolo con cara de fallo.
 */
sealed interface ResultadoConversion {
    /** Se escribió, y estos son los ficheros. */
    data class Escrito(
        val ficheros: List<OrigenDocumento>,
    ) : ResultadoConversion

    /**
     * No había ni una letra que sacar: un escaneado, casi siempre.
     *
     * **No se ha escrito nada**, y ése es el punto: entregar un .odt vacío deja al
     * usuario mirando un documento en blanco sin saber que su PDF son fotos de papel.
     */
    data object PareceEscaneado : ResultadoConversion

    /** No se encontró ninguna tabla. Tampoco se ha escrito nada. */
    data object SinTablas : ResultadoConversion
}

/**
 * Convertir el documento que se está mirando a otra cosa: HTML, Markdown, texto, ODT,
 * RTF, o sus tablas a CSV y XLSX.
 *
 * **La lectura y la escritura se piden por separado, y es a propósito.** Deducir la
 * estructura recorre el documento entero y es lo caro; escribirla es instantáneo. Quien
 * quiera contar las tablas antes de convertir —que es lo que exige la fase— y luego
 * escribirlas, lee una vez y escribe después, en vez de pagar dos recorridos para
 * responder a la misma pregunta.
 *
 * **No comprueba si el documento está firmado**, igual que [ConvertirDocumento] y por el
 * mismo motivo: convertir no reescribe el PDF ni le toca la firma. Sacar el texto de un
 * contrato firmado para leerlo en otro sitio es legítimo.
 */
class ConvertirEstructura(
    private val lector: LectorDeEstructura,
    private val conversor: ConversorSalidas,
) {
    /** Lee el documento abierto como contenido. Es el paso caro; se hace una vez. */
    fun leer(
        id: IdDocumento,
        progreso: Progreso = Progreso.NINGUNO,
    ): DocumentoEstructurado = lector.estructuraDe(id, progreso)

    /**
     * Cuántas tablas hay y dónde, **antes** de convertir.
     *
     * Se contesta sobre la estructura ya leída y no volviendo al motor: preguntar dos
     * veces por lo mismo puede dar dos respuestas distintas si la heurística cambia, y
     * enseñar «3 tablas» para luego escribir 2 sería peor que no contarlas.
     */
    fun recuentoDeTablas(documento: DocumentoEstructurado): RecuentoDeTablas {
        val tablas = tablasDe(documento)
        return RecuentoDeTablas(
            paginas = tablas.map { it.pagina },
            algunaAproximada = tablas.any { it.aproximada },
        )
    }

    /**
     * Escribe [documento] en [carpeta] con el formato pedido.
     *
     * @return [ResultadoConversion.PareceEscaneado] si no había texto, sin tocar el
     *   disco.
     */
    fun aDocumento(
        documento: DocumentoEstructurado,
        formato: FormatoSalida,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ): ResultadoConversion {
        require(nombreBase.isNotBlank()) { "Un fichero sin nombre no se puede escribir" }
        if (documento.vacio) return ResultadoConversion.PareceEscaneado
        return ResultadoConversion.Escrito(
            listOf(conversor.escribir(documento, formato, carpeta, nombreBase)),
        )
    }

    /**
     * Escribe las tablas de [documento] en [carpeta].
     *
     * @return [ResultadoConversion.SinTablas] si no hay ninguna, sin tocar el disco.
     */
    fun aTablas(
        documento: DocumentoEstructurado,
        formato: FormatoTabla,
        carpeta: OrigenDocumento,
        nombreBase: String,
    ): ResultadoConversion {
        require(nombreBase.isNotBlank()) { "Un fichero sin nombre no se puede escribir" }
        val tablas = tablasDe(documento)
        if (tablas.isEmpty()) return ResultadoConversion.SinTablas
        return ResultadoConversion.Escrito(
            conversor.escribirTablas(tablas, formato, carpeta, nombreBase),
        )
    }

    /** En orden de aparición, que es el orden en el que se numeran los ficheros. */
    private fun tablasDe(documento: DocumentoEstructurado): List<BloqueDeTexto.Tabla> =
        documento.bloques.filterIsInstance<BloqueDeTexto.Tabla>()
}
