// ⬅️ IMPORTS HARUS DI PALING ATAS (sebelum plugins{})
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.mapsSecrets)
}

// Kunci Google Maps dikelola Secrets Gradle Plugin: dibaca dari local.properties (gitignored),
// jatuh ke local.defaults.properties (placeholder, di-commit) bila belum diisi. Plugin
// menyuntikkan `${MAPS_API_KEY}` ke manifest dan `BuildConfig.MAPS_API_KEY` ke Kotlin.
// Lihat docs/maps-api-key.md.
secrets {
    propertiesFileName = "local.properties"
    defaultPropertiesFileName = "local.defaults.properties"
    // Hanya MAPS_API_KEY yang boleh masuk BuildConfig/manifest. Tanpa ini plugin menyuntikkan
    // SETIAP kunci local.properties sebagai String, dan bentrok dengan buildConfigField Boolean
    // USE_FIRESTORE_EMULATOR di bawah.
    ignoreList.add("^(?!MAPS_API_KEY\$).*\$")
}

// Hanya untuk penjaga build release di bawah: plugin tidak menolak nilai placeholder.
// Sengaja hanya membaca local.properties (persis sumber plugin); variabel lingkungan TIDAK dibaca
// plugin, jadi menerimanya di sini akan meloloskan build release ber-placeholder.
private fun isMapsKeyUsable(): Boolean {
    val props = Properties()
    val lp = rootProject.file("local.properties")
    if (lp.exists()) FileInputStream(lp).use(props::load)
    val key = (props.getProperty("MAPS_API_KEY") ?: "").trim()
    return key.isNotEmpty() && key != "DEFAULT_API_KEY"
}

// Dev-only Firestore emulator redirect — gitignored local.properties flag, never
// committed. This loader is layer 1 of 2; layer 2 is the release buildType below,
// which hardcodes USE_FIRESTORE_EMULATOR to false regardless of this value, so a
// release build can never honor it even if a machine's local.properties has it on.
private val localDevProperties: Properties by lazy {
    Properties().apply {
        val lp = rootProject.file("local.properties")
        if (lp.exists()) FileInputStream(lp).use(::load)
    }
}
val useFirestoreEmulator: Boolean by lazy {
    (localDevProperties.getProperty("USE_FIRESTORE_EMULATOR") ?: System.getenv("USE_FIRESTORE_EMULATOR"))
        ?.toBoolean() ?: false
}
val firestoreEmulatorHost: String by lazy {
    localDevProperties.getProperty("FIRESTORE_EMULATOR_HOST")
        ?: System.getenv("FIRESTORE_EMULATOR_HOST")
        ?: "10.0.2.2" // the Android emulator's alias for the host machine's localhost
}
val authEmulatorPort: Int by lazy {
    (localDevProperties.getProperty("AUTH_EMULATOR_PORT") ?: System.getenv("AUTH_EMULATOR_PORT"))
        ?.toIntOrNull() ?: 9099
}
val firestoreEmulatorPort: Int by lazy {
    (localDevProperties.getProperty("FIRESTORE_EMULATOR_PORT") ?: System.getenv("FIRESTORE_EMULATOR_PORT"))
        ?.toIntOrNull() ?: 9098
}

fun isReleaseRequested(): Boolean =
    gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }

/**
 * Laporan metrik Compose (skippability / restartability).
 *
 * CLAUDE.md mendokumentasikan `assembleRelease -Pandroid.enableComposeCompilerReports=true`,
 * dan itu **tidak bekerja di repo ini** karena dua sebab: (1) properti `-Pandroid.*` adalah
 * mekanisme lama, sementara project ini memakai plugin `composeCompiler` yang dikonfigurasi
 * lewat blok ini; (2) `assembleRelease` gagal di tahap konfigurasi kalau MAPS_API_KEY kosong.
 * Karena itu metriknya dipasang di sini dan bisa dijalankan pada varian debug:
 *
 *   ./gradlew :composeApp:compileDebugKotlin --rerun-tasks -PcomposeMetrics=true
 *
 * Mati secara default — menyalakannya selalu memaksa task Kotlin berjalan ulang penuh.
 */
if (providers.gradleProperty("composeMetrics").orNull == "true") {
    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_metrics")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

android {
    namespace = "com.drivemate.showcase"

    compileSdk = 37

    defaultConfig {
        applicationId = "com.drivemate.showcase"
        minSdk    = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true // ✅ pastikan BuildConfig digenerate
    }

    // Pakai folder androidMain milikmu
    sourceSets.named("main") {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
        res.directories.clear()
        res.directories.add("src/androidMain/res")
        java.directories.clear()
        java.directories.add("src/androidMain/kotlin")
        kotlin.directories.clear()
        kotlin.directories.add("src/androidMain/kotlin")
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    buildTypes {
        debug {
            // Flag contoh supaya bisa gating log/fitur di kode
            buildConfigField("boolean", "LOG_SECRETS", "true")
            buildConfigField("boolean", "USE_FIRESTORE_EMULATOR", "$useFirestoreEmulator")
            buildConfigField("String", "FIRESTORE_EMULATOR_HOST", "\"$firestoreEmulatorHost\"")
            buildConfigField("int", "FIRESTORE_EMULATOR_PORT", "$firestoreEmulatorPort")
            buildConfigField("int", "AUTH_EMULATOR_PORT", "$authEmulatorPort")
            isMinifyEnabled = false
        }
        release {
            buildConfigField("boolean", "LOG_SECRETS", "false")
            // Hardcoded false — deliberately NOT interpolating useFirestoreEmulator.
            // A release build must never be able to compile a true value here,
            // regardless of what local.properties says on the machine that built it.
            buildConfigField("boolean", "USE_FIRESTORE_EMULATOR", "false")
            buildConfigField("String", "FIRESTORE_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "FIRESTORE_EMULATOR_PORT", "0")
            buildConfigField("int", "AUTH_EMULATOR_PORT", "0")
            // R8 + penyusutan resource (Issue #492). Aturan keep ada di proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    if (!isMapsKeyUsable()) {
        if (isReleaseRequested()) {
            throw GradleException(
                "MAPS_API_KEY is missing. Set MAPS_API_KEY in local.properties or as an environment variable."
            )
        } else {
            logger.lifecycle("⚠ MAPS_API_KEY belum diisi (blank/placeholder); peta akan menampilkan kartu \"tidak tersedia\" di build debug.")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}
kotlin { jvmToolchain(17) }

dependencies {
    // AGP 9.4 memaksa classpath kompilasi androidTest strictly sama dengan runtime
    // (consistent resolution). Runtime app berhenti di 1.1.0, sedangkan
    // androidx.test.ext:junit 1.3.0 meminta 1.2.0 di sisi kompilasi: tanpa lantai
    // ini resolusi gagal ("1.1.0 - from lock file"; repo tidak punya lock file).
    constraints {
        implementation("androidx.concurrent:concurrent-futures:1.2.0")
        implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    }

    implementation(project(":shared"))

    // Compose BOM + inti
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.text)
    implementation(libs.compose.foundation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.material3)
    implementation(libs.compose.tooling.preview)
    implementation(libs.androidx.runtime.saveable)
    debugImplementation(libs.compose.tooling)

    // Maps
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)
    implementation(libs.google.location)

    // Firebase Cloud Messaging (push token registration + notification receiving)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // AndroidX
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime.ktx)

    // Material Design icons (opsional)
    implementation(libs.google.material)

    // KotlinX
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)

    // Koin Android + Compose
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.androidx.material.icons.extended)

    // ❌ Hapus baris Material 2 ini jika tidak dipakai komponen legacy:
    // implementation(libs.androidx.material)

    // Unit test
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
