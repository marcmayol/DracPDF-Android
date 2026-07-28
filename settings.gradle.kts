pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // MuPDF no está en Maven Central: Artifex lo publica en su propio repositorio.
        // Se acota a su grupo para que ninguna otra dependencia pueda resolverse aquí.
        maven("https://maven.ghostscript.com") {
            content { includeGroup("com.artifex.mupdf") }
        }
    }
}

rootProject.name = "DracPDF-Android"

// El dominio es un módulo JVM puro a propósito: al no tener el SDK en el classpath,
// no puede importar Android ni por descuido. La regla del plan deja de ser un acuerdo
// y pasa a ser algo que el compilador impide romper.
include(":dominio")

// Implementaciones concretas de los puertos: MuPDF para el documento, Storage Access
// Framework para los ficheros, DataStore para recientes y preferencias.
include(":adaptadores")

// Interfaz Compose y tema Ladón.
include(":ui")

// Manifiesto, intents y ensamblado.
include(":app")
