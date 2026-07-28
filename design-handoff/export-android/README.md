# DracPDF Android — assets e identidad Ladón

Paquete **solo Android**. Suelta `res/` sobre `app/src/main/res/` y los cuatro
`.kt` en `app/src/main/java/com/dracpdf/ui/theme/` (ajusta el `package`).

## Contenido

    res/drawable/ic_*.xml            56 VectorDrawable · retícula 24dp, trazo 2dp, un color
    res/drawable-<dpi>/ic_stat_*.png icono de notificación (24dp, blanco sobre transparente)
    res/mipmap-<dpi>/ic_launcher_*   capas del icono adaptativo (108dp)
    res/mipmap-anydpi-v26/           ic_launcher.xml + ic_launcher_round.xml (ya escritos)
    Color.kt Theme.kt Type.kt Shape.kt

Excluidos a propósito: `present`, `fullscreen` y `more_horiz` — el diseño dice que
pantalla completa no se porta (es el modo inmersivo, sin botón) y el menú es siempre
el ⋮ vertical. Viven en el paquete de escritorio, donde sí se usan.

## Iconos en Compose

    Icon(painterResource(R.drawable.ic_search), contentDescription = null)

Los XML llevan negro literal; el tinte lo pone `Icon` desde `LocalContentColor`.
Nunca hardcodear color en el asset ni usar PNG para iconos de UI: rompería el
tintado por tema y se vería borroso en pantallas 3×–4×.

## Manifest

    <application
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        ...>

## Notificación de operaciones largas (§20 del diseño)

    NotificationCompat.Builder(ctx, "operaciones")
        .setSmallIcon(R.drawable.ic_stat_dracpdf)
        .setColor(0xFFB03228.toInt())          // rojo brasa
        .setProgress(total, done, indeterminate)
        .addAction(0, "Cancelar", cancelIntent)

Canal `operaciones`, importancia baja: informa sin sonido ni vibración.
Umbrales: <1 s nada · 1–5 s progreso lineal en la propia hoja · >5 s ForegroundService.

## Reglas no negociables

- **Sin color dinámico.** `DracPdfTheme` no llama a `dynamicDarkColorScheme()`:
  aquí el color es información legal. Sí se obedece al sistema en claro/oscuro,
  escala de fuente, alto contraste y reducción de movimiento.
- **`PaperColors` no va en el `ColorScheme`.** El documento siempre se pinta claro,
  así que resaltados, selección y campos de formulario son constantes del canvas.
- **48 dp de área táctil** en todo lo pulsable, aunque el icono mida 24.
- **Un modo, una barra**: un `sealed class ViewerMode` (Read, Form, PlaceSignature,
  PlaceStamp, SelectText, OrganizePages, Markup) decide qué barra se dibuja. Es un
  `when`, no condicionales dispersos.
- Los tres estados de firma llevan **siempre icono + texto**: el color no es el
  único portador de significado.
- El dragón nunca se recolorea fuera de tinta `#111318` o blanco.
