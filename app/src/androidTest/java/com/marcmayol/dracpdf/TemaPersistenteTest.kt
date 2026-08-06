package com.marcmayol.dracpdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.marcmayol.dracpdf.adaptadores.ajustes.AjustesDeInterfaz
import com.marcmayol.dracpdf.ui.tema.PreferenciaTema
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * El tema elegido sigue elegido mañana.
 *
 * Es la regresión que pide el criterio de la fase, y se comprueba donde de verdad
 * duele: **releyendo el disco desde cero**, como hace el arranque de la aplicación.
 * Un test que se fiara del objeto que acaba de escribir pasaría siempre, incluso si
 * no se guardara nada.
 */
@RunWith(AndroidJUnit4::class)
class TemaPersistenteTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun dejarloComoEstaba() {
        runBlocking { AjustesDeInterfaz(contexto).elegirTema(PreferenciaTema.SISTEMA.name) }
    }

    @Test
    fun lo_elegido_sobrevive_a_un_arranque_nuevo() {
        runBlocking { AjustesDeInterfaz(contexto).elegirTema(PreferenciaTema.CLARO.name) }

        // Otro objeto, el mismo disco: es lo que ocurre al matar la aplicación y
        // volver a abrirla.
        val delSiguienteArranque = AjustesDeInterfaz(contexto).temaGuardado()

        assertEquals(PreferenciaTema.CLARO, PreferenciaTema.de(delSiguienteArranque))
    }

    @Test
    fun volver_al_del_sistema_tambien_se_guarda() {
        runBlocking {
            val ajustes = AjustesDeInterfaz(contexto)
            ajustes.elegirTema(PreferenciaTema.OSCURO.name)
            ajustes.elegirTema(PreferenciaTema.SISTEMA.name)
        }

        // «Del sistema» es una elección, no la ausencia de elección: quien vuelve a
        // ella tiene que encontrarla puesta, y no el oscuro de antes.
        assertEquals(PreferenciaTema.SISTEMA, PreferenciaTema.de(AjustesDeInterfaz(contexto).temaGuardado()))
    }
}
