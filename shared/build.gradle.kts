// ===============================================
// :shared (KMM) — Gradle Kotlin DSL
// ===============================================
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    androidLibrary {
        namespace  = "com.drivemate.showcase.shared"
        compileSdk = 37
        minSdk     = 26
        // Enable a host-local JVM test surface so commonTest assertions actually execute.
        withHostTestBuilder {}
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    // ===== Source sets =====
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines)
                implementation(libs.kotlinx.serialization)
                implementation(libs.koin.core)
                implementation(libs.kotlinx.datetime)   // <- WAJIB utk Clock.System.now()
            }
        }
        val commonTest by getting {
            dependencies { implementation(libs.kotlin.test) }
        }
        val androidMain by getting {
            dependencies {
                // Firebase via BoM
                implementation(project.dependencies.platform(libs.firebase.bom))
                implementation(libs.firebase.firestore)
                implementation(libs.firebase.auth)
                implementation(libs.firebase.common)

                implementation(libs.androidx.datastore.preferences)
                // <-- WAJIB untuk .await() di Task Firebase
                implementation(libs.kotlinx.coroutines.play.services)
                implementation(libs.koin.android)
            }
        }

        // iOS main/test otomatis
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    // Toolchain Kotlin/JVM (untuk Android target)
    jvmToolchain(17)
}
