package com.marcmayol.dracpdf.dominio.casos

import com.marcmayol.dracpdf.dominio.modelo.Coincidencia
import com.marcmayol.dracpdf.dominio.modelo.IdDocumento
import com.marcmayol.dracpdf.dominio.puertos.TextoPdf

/**
 * Busca un texto por todo el documento, empezando por donde está el lector.
 *
 * Dos decisiones gobiernan esto y las dos son de móvil:
 *
 * **Se empieza por la página actual y se da la vuelta al final.** Quien busca «total»
 * en la página 40 de una factura no quiere que le lleven a la 3; quiere la siguiente.
 *
 * **Se entrega lo que se va encontrando.** Un documento largo tarda, y una búsqueda
 * que no enseña nada hasta el final parece rota. Cada página que aparece se cuenta, y
 * quien escucha puede parar en cuanto tenga bastante o el usuario cambie de idea.
 */
class BuscarEnDocumento(
    private val texto: TextoPdf,
) {
    /**
     * @param alEncontrar se llama por cada página con resultados; devolver `false`
     *   para dejarlo aquí.
     * @return todas las coincidencias que se llegaron a recorrer.
     */
    operator fun invoke(
        id: IdDocumento,
        buscado: String,
        paginas: Int,
        desde: Int = 0,
        alEncontrar: (List<Coincidencia>) -> Boolean = { true },
    ): List<Coincidencia> {
        if (buscado.isBlank()) return emptyList()
        require(paginas > 0) { "Un documento sin páginas no tiene dónde buscar" }

        val encontradas = mutableListOf<Coincidencia>()
        // Todas las páginas, pero empezando por la de delante: la 40, 41, 42… y al
        // llegar al final, la 0.
        val recorrido = (0 until paginas).map { (desde.coerceIn(0, paginas - 1) + it) % paginas }

        for (pagina in recorrido) {
            val enLaPagina = texto.buscar(id, pagina, buscado).map { Coincidencia(pagina, it) }
            if (enLaPagina.isEmpty()) continue

            encontradas += enLaPagina
            if (!alEncontrar(enLaPagina)) return encontradas
        }
        return encontradas
    }
}
