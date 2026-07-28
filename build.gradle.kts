plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

subprojects {
    apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
    apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

    extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.3.1")
        // Los recursos del handoff de diseño entran por copia y no se tocan.
        filter { exclude { it.file.path.contains("design-handoff") } }
    }

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt.yml"))
        buildUponDefaultConfig = true
    }
}

// Puerta de calidad local, el equivalente al `ruff + mypy + pytest` del escritorio.
tasks.register("verificar") {
    group = "verification"
    description = "Formato, análisis estático y tests JVM de todos los módulos."
    dependsOn(
        subprojects.map { "${it.path}:ktlintCheck" },
        subprojects.map { "${it.path}:detekt" },
        // Sólo tests JVM: los instrumentados necesitan un dispositivo y van aparte,
        // con `connectedCheck`. Esta puerta tiene que poder pasarse sin emulador.
        ":dominio:test",
        ":adaptadores:testDebugUnitTest",
        ":ui:testDebugUnitTest",
        ":app:testDebugUnitTest",
    )
}
