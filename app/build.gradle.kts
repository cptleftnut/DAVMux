plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.davmux.app"
    compileSdk = 34

    defaultConfig {
        // MUST be com.termux — bootstrap binaries have /data/data/com.termux/files/usr hardcoded
        applicationId = "com.termux"
        minSdk = 24
        targetSdk = 34
        versionCode = 2
        versionName = "2.0.0-davmux"

        manifestPlaceholders["TERMUX_PACKAGE_NAME"] = "com.termux"
        manifestPlaceholders["TERMUX_APP_NAME"]     = "DAVMux"

        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86") }

        externalNativeBuild {
            ndkBuild {
                cFlags("-std=c11", "-Wall", "-Wextra", "-Os", "-Wl,--gc-sections")
            }
        }
    }

    externalNativeBuild {
        ndkBuild { path = file("src/main/cpp/Android.mk") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }
    buildFeatures { viewBinding = true }
}

dependencies {
    implementation(project(":terminal-emulator"))
    implementation(project(":terminal-view"))
    implementation(project(":termux-shared"))
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
}


// ── Bootstrap download (same as Termux) ───────────────────────────────────────
// Downloads bootstrap-<arch>.zip files into src/main/cpp/ at build time.
// These are then embedded into libtermux-bootstrap.so via termux-bootstrap-zip.S
fun downloadBootstrap(arch: String, expectedChecksum: String, version: String) {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val localFile = file("src/main/cpp/bootstrap-${arch}.zip")

    if (localFile.exists()) {
        val checksum = localFile.readBytes().let {
            digest.update(it); java.math.BigInteger(1, digest.digest()).toString(16).padStart(64, '0')
        }
        if (checksum == expectedChecksum) return
        logger.quiet("Stale bootstrap-${arch}.zip, re-downloading…")
        localFile.delete()
    }

    val encodedVersion = version.replace("+", "%2B")
    val url = "https://github.com/termux/termux-packages/releases/download/bootstrap-${encodedVersion}/bootstrap-${arch}.zip"
    logger.quiet("Downloading $url")
    localFile.parentFile.mkdirs()
    java.net.URL(url).openStream().use { input -> localFile.outputStream().use { input.copyTo(it) } }
}

tasks.register("downloadBootstraps") {
    doLast {
        val version = "2026.02.12-r1+apt.android-7"
        downloadBootstrap("aarch64", "ea2aeba8819e517db711f8c32369e89e7c52cee73e07930ff91185e1ab93f4f3", version)
        downloadBootstrap("arm",     "a38f4d3b2f735f83be2bf54eff463e86dc32a3e2f9f861c1557c4378d249c018", version)
        downloadBootstrap("i686",    "f5bc0b025b9f3b420b5fcaeefc064f888f5f22a0d6fd7090f4aac0c33eb3555b", version)
        downloadBootstrap("x86_64",  "b7fd0f2e3a4de534be3144f9f91acc768630fc463eaf134ab2e64c545e834f7a", version)
    }
}

afterEvaluate {
    tasks.matching { it.name.startsWith("compile") && it.name.contains("JavaWithJavac") || it.name.contains("Kotlin") }.configureEach {
        dependsOn("downloadBootstraps")
    }
}
