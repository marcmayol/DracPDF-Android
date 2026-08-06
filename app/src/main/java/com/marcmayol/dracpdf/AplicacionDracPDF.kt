package com.marcmayol.dracpdf

import android.app.Application

/**
 * La aplicación. Guarda el grafo y el registro de documentos, que tienen que
 * sobrevivir a la actividad —girar la pantalla la recrea y el documento abierto no
 * puede irse con ella—.
 */
class AplicacionDracPDF : Application() {
    lateinit var grafo: Grafo
        private set

    override fun onCreate() {
        super.onCreate()
        grafo = Grafo(this)
    }

    /**
     * El sistema anda justo de memoria. Se sueltan las páginas rasterizadas, que se
     * pueden volver a dibujar, antes de que el sistema opte por matar el proceso, que
     * no se puede deshacer.
     */
    @Deprecated("El reemplazo (onTrimMemory con niveles nuevos) no cubre API 26")
    override fun onLowMemory() {
        super.onLowMemory()
        grafo.cachePaginas.vaciar()
    }

    /**
     * Cualquier aviso de recorte vacía la caché, sin mirar el nivel.
     *
     * Los niveles intermedios —`TRIM_MEMORY_RUNNING_LOW` y compañía— están deprecados
     * desde Android 14 y el sistema ya no los manda, así que comparar contra ellos
     * significaba dejar de soltar memoria justo en los teléfonos nuevos. Y no hace
     * falta afinar: el sistema no llama aquí por capricho, y lo que se suelta son
     * páginas rasterizadas que se vuelven a dibujar en milisegundos.
     */
    override fun onTrimMemory(nivel: Int) {
        super.onTrimMemory(nivel)
        grafo.cachePaginas.vaciar()
    }
}
