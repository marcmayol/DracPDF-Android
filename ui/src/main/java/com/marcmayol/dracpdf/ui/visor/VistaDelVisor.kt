package com.marcmayol.dracpdf.ui.visor

import com.marcmayol.dracpdf.adaptadores.ajustes.VistaGuardada
import com.marcmayol.dracpdf.dominio.modelo.PuntoPt
import com.marcmayol.dracpdf.dominio.modelo.TamanoPt

/**
 * Cómo se encaja la página en la pantalla antes de que nadie toque el zoom.
 *
 * Son dos y no tres: el «100 %» del escritorio aquí no significa nada, porque en un
 * móvil no hay una relación fija entre un punto del PDF y un píxel de la pantalla.
 */
enum class AjusteDeVista {
    /**
     * La página ocupa todo el ancho disponible. Es el arranque por defecto porque es
     * lo que hace legible un A4 de texto en un teléfono: el alto sobra, y se recorre
     * con el pulgar.
     */
    ANCHO,

    /**
     * La página entera cabe en la pantalla. Manda el lado que primero se queda sin
     * sitio, que en vertical es siempre el alto.
     */
    PAGINA,
    ;

    companion object {
        /**
         * El ajuste guardado bajo ese nombre. Lo que no se reconoce se lee como
         * [ANCHO], igual que el tema desconocido se lee como «del sistema»: un fichero
         * de una versión anterior no puede impedir abrir un documento.
         */
        fun de(guardado: String?): AjusteDeVista = entries.firstOrNull { it.name == guardado } ?: ANCHO
    }
}

/**
 * Cuánto se gira **lo que se ve**, que no es lo mismo que girar el documento.
 *
 * La distinción importa y es la razón de que esto viva en la interfaz: aquí no se
 * reescribe nada, así que un escaneado tumbado se puede leer sin tocar el fichero —y
 * sin invalidar su firma, si la lleva—. Girar las páginas de verdad es la herramienta
 * de organizar, que escribe un PDF nuevo.
 */
enum class GiroDeVista {
    NINGUNO,
    UN_CUARTO,
    MEDIA,
    TRES_CUARTOS,
    ;

    /** Los grados que hay que girar el dibujo. Salen del orden: son cuartos de vuelta. */
    val grados: Float get() = ordinal * GRADOS_POR_CUARTO

    /** Si el giro deja la página de lado, y por tanto intercambia su ancho y su alto. */
    val intercambiaLados: Boolean get() = ordinal % 2 != 0

    /**
     * El siguiente cuarto de vuelta. El botón es uno solo y da la vuelta entera en
     * cuatro toques: dos botones —a izquierda y a derecha— gastarían el doble de barra
     * para llegar a los mismos cuatro sitios.
     */
    val siguiente: GiroDeVista get() = entries[(ordinal + 1) % entries.size]

    /**
     * Dónde ha caído, dentro de la página, un toque que ocurrió en la pantalla.
     *
     * Es la vuelta atrás del giro que se dibuja, y sin ella la vista girada sería sólo
     * un adorno: el dedo seguiría seleccionando texto y siguiendo enlaces en el sitio
     * donde estaban **antes** de girar, que es un fallo que no se ve venir porque la
     * página sí se ve bien.
     *
     * Entran las dos fracciones de lo que se ve —0 a 1, esquina de arriba a la
     * izquierda— y sale el punto en el sistema de la página.
     */
    fun puntoEnLaPagina(
        fraccionX: Float,
        fraccionY: Float,
        tamano: TamanoPt,
    ): PuntoPt {
        val enLaPagina =
            when (this) {
                NINGUNO -> fraccionX to fraccionY
                UN_CUARTO -> fraccionY to (1f - fraccionX)
                MEDIA -> (1f - fraccionX) to (1f - fraccionY)
                TRES_CUARTOS -> (1f - fraccionY) to fraccionX
            }
        return PuntoPt(enLaPagina.first * tamano.ancho, enLaPagina.second * tamano.alto)
    }

    companion object {
        private const val GRADOS_POR_CUARTO = 90f

        fun de(guardado: String?): GiroDeVista = entries.firstOrNull { it.name == guardado } ?: NINGUNO
    }
}

/**
 * Cómo quiere ver el documento quien lo está leyendo.
 *
 * Las tres van juntas porque se guardan juntas y porque se aplican en el mismo sitio
 * —el ancho que se le da a una página—, así que separarlas obligaría a cuadrar tres
 * estados que sólo tienen sentido a la vez.
 */
data class VistaDelVisor(
    val ajuste: AjusteDeVista = AjusteDeVista.ANCHO,
    /**
     * Si el lector **quiere** dos páginas lado a lado. Que quepan es otra cosa, y la
     * decide la pantalla: se guarda el deseo y no el resultado, porque el mismo
     * documento se abre hoy en el móvil de pie y mañana en la tablet.
     */
    val doblePagina: Boolean = false,
    val giro: GiroDeVista = GiroDeVista.NINGUNO,
) {
    /** Cuántas páginas van en una fila, ya contando con si de verdad caben dos. */
    fun paginasPorFila(cabenDos: Boolean): Int = if (doblePagina && cabenDos) 2 else 1

    companion object {
        fun de(guardada: VistaGuardada): VistaDelVisor =
            VistaDelVisor(
                ajuste = AjusteDeVista.de(guardada.ajuste),
                doblePagina = guardada.doblePagina == true,
                giro = GiroDeVista.de(guardada.giro),
            )
    }
}
