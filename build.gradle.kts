plugins {
    // Hindari plugin di-load berulang di tiap subproject
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false

    // Kotlin multiplatform → dipakai di module shared
    alias(libs.plugins.kotlinMultiplatform) apply false

    // Google Services (opsional, aktifkan hanya kalau ada google-services.json)
    alias(libs.plugins.googleServices) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.mapsSecrets) apply false
}
