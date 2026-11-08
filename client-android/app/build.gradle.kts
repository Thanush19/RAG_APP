
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)

    // Built-in Kotlin support (AGP 9).
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Load production signing credentials.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").inputStream().use {
        load(it)
    }
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

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    // Production signing configuration.
    signingConfigs {
        create("prodRelease") {
            storeFile = rootProject.file(
                keystoreProperties.getProperty("storeFile")
            )
            storePassword =
                keystoreProperties.getProperty("storePassword")
            keyAlias =
                keystoreProperties.getProperty("keyAlias")
            keyPassword =
                keystoreProperties.getProperty("keyPassword")
        }
    }

    flavorDimensions += "environment"

    // API base URL configuration.
    val devBaseUrl = providers.gradleProperty("rag.devBaseUrl")
        .getOrElse("http://127.0.0.1:8080/")

    val prodBaseUrl = providers.gradleProperty("rag.prodBaseUrl")
        .getOrElse("https://api.example.invalid/")

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"

            // Local development backend.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$devBaseUrl\""
            )
            buildConfigField(
                "boolean",
                "IS_DEV_BUILD",
                "true"
            )
        }

        create("prod") {
            dimension = "environment"

            // Production backend.
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"$prodBaseUrl\""
            )
            buildConfigField(
                "boolean",
                "IS_DEV_BUILD",
                "false"
            )
        }
    }

    buildTypes {
        release {
            // Apply production signing.
            signingConfig = signingConfigs.getByName("prodRelease")

            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
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
