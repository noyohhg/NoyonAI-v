plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.noyon.myraai"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.noyon.myraai"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
}
dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
