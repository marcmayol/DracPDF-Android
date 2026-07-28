package com.marcmayol.dracpdf.ui.iconos

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.marcmayol.dracpdf.ui.tema.MedidasLadon

/**
 * Un icono del paquete Ladón, dibujado a 24 dp y tintado por su estado.
 *
 * No admite un [Color] arbitrario a propósito: el diseño fija seis tintes y esta
 * firma impide que una pantalla se invente el séptimo.
 */
@Composable
fun IconoLadon(
    @DrawableRes icono: Int,
    descripcion: String?,
    modifier: Modifier = Modifier,
    estado: EstadoIcono = EstadoIcono.NORMAL,
) {
    Icon(
        painter = painterResource(icono),
        contentDescription = descripcion,
        tint = TintesLadon.de(estado),
        modifier = modifier.size(MedidasLadon.icono),
    )
}

/**
 * Icono pulsable. El área táctil es de 48 dp aunque el glifo mida 24: el dedo no
 * encoge porque el dibujo sea pequeño, y es la primera regla de accesibilidad que
 * el diseño repite en cada sección.
 *
 * Cuando está deshabilitado sigue viéndose, atenuado: el inventario de acciones
 * exige que una acción que aún no existe se muestre y no se pueda pulsar, en vez de
 * desaparecer y dejar al usuario buscándola.
 */
@Composable
fun BotonIconoLadon(
    @DrawableRes icono: Int,
    descripcion: String,
    alPulsar: () -> Unit,
    modifier: Modifier = Modifier,
    habilitado: Boolean = true,
    estado: EstadoIcono = EstadoIcono.NORMAL,
) {
    IconButton(
        onClick = alPulsar,
        enabled = habilitado,
        modifier =
            modifier
                .size(MedidasLadon.areaTactil)
                .semantics { role = Role.Button },
    ) {
        IconoLadon(
            icono = icono,
            descripcion = descripcion,
            estado = if (habilitado) estado else EstadoIcono.DESHABILITADO,
        )
    }
}
