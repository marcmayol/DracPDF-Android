package com.marcmayol.dracpdf.ui.documentos

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Un documento abierto, tal como se enseña en la lista.
 *
 * Lleva `id` como cadena y no como `IdDocumento` a propósito: el módulo de interfaz
 * no tiene por qué conocer el modelo del dominio para pintar una fila, y así la
 * misma lista sirve para la hoja del visor y para la sección «Abiertos» del inicio.
 */
data class DocumentoEnLista(
    val id: String,
    val nombre: String,
    val paginaActual: Int,
    val paginas: Int,
    val abiertoEn: Long,
    val activo: Boolean,
    val miniatura: ImageBitmap? = null,
)
