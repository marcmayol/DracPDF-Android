package com.marcmayol.dracpdf.ui.visor

/**
 * En qué está el visor. **Un modo, una barra**: la barra inferior se deriva de esto
 * con un `when`, y no de condicionales repartidos por la pantalla.
 *
 * Que se derive del modo no es una preferencia de estilo. Los controles de un modo
 * —el «Hecho» que lo cierra, el «Cancelar» que lo deshace— tienen que ser
 * imposibles de encontrar fuera de él, y si la barra sale de un `when` sobre el
 * modo, eso ya no hay que acordarse de comprobarlo: no existe otro sitio donde
 * dibujarlos. El escritorio llegó a esa regla después de dejarse controles de
 * colocación visibles fuera de la colocación; aquí sale de fábrica.
 *
 * Los modos que faltan llegan con su fase, cada uno con su barra: colocar firma y
 * colocar sello (Fases 3 y 4), seleccionar texto (Fase 7), organizar páginas
 * (Fase 6) y marcado (Fase 8).
 */
sealed interface ModoVisor {
    /** Leer, que es lo que hace el visor mientras nadie le pida otra cosa. */
    data object Lectura : ModoVisor

    /** Rellenar el formulario del documento. */
    data object Formulario : ModoVisor

    /**
     * Colocar una firma dibujada sobre la página: se arrastra, se ajusta de tamaño y
     * se confirma. Hasta que se confirma, el documento no se ha tocado.
     */
    data object ColocarFirma : ModoVisor
}
