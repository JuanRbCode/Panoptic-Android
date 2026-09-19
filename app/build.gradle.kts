plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.juanrbcode.panoptic"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.juanrbcode.panoptic"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)


    // Dependencia obligatoria para LifecycleService (para que el Service sea un LifecycleOwner)
    implementation("androidx.lifecycle:lifecycle-service:2.7.0")
    implementation("io.socket:socket.io-client:2.1.0")
    implementation(libs.firebase.crashlytics.buildtools)
    // Define la versión de CameraX (puedes usar una estable reciente)

    // Core y Lifecycle necesarios para CameraX
    val camerax_version = "1.4.1" // o superior

    implementation("androidx.camera:camera-core:${camerax_version}")
    implementation("androidx.camera:camera-camera2:${camerax_version}")
    implementation("androidx.camera:camera-lifecycle:${camerax_version}")

    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    // Opcionales muy recomendados (necesarios para ImageCapture e ImageAnalysis) //
    implementation("androidx.camera:camera-view:${camerax_version}")
    implementation("androidx.camera:camera-video:${camerax_version}")

    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}