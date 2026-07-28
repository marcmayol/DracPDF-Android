package com.marcmayol.dracpdf.ui.tema

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Los valores del tema son un contrato con el diseño, no una preferencia: si alguien
 * los cambia sin cambiar el diseño, esto se pone rojo. La tabla es la sección 16 de
 * «DracPDF Android».
 */
class TemaTest {
    @Test
    fun `el esquema oscuro usa los tokens Ladon de la seccion 16`() {
        val e = EsquemaOscuroLadon
        assertEquals(Color(0xFFE0534A), e.primary)
        assertEquals(Color(0xFF1A1D23), e.onPrimary)
        assertEquals(Color(0xFF3A2523), e.primaryContainer)
        assertEquals(Color(0xFF14161A), e.background)
        assertEquals(Color(0xFF1A1D23), e.surface)
        assertEquals(Color(0xFF22262E), e.surfaceContainer)
        assertEquals(Color(0xFF2A2F39), e.surfaceContainerHigh)
        assertEquals(Color(0xFFE9EBF0), e.onSurface)
        assertEquals(Color(0xFF98A0B0), e.onSurfaceVariant)
        assertEquals(Color(0xFF343A46), e.outline)
        assertEquals(Color(0xFF2A2F39), e.outlineVariant)
        assertEquals(Color(0xFFE07B6E), e.error)
        assertEquals(Color(0xFF6FBF87), e.tertiary)
        assertEquals(Color(0xFFD9B45C), ColoresLadonOscuro.firmaDesconocida)
    }

    @Test
    fun `el esquema claro usa los tokens Ladon de la seccion 16`() {
        val e = EsquemaClaroLadon
        assertEquals(Color(0xFFA83228), e.primary)
        assertEquals(Color(0xFFFFFFFF), e.onPrimary)
        assertEquals(Color(0xFFF3D9D5), e.primaryContainer)
        assertEquals(Color(0xFFD8DAE0), e.background)
        assertEquals(Color(0xFFF2F2F5), e.surface)
        assertEquals(Color(0xFFFFFFFF), e.surfaceContainer)
        assertEquals(Color(0xFFE9EAEF), e.surfaceContainerHigh)
        assertEquals(Color(0xFF22252C), e.onSurface)
        assertEquals(Color(0xFF6A7080), e.onSurfaceVariant)
        assertEquals(Color(0xFFC9CCD6), e.outline)
        assertEquals(Color(0xFFE0E2E9), e.outlineVariant)
        assertEquals(Color(0xFFB23B2E), e.error)
        assertEquals(Color(0xFF2E7D4F), e.tertiary)
        assertEquals(Color(0xFF96741F), ColoresLadonClaro.firmaDesconocida)
    }

    @Test
    fun `los dos esquemas no comparten acento ni fondo`() {
        assertNotEquals(EsquemaOscuroLadon.primary, EsquemaClaroLadon.primary)
        assertNotEquals(EsquemaOscuroLadon.background, EsquemaClaroLadon.background)
        assertNotEquals(EsquemaOscuroLadon.onSurface, EsquemaClaroLadon.onSurface)
    }

    @Test
    fun `los colores del papel son los del diseno y no dependen del tema`() {
        // No hay variante clara ni oscura del papel: el documento se pinta siempre
        // claro y lo que se dibuja encima le pertenece a él, no a la aplicación.
        assertEquals(Color(0xFFFDFDFC), ColoresPapel.papel)
        assertEquals(Color(0x66D9B45C), ColoresPapel.coincidencia)
        assertEquals(Color(0x80E0534A), ColoresPapel.coincidenciaActiva)
        assertEquals(Color(0xFFA83228), ColoresPapel.coincidenciaActivaBorde)
        assertEquals(Color(0x665B86BD), ColoresPapel.seleccion)
        assertEquals(Color(0x1FD9B45C), ColoresPapel.campoPendiente)
        assertEquals(Color(0xFFC9A24A), ColoresPapel.campoPendienteBorde)
        assertEquals(Color(0x0FE0534A), ColoresPapel.campoActivo)
        assertEquals(Color(0xFFA83228), ColoresPapel.campoActivoBorde)
    }

    @Test
    fun `el handle de seleccion es azul acero en ambos temas, porque no es acento`() {
        assertEquals(Color(0xFF5B86BD), ColoresLadonOscuro.handleSeleccion)
        assertEquals(Color(0xFF5B86BD), ColoresLadonClaro.handleSeleccion)
    }

    @Test
    fun `la tipografia respeta la escala de la seccion 16`() {
        assertEquals(36f, TipografiaLadon.displaySmall.fontSize.value)
        assertEquals(24f, TipografiaLadon.headlineSmall.fontSize.value)
        assertEquals(22f, TipografiaLadon.titleLarge.fontSize.value)
        assertEquals(16f, TipografiaLadon.titleMedium.fontSize.value)
        assertEquals(14f, TipografiaLadon.bodyMedium.fontSize.value)
        assertEquals(14f, TipografiaLadon.labelLarge.fontSize.value)
        assertEquals(11f, TipografiaLadon.labelSmall.fontSize.value)
    }

    @Test
    fun `las medidas son las de la seccion 16`() {
        assertEquals(56f, MedidasLadon.barraSuperior.value)
        assertEquals(80f, MedidasLadon.barraInferior.value)
        assertEquals(56f, MedidasLadon.barraContextual.value)
        assertEquals(48f, MedidasLadon.areaTactil.value)
        assertEquals(24f, MedidasLadon.icono.value)
        assertEquals(16f, MedidasLadon.margen.value)
        assertEquals(10f, MedidasLadon.hueco.value)
        assertEquals(32f, MedidasLadon.pildoraPagina.value)
        assertEquals(4f, MedidasLadon.progreso.value)
    }
}
