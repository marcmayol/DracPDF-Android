package com.dracpdf.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val LadonShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(10.dp),   // campos de texto
    medium     = RoundedCornerShape(12.dp),   // tarjetas
    large      = RoundedCornerShape(20.dp),   // botones tipo pill
    extraLarge = RoundedCornerShape(28.dp),   // hojas inferiores (esquinas superiores)
)

object LadonDimens {
    val TopBar        = 56.dp
    val BottomBar     = 80.dp
    val ContextBar    = 56.dp
    val TouchTarget   = 48.dp
    val Icon          = 24.dp
    val Handle        = 24.dp
    val PagePill      = 32.dp
    val ProgressBar   = 4.dp
    val GridMargin    = 16.dp
    val GridGap       = 10.dp
    val SheetRow      = 60.dp
    val TreeRow       = 48.dp
    val NumKey        = 52.dp
    val SidePanelMin  = 200.dp   // expanded
    val SidePanelMax  = 280.dp
}
