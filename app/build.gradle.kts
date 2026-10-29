plugins {
    alias(libs.plugins.android.application)
}

val firebaseConfigurado = file("google-services.json").exists()
if (firebaseConfigurado) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.example.udb_academic"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.udb_academic"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("boolean", "FIREBASE_CONFIGURADO", firebaseConfigurado.toString())

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
