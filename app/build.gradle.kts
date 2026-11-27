plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.orion.voicelab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aistudio.orionvoicelab.kxrqmw"
        minSdk = 26
        targetSdk = 36
        versionCode = 13
        versionName = "0.8.1"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    val localDebugKeystore = file("${rootDir}/debug.keystore")
    val releaseStoreFilePath = providers.gradleProperty("ORION_RELEASE_STORE_FILE").orNull
        ?: System.getenv("ORION_RELEASE_STORE_FILE")
    val releaseStorePassword = providers.gradleProperty("ORION_RELEASE_STORE_PASSWORD").orNull
        ?: System.getenv("ORION_RELEASE_STORE_PASSWORD")
    val releaseKeyAlias = providers.gradleProperty("ORION_RELEASE_KEY_ALIAS").orNull
        ?: System.getenv("ORION_RELEASE_KEY_ALIAS")
    val releaseKeyPassword = providers.gradleProperty("ORION_RELEASE_KEY_PASSWORD").orNull
        ?: System.getenv("ORION_RELEASE_KEY_PASSWORD")

    val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
        file(releaseStoreFilePath).isFile &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank() &&
        !releaseKeyPassword.isNullOrBlank()

    signingConfigs {
        if (localDebugKeystore.isFile) {
            getByName("debug") {
                storeFile = localDebugKeystore
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                null
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sherpa.onnx)
    implementation(libs.commons.compress)
    implementation(libs.llamacpp.kotlin)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
