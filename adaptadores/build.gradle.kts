plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.marcmayol.dracpdf.adaptadores"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        // Los formularios oficiales que baja scripts/descargar_formularios_oficiales.py
        // viajan al dispositivo como assets del APK de test. La carpeta no está
        // versionada y puede no existir: si falta, el APK se monta igual y los tests
        // que la necesitan se saltan solos diciendo cómo conseguirla.
        getByName("androidTest").assets.srcDir(rootProject.file("fixtures-externos"))
    }
}

dependencies {
    api(project(":dominio"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)

    // El motor. Lleva las bibliotecas nativas de MuPDF; las ABIs que acaban en el
    // APK se filtran en :app.
    implementation(libs.mupdf.fitz)

    // Quien firma. Va aquí y no en :dominio porque es un detalle de infraestructura:
    // el dominio sólo conoce el puerto SignatureService.
    //
    // Bouncy Castle **no** se declara aparte a propósito, aunque la firma lo use de
    // arriba abajo: PDFBox-Android arrastra su propia pareja (bcprov + bcpkix,
    // variante jdk15to18) y añadir la variante jdk18on mete dos Bouncy Castle en el
    // mismo APK, con las mismas clases en los dos. El que viene con PDFBox es además
    // el que él tiene probado.
    implementation(libs.pdfbox.android)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
