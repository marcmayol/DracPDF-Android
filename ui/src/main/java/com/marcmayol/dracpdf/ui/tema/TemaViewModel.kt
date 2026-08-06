package com.marcmayol.dracpdf.ui.tema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcmayol.dracpdf.adaptadores.ajustes.AjustesDeInterfaz
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * El tema elegido.
 *
 * Se aplica en el acto y se guarda después: el usuario ve el cambio al soltar el
 * dedo, sin esperar al disco. Si el guardado fallara, lo que se pierde es la
 * preferencia de la próxima vez, no el cambio de ahora.
 */
class TemaViewModel(
    private val ajustes: AjustesDeInterfaz,
    inicial: PreferenciaTema,
) : ViewModel() {
    private val _preferencia = MutableStateFlow(inicial)
    val preferencia: StateFlow<PreferenciaTema> = _preferencia.asStateFlow()

    fun elegir(nueva: PreferenciaTema) {
        if (nueva == _preferencia.value) return
        _preferencia.value = nueva
        viewModelScope.launch { ajustes.elegirTema(nueva.name) }
    }
}
