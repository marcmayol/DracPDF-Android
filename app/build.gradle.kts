import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma de release: se lee de keystore.properties (en la raíz, fuera de git) o, si no
// está, de las variables de entorno DRACPDF_STORE_FILE, DRACPDF_STORE_PASSWORD,
// DRACPDF_KEY_ALIAS y DRACPDF_KEY_PASSWORD. Si no hay ninguna de las dos fuentes el
// release sale SIN FIRMAR a propósito: así cualquiera puede compilar el proyecto y un
// APK sin credenciales no se puede publicar por descuido (scripts/publicar_release.py
// aborta si no encuentra app-release.apk). El debug no depende de nada de esto.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps =
    Properties().apply {
        if (keystorePropsFile.exists()) {
            keystorePropsFile.inputStream().use { load(it) }
        }
    }

fun datoDeFirma(
    clave: String,
    variableEntorno: String,
): String? =
    keystoreProps.getProperty(clave)?.takeIf { it.isNotBlank() }
        ?: System.getenv(variableEntorno)?.takeIf { it.isNotBlank() }

val rutaKeystore = datoDeFirma("storeFile", "DRACPDF_STORE_FILE")
val hayFirmaDeRelease = rutaKeystore != null

// Properties.load lee el fichero como ISO-8859-1: si se guardó en UTF-8 CON BOM, la
// primera clave pasa a llamarse "<BOM>storeFile" y getProperty devuelve null sin
// quejarse. Ya ocurrió en otra app de la casa y el APK salió firmado en debug.
if (keystorePropsFile.exists() && !hayFirmaDeRelease) {
    logger.warn(
        "AVISO: keystore.properties existe pero no trae 'storeFile'. Si lo guardaste en " +
            "UTF-8 con BOM, Gradle no ve la primera clave: reescríbelo sin BOM. El " +
            "release saldrá SIN FIRMAR.",
    )
}

android {
    namespace = "com.marcmayol.dracpdf"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.marcmayol.dracpdf"
        minSdk = 26
        // 36 y no 35: un Android más nuevo que el objetivo de la aplicación por dos
        // versiones enseña el aviso de «esta app está hecha para una versión anterior»
        // cada vez que se abre. Pasó de verdad en el Pixel del titular, que va por
        // delante del SDK con el que se compilaba. Los APK de instrumentación tienen el
        // suyo aparte, en el `testOptions` de cada módulo.
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // arm64-v8a es cualquier móvil actual; x86_64 es el emulador, donde corren
            // los tests instrumentados, que son la mitad de la evidencia de cada fase.
            // Sin ellas el AAR de MuPDF mete cuatro juegos de bibliotecas nativas.
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    signingConfigs {
        if (hayFirmaDeRelease) {
            create("release") {
                storeFile = rootProject.file(rutaKeystore!!)
                storePassword = datoDeFirma("storePassword", "DRACPDF_STORE_PASSWORD")
                keyAlias = datoDeFirma("keyAlias", "DRACPDF_KEY_ALIAS")
                keyPassword = datoDeFirma("keyPassword", "DRACPDF_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hayFirmaDeRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":dominio"))
    implementation(project(":adaptadores"))
    implementation(project(":ui"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.compose.ui.test.manifest)
}
