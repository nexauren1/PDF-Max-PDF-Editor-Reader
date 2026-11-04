plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.nexauren.pdfmax"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.nexauren.pdfmax"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.2.6"
    }
    // Android App Bundles are automatically split by device ABI by Google Play.
    buildFeatures { compose = true }
    val signingStorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val signingStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val signingKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val signingKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    val hasReleaseCredentials = !signingStorePath.isNullOrBlank() && file(signingStorePath).isFile &&
        !signingStorePassword.isNullOrBlank() &&
        !signingKeyAlias.isNullOrBlank() &&
        !signingKeyPassword.isNullOrBlank()
    signingConfigs {
        create("upload") {
            if (hasReleaseCredentials) {
                storeFile = file(signingStorePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        getByName("release") {
            // Production release builds are minified and resource-shrunk to reduce download size.
            // Never fall back to a debug key for a Release build. Missing upload credentials must fail the release task.
            signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.2")
    implementation("com.google.mlkit:translate:17.0.3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
