plugins {
    alias(libs.plugins.android.application)
    //alias(libs.plugins.kotlin.android)
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