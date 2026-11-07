plugins {
    alias(libs.plugins.android.application)
    // Built-in Kotlin support (AGP 9): org.jetbrains.kotlin.android is NOT applied.
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.ragclient.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ragclient.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "environment"

    // Base URL is a build-time knob, not a source edit: pass
    // -Prag.prodBaseUrl=https://api.yourhost/ (or set it in gradle.properties).
    val devBaseUrl = providers.gradleProperty("rag.devBaseUrl")
        .getOrElse("http://127.0.0.1:8080/")
    val prodBaseUrl = providers.gradleProperty("rag.prodBaseUrl")
        // RFC-2606 .invalid never resolves, so an unconfigured prod build
        // fails with the client's own "can't reach the server" copy instead of
        // silently talking to somebody else's host.
        .getOrElse("https://api.example.invalid/")

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            // 127.0.0.1 works on an emulator AND on a USB device, but only once
            // the tunnel exists: adb reverse tcp:8080 tcp:8080 (re-run after
            // replugging). For a phone on the same Wi-Fi, pass
            // -Prag.devBaseUrl=http://<pc-lan-ip>:8080/.
            buildConfigField("String", "API_BASE_URL", "\"$devBaseUrl\"")
            buildConfigField("boolean", "IS_DEV_BUILD", "true")
        }
        create("prod") {
            dimension = "environment"
            // Cleartext is a dev-only capability (src/dev/AndroidManifest.xml),
            // so prod must be given an https URL.
            buildConfigField("String", "API_BASE_URL", "\"$prodBaseUrl\"")
            buildConfigField("boolean", "IS_DEV_BUILD", "false")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        jvmToolchain(17)
        compilerOptions {
            freeCompilerArgs.add("-Xskip-prerelease-check")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.icons.feather)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
