import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    //alias(libs.plugins.kotlin.android)
}

// Load keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.sa.posprinter"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.sa.webview"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

    }

    buildFeatures {
        viewBinding = true
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.coroutines.android)
    implementation(libs.coil)
    //implementation(libs.escpos.coffee)
    implementation(libs.escpos)
    // Add Printooth library
    //implementation("com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0")
    //implementation("com.github.mazenrashed:Printooth:1.3.1")

    // Add Kotlin coroutines for async operations
    //implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")


}