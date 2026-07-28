package com.marcmayol.dracpdf.ui.tema

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Radios de la sección 16: botón 20, tarjeta 12, hoja 28, campo 10, chip 16.
val FormasLadon =
    Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(10.dp), // campo
        medium = RoundedCornerShape(12.dp), // tarjeta
        large = RoundedCornerShape(20.dp), // botón
        extraLarge = RoundedCornerShape(28.dp), // hoja inferior
    )

/** El chip no tiene rol propio en Material y el diseño le da 16 dp. */
val FormaChip = RoundedCornerShape(16.dp)

/** La píldora de página: 32 dp de alto, radio 16. */
val FormaPildora = RoundedCornerShape(16.dp)

/**
 * Medidas de la sección 16. Están aquí y no repartidas por las pantallas porque son
 * decisiones del diseño, no de cada componente.
 */
object MedidasLadon {
    /** Barra superior: sólo identidad y acción de página. */
    val barraSuperior = 56.dp

    /** Barra inferior de cuatro acciones. */
    val barraInferior = 80.dp

    /** Barra contextual de modo, y barra de herramientas de modo. */
    val barraContextual = 56.dp

    /**
     * Área táctil mínima. Se aplica aunque el icono mida 24: el dedo no encoge
     * porque el glifo sea pequeño.
     */
    val areaTactil = 48.dp

    /** Tamaño de dibujo de todo icono, y diámetro de los handles. */
    val icono = 24.dp

    /** Margen de la rejilla y hueco entre elementos. */
    val margen = 16.dp
    val hueco = 10.dp

    /** Altura de la píldora «3 / 12». */
    val pildoraPagina = 32.dp

    /** Grosor de la barra de progreso lineal. */
    val progreso = 4.dp

    /** Contorno del elemento seleccionado en los modos táctiles. */
    val contornoSeleccion = 2.dp
}
