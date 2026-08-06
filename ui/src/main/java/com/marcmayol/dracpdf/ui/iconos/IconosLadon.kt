package com.marcmayol.dracpdf.ui.iconos

import androidx.annotation.DrawableRes
import com.marcmayol.dracpdf.ui.R

/**
 * Los 58 iconos Android del paquete de identidad Ladón, tal como los entregó Claude Design.
 *
 * Los ficheros de `res/drawable/ic_*.xml` son copia literal de
 * `design-handoff/export/icons/android-vector/`: retícula de 24, trazo de 2,
 * terminales redondeados, un solo color. No se redibujan, no se optimizan y no se
 * inventan: si la interfaz necesita un icono que no está aquí, se pide al diseño.
 *
 * Son 58: los 56 de la entrega original más `help` y `about`, que llegaron con la
 * revisión de conformidad de la Fase 5. Tres del paquete se quedan fuera —`present`,
 * `fullscreen` y `more_horiz`— porque en móvil no existen: pantalla completa es el
 * modo inmersivo, que no tiene botón, y el menú es siempre el ⋮ vertical. Viven en el
 * paquete para el DracPDF de escritorio, que sí los usa.
 *
 * El color no viene en el asset —los XML llevan negro literal dentro— sino de quien
 * los pinta. Ver [TintesLadon].
 */
object IconosLadon {
    @DrawableRes val atras = R.drawable.ic_arrow_back

    @DrawableRes val marcador = R.drawable.ic_bookmark

    @DrawableRes val comprobado = R.drawable.ic_check

    @DrawableRes val cerrar = R.drawable.ic_close

    /** Los documentos abiertos a la vez. Llegó con la revisión del diseño de julio. */
    @DrawableRes val documentos = R.drawable.ic_documents

    @DrawableRes val comprimir = R.drawable.ic_compress

    @DrawableRes val convertir = R.drawable.ic_convert

    @DrawableRes val tabla = R.drawable.ic_table

    @DrawableRes val copiar = R.drawable.ic_copy

    @DrawableRes val asa = R.drawable.ic_drag_handle

    @DrawableRes val duplicar = R.drawable.ic_duplicate

    @DrawableRes val exportar = R.drawable.ic_export

    @DrawableRes val extraer = R.drawable.ic_extract

    @DrawableRes val ajustarPagina = R.drawable.ic_fit_page

    @DrawableRes val ajustarAncho = R.drawable.ic_fit_width

    @DrawableRes val formulario = R.drawable.ic_form_fill

    @DrawableRes val irAPagina = R.drawable.ic_goto_page

    @DrawableRes val resaltar = R.drawable.ic_highlight

    @DrawableRes val subrayar = R.drawable.ic_underline

    @DrawableRes val tachar = R.drawable.ic_strikethrough

    @DrawableRes val anadirImagen = R.drawable.ic_image_add

    @DrawableRes val bloquear = R.drawable.ic_lock

    @DrawableRes val enlace = R.drawable.ic_link

    @DrawableRes val unir = R.drawable.ic_merge

    @DrawableRes val mas = R.drawable.ic_more_vert

    @DrawableRes val nota = R.drawable.ic_note

    @DrawableRes val abrir = R.drawable.ic_open

    @DrawableRes val paginaDoble = R.drawable.ic_page_double

    @DrawableRes val paginaSiguiente = R.drawable.ic_page_next

    @DrawableRes val paginaAnterior = R.drawable.ic_page_prev

    @DrawableRes val paginaSimple = R.drawable.ic_page_single

    @DrawableRes val miniaturas = R.drawable.ic_pages_grid

    @DrawableRes val imprimir = R.drawable.ic_print

    @DrawableRes val propiedades = R.drawable.ic_properties

    @DrawableRes val reciente = R.drawable.ic_recent

    @DrawableRes val rehacer = R.drawable.ic_redo

    @DrawableRes val rotar = R.drawable.ic_rotate

    @DrawableRes val rotarIzquierda = R.drawable.ic_rotate_left

    @DrawableRes val guardar = R.drawable.ic_save

    @DrawableRes val buscar = R.drawable.ic_search

    @DrawableRes val seleccionarTodo = R.drawable.ic_select_all

    @DrawableRes val ajustes = R.drawable.ic_settings

    @DrawableRes val compartir = R.drawable.ic_share

    @DrawableRes val firmaCertificado = R.drawable.ic_sign_cert

    @DrawableRes val firmaDibujada = R.drawable.ic_sign_draw

    @DrawableRes val dividir = R.drawable.ic_split

    @DrawableRes val anadirTexto = R.drawable.ic_text_add

    /** Corregir texto existente: la «A» con onda, sin el «+» de añadir. */
    @DrawableRes val corregirTexto = R.drawable.ic_text_fix

    @DrawableRes val tema = R.drawable.ic_theme

    /**
     * Ayuda. Es un «?» en círculo y no la (i) que dibujaba la maqueta: el diseño
     * avisó de que la (i) en círculo **ya está cogida** por [propiedades], que son los
     * datos del PDF. Los dos comparten círculo a propósito, para que se lean como
     * familia y no como el mismo icono repetido.
     */
    @DrawableRes val ayuda = R.drawable.ic_help

    /**
     * «Acerca de DracPDF»: la marca, sin glifo nuevo. El diseño aceptó que una hoja de
     * documento suelta habría chocado con [documentos] en un lector de PDF, y aquí la
     * pantalla habla literalmente del dragón. Es un PNG monocromo por densidades, no un
     * vector: viene dibujado para 24 dp, no reducido desde la silueta de 512 px.
     */
    @DrawableRes val acercaDe = R.drawable.ic_about

    @DrawableRes val herramientas = R.drawable.ic_tools

    @DrawableRes val eliminar = R.drawable.ic_trash

    @DrawableRes val deshacer = R.drawable.ic_undo

    @DrawableRes val desbloquear = R.drawable.ic_unlock

    @DrawableRes val verificar = R.drawable.ic_verify

    @DrawableRes val acercar = R.drawable.ic_zoom_in

    @DrawableRes val alejar = R.drawable.ic_zoom_out

    /** La marca: silueta del dragón en tinta y en blanco, 512 px. */
    @DrawableRes val dragonTinta = R.drawable.marca_dragon_tinta

    @DrawableRes val dragonBlanco = R.drawable.marca_dragon_blanco

    /**
     * Todos los iconos del paquete, para que un test pueda comprobar de una vez que
     * cada uno de los declarados existe de verdad como recurso.
     */
    val todos: List<Int> =
        listOf(
            atras,
            marcador,
            comprobado,
            cerrar,
            documentos,
            comprimir,
            convertir,
            tabla,
            copiar,
            asa,
            duplicar,
            exportar,
            extraer,
            ajustarPagina,
            ajustarAncho,
            formulario,
            irAPagina,
            resaltar,
            subrayar,
            tachar,
            anadirImagen,
            bloquear,
            enlace,
            unir,
            mas,
            nota,
            abrir,
            paginaDoble,
            paginaSiguiente,
            paginaAnterior,
            paginaSimple,
            miniaturas,
            imprimir,
            propiedades,
            reciente,
            rehacer,
            rotar,
            rotarIzquierda,
            guardar,
            buscar,
            seleccionarTodo,
            ajustes,
            compartir,
            firmaCertificado,
            firmaDibujada,
            dividir,
            anadirTexto,
            corregirTexto,
            tema,
            herramientas,
            eliminar,
            deshacer,
            desbloquear,
            verificar,
            acercar,
            alejar,
            ayuda,
            acercaDe,
        )
}
