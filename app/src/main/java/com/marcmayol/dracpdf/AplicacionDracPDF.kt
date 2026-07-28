package com.marcmayol.dracpdf

import android.app.Application
import android.content.ComponentCallbacks2

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

    override fun onTrimMemory(nivel: Int) {
        super.onTrimMemory(nivel)
        if (nivel >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            grafo.cachePaginas.vaciar()
        }
    }
}
