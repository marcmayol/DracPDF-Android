package com.marcmayol.dracpdf.ui.inicio

/**
 * Un documento reciente, tal como lo enseña el inicio.
 *
 * Es un modelo de la interfaz y no el del dominio: aquí se guarda ya resuelto lo que
 * hay que pintar —«hace dos días», «pág. 12 de 40»— porque calcularlo dentro del
 * composable lo recalcularía en cada recomposición.
 */
data class RecienteEnLista(
    val identificador: String,
    val nombre: String,
    val cuando: String,
    val porDonde: String?,
    /**
     * Si el permiso para volver a abrirlo sigue en pie.
     *
     * Los documentos que llegaron compartidos desde otra aplicación se apuntan igual
     * —el usuario los ha visto y los busca aquí— pero puede que ya no abran. Se avisa
     * antes de tocarlos, que es mejor que un error después.
     */
    val puedeQueNoAbra: Boolean = false,
)
