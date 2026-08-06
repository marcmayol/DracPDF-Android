package com.marcmayol.dracpdf.ui.iconos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El catálogo de iconos es un contrato con el paquete de identidad: si alguien borra
 * un `ic_*.xml` de `res/drawable/`, esto deja de compilar; si declara uno que no
 * existe, tampoco. Lo que este test añade es que el catálogo esté completo y sin
 * duplicados, que es lo que el compilador no puede ver.
 */
class IconosLadonTest {
    @Test
    fun `el catalogo tiene los 58 iconos que Android usa`() {
        // 56 de la entrega original —present, fullscreen y more_horiz son del
        // escritorio y en móvil no existen— más help y about, que llegaron con la
        // revisión de conformidad de la Fase 5.
        assertEquals(58, IconosLadon.todos.size)
    }

    @Test
    fun `ningun icono del catalogo apunta a un recurso vacio`() {
        assertTrue(IconosLadon.todos.none { it == 0 })
    }

    @Test
    fun `ningun icono esta declarado dos veces`() {
        assertEquals(IconosLadon.todos.size, IconosLadon.todos.distinct().size)
    }

    @Test
    fun `la marca no esta en el catalogo de iconos de accion`() {
        // El dragón no es un icono de acción y nunca se recolorea con los tintes de
        // interfaz: sólo existe en tinta o en blanco.
        assertTrue(IconosLadon.dragonTinta !in IconosLadon.todos)
        assertTrue(IconosLadon.dragonBlanco !in IconosLadon.todos)
    }

    @Test
    fun `hay un tinte declarado para cada estado`() {
        assertEquals(6, EstadoIcono.entries.size)
    }
}
