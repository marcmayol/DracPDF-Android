package com.marcmayol.dracpdf.dominio.modelo

/**
 * Qué clase de formulario trae un PDF. La distinción no es un tecnicismo: decide
 * qué se le puede prometer al usuario.
 *
 * XFA es el formato de formularios de Adobe que nunca fue estándar y que el propio
 * Acrobat trata como legado. MuPDF no lo rellena, y ningún visor que no sea Acrobat
 * lo hace bien. Cuando aparece hay que avisar, no fingir que se puede editar.
 */
enum class TipoFormulario {
    /** No hay formulario: el documento es sólo para leer. */
    NINGUNO,

    /** AcroForm, el estándar. Es el que esta aplicación rellena. */
    ACROFORM,

    /**
     * Híbrido: el PDF lleva XFA, pero también el AcroForm equivalente debajo. Se
     * puede rellenar por AcroForm, con el matiz de que quien lo emitió espera leer
     * el XFA, y algunos validadores oficiales sólo miran ahí.
     */
    XFA_HIBRIDO,

    /**
     * XFA puro: sin AcroForm debajo no hay nada que rellenar. Se muestra el
     * documento y se dice claramente que aquí no se puede rellenar.
     */
    XFA_PURO,
}

/** Si con este formulario se puede rellenar algo, aunque sea con reservas. */
val TipoFormulario.esRellenable: Boolean
    get() = this == TipoFormulario.ACROFORM || this == TipoFormulario.XFA_HIBRIDO

/** El formulario de un documento, visto desde fuera. */
data class Formulario(
    val tipo: TipoFormulario,
    /**
     * Cuántos campos tiene en total, contando todas las páginas. Es lo que permite
     * decir «este documento tiene formulario» sin haber recorrido nada más.
     */
    val campos: Int = 0,
) {
    val esRellenable: Boolean get() = tipo.esRellenable && campos > 0
}

/** Los tipos de campo del estándar, tal como los distingue la interfaz. */
enum class TipoCampo {
    TEXTO,
    CASILLA,
    RADIO,
    COMBO,
    LISTA,

    /** Botón de acción (enviar, imprimir, limpiar). No guarda valor. */
    BOTON,

    /** Hueco de firma. Lo rellena la Fase 4, no ésta. */
    FIRMA,

    /** El motor no sabe qué es. Se muestra bloqueado antes que adivinar. */
    DESCONOCIDO,
}

/**
 * El formato que el PDF declara para un campo de texto. Aquí sólo se registra; en
 * la Fase 2 tarea 4 es lo que elige el teclado que sale al enfocarlo.
 */
enum class FormatoTexto {
    NINGUNO,
    NUMERO,
    FECHA,
    HORA,

    /** Formato «especial» del estándar: teléfono, código postal y demás. */
    ESPECIAL,
}

/**
 * Un rectángulo en coordenadas de página, en puntos PDF. Es el sistema del
 * documento, no el de la pantalla: la transformación página→pantalla la aplica el
 * overlay, que es el único que sabe a qué escala se está dibujando.
 */
data class RectPt(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
) {
    val ancho: Float get() = x1 - x0
    val alto: Float get() = y1 - y0
}

/**
 * Un campo de formulario. Es una foto inmutable: leerlo no deja viva ninguna
 * referencia al motor, porque los objetos nativos de MuPDF mueren con la página que
 * los cargó y sacarlos de ahí es la forma corta de llegar a un SIGSEGV.
 */
data class CampoFormulario(
    /**
     * El nombre del campo en el PDF. **No identifica**: los botones de radio de un
     * mismo grupo comparten nombre a propósito, porque el nombre es el grupo.
     */
    val nombre: String,
    val pagina: Int,
    /** Posición del campo dentro de su página. Junto con la página, la identidad. */
    val indice: Int,
    val tipo: TipoCampo,
    /**
     * El valor del **campo**. En un grupo de radio es el del grupo entero, así que
     * los dos botones de un mismo grupo tienen aquí lo mismo aunque sólo uno esté
     * elegido: para saber cuál, está [marcado].
     */
    val valor: String,
    val marco: RectPt,
    /** Lo que el PDF quiere que se muestre al usuario, si lo trae. */
    val etiqueta: String? = null,
    /**
     * Si esta casilla o este botón de radio están marcados.
     *
     * Es un dato de **este** widget y no del campo, que es justo la diferencia que un
     * grupo de radio necesita: los dos botones comparten nombre y valor, y sólo uno
     * está elegido. Deducirlo del valor marcaría el grupo entero.
     */
    val marcado: Boolean = false,
    val opciones: List<String> = emptyList(),
    val soloLectura: Boolean = false,
    val obligatorio: Boolean = false,
    val multilinea: Boolean = false,
    val esContrasena: Boolean = false,
    /** Tope de caracteres que impone el PDF, si impone alguno. */
    val maxLongitud: Int? = null,
    val formatoTexto: FormatoTexto = FormatoTexto.NINGUNO,
) {
    val id: IdCampo get() = IdCampo(pagina, indice)

    /** Si el usuario puede cambiarlo desde el overlay de la Fase 2. */
    val esEditable: Boolean
        get() = !soloLectura && tipo != TipoCampo.BOTON && tipo != TipoCampo.FIRMA && tipo != TipoCampo.DESCONOCIDO

    companion object {
        /**
         * El estado «apagado» de casillas y radios, tal como lo nombra el estándar.
         *
         * El encendido, en cambio, no lo fija nadie: cada PDF lo llama como quiere
         * —`Yes`, `On`, `Si`—, así que lo único que se puede comprobar con certeza es
         * que algo **no** está apagado.
         */
        const val APAGADO = "Off"
    }
}

/**
 * Identidad de un campo dentro de un documento abierto: página y posición.
 *
 * Se usa la posición y no el nombre porque el nombre no es único —los radios de un
 * grupo lo comparten— y porque un formulario mal hecho puede repetirlo sin querer.
 */
data class IdCampo(
    val pagina: Int,
    val indice: Int,
) {
    override fun toString(): String = "$pagina:$indice"
}
