plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mustafa.agriportal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mustafa.agriportal"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")
}
