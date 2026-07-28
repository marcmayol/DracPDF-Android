# DracPDF — set de iconos y assets de marca

Generado desde el sistema visual Ladón. Todo monocromo y recoloreable: ningún icono
lleva color propio. Retícula 24, trazo 2, terminales redondeados.

## Estructura

    icons/svg/                 53 SVG fuente, stroke="currentColor" — la verdad de origen
    icons/android-vector/      los mismos como VectorDrawable (ic_*.xml) para res/drawable/
    icons/qt-tinted/<tema>/    copias pre-tintadas para QIcon (dark, light, accent, ember, muted, disabled)
    android/mipmap-*/          capas del icono adaptativo por densidad (108dp)
    android/drawable-*/        ic_stat_dracpdf.png — icono de notificación (24dp, blanco)
    desktop/icon-*.png         iconos de app Windows/Linux (16-256; "PDF" a partir de 32)
    brand/                     silueta del dragón en tinta y en blanco (512px)

## Android

Iconos de UI: usar `icons/android-vector/*.xml` en `res/drawable/`.
En Compose: `Icon(painterResource(R.drawable.ic_search), null)` — hereda `LocalContentColor`.
Los XML llevan color negro literal; el tinte lo pone `Icon`/`android:tint`, no el asset.

Icono de launcher — `res/mipmap-anydpi-v26/ic_launcher.xml`:

    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
        <background android:drawable="@mipmap/ic_launcher_background" />
        <foreground android:drawable="@mipmap/ic_launcher_foreground" />
        <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
    </adaptive-icon>

Notificaciones: `ic_stat_dracpdf` como `setSmallIcon`, con `setColor(0xFFB03228)`.
El sistema recolorea la silueta: debe quedar blanca sobre transparente (ya lo está).

## Escritorio (Qt)

Windows `.ico` desde los PNG de `desktop/`:

    from PIL import Image
    Image.open("desktop/icon-256.png").save(
        "dracpdf.ico", sizes=[(16,16),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])

Linux: instalar cada `icon-<px>.png` en `share/icons/hicolor/<px>x<px>/apps/dracpdf.png`.

Iconos de toolbar: cargar `icons/svg/*.svg` y sustituir `currentColor` por el token del
tema en tiempo de ejecución (QSvgRenderer), o usar directamente `icons/qt-tinted/<tema>/`.

## Reglas

- Un solo color por icono. Nunca dos tintes en el mismo glifo.
- Tamaño de dibujo 24; se renderiza a 20 px en toolbar de escritorio y 24 dp en Android.
- Área táctil mínima 48 dp en móvil aunque el icono mida 24.
- El dragón nunca se recolorea fuera de tinta `#111318` o blanco.
- El rótulo "PDF" solo aparece en iconos de escritorio de 32 px o más.
