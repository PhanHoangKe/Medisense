import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "vn.medisense.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.medisense.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Đọc API key từ local.properties
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { inputStream ->
                localProperties.load(inputStream)
            }
        }
        
        // Inject API key vào BuildConfig
        buildConfigField("String", "GEMINI_API_KEY", 
            "\"${localProperties.getProperty("GEMINI_API_KEY", "")}\"")
        buildConfigField("String", "PLACES_API_KEY",
            "\"${localProperties.getProperty("PLACES_API_KEY", "")}\"")
        buildConfigField("String", "OPENROUTER_KEY",
            "\"${localProperties.getProperty("OPENROUTER_KEY", "")}\"")


        val mapsApiKey = localProperties.getProperty(
            "MAPS_API_KEY",
            localProperties.getProperty("PLACES_API_KEY", "")
        )
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
    buildFeatures {
        viewBinding = true
        dataBinding = true
        buildConfig = true  // Bật BuildConfig để sử dụng buildConfigField
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.vision.interfaces)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)

    implementation(libs.recyclerview)
    implementation(libs.mpandroidchart)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    // firebase-auth được quản lý qua BOM 32.2.0 → tự resolve về 22.1.1 (không có reCAPTCHA Enterprise)
    implementation("com.google.firebase:firebase-auth")
    // Firebase Storage để tải lên hình ảnh
    implementation("com.google.firebase:firebase-storage-ktx")

    // ZXing
    implementation(libs.zxing.android.embedded)

    // WorkManager
    implementation(libs.work.runtime)
    
    // Tải hình ảnh (Glide)
    implementation("com.github.bumptech.glide:glide:4.15.1")
    annotationProcessor("com.github.bumptech.glide:compiler:4.15.1")
    
    // Google Maps & Places
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("com.google.android.libraries.places:places:3.3.0")
    
    // Biometric Authentication
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Lottie animation
    implementation("com.airbnb.android:lottie:6.4.0")

    // Guava cho ListenableFuture (CameraX)
    implementation("com.google.guava:guava:33.0.0-android")

    // OpenCV for Android
    implementation(libs.opencv.android)

    // Lifecycle ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.6")
}