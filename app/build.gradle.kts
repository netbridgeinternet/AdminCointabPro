import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

// Load keystore properties from keystore.properties file
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.urbancointabpro.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.urbancointabpro.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            merges += "META-INF/INDEX.LIST"
            merges += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Force gRPC version to match Firestore 25.x requirements
configurations.all {
    resolutionStrategy {
        force("io.grpc:grpc-core:1.64.2")
        force("io.grpc:grpc-android:1.64.2")
        force("io.grpc:grpc-okhttp:1.64.2")
        force("io.grpc:grpc-protobuf-lite:1.64.2")
        force("io.grpc:grpc-stub:1.64.2")
        force("io.grpc:grpc-api:1.64.2")
        force("io.grpc:grpc-protobuf:1.64.2")
        force("io.grpc:grpc-context:1.64.2")
        force("io.grpc:grpc-codegen:1.64.2")
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose + Material3
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Google Sign-In + Drive
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.1")
    implementation("com.google.apis:google-api-services-drive:v3-rev20241027-2.0.0")

    // Firebase (Firestore for pairing data)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-firestore")

    // gRPC (required by Firebase Firestore 25.x - without these, Firestore crashes)
    implementation("io.grpc:grpc-api:1.64.2")
    implementation("io.grpc:grpc-core:1.64.2")
    implementation("io.grpc:grpc-android:1.64.2")
    implementation("io.grpc:grpc-okhttp:1.64.2")
    implementation("io.grpc:grpc-protobuf-lite:1.64.2")
    implementation("io.grpc:grpc-stub:1.64.2")
    implementation("javax.annotation:javax.annotation-api:1.3.2")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // QR Code generation + scanning
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
