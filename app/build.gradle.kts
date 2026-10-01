plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}
val run = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
fun env(k: String, d: String) = "\"" + (System.getenv(k) ?: d) + "\""
android {
    namespace = "dz.iline.tashilmedical"
    compileSdk = 35
    defaultConfig {
        applicationId = "dz.iline.tashilmedical"
        minSdk = 26; targetSdk = 35
        versionCode = run
        versionName = "1.0.0-build$run"
        buildConfigField("String", "GH_OWNER", env("GH_OWNER", "Aladdinweb"))
        buildConfigField("String", "GH_REPO", env("GH_REPO", "TASHIL-MEDICAL"))
        buildConfigField("String", "GH_TOKEN", env("BRIDGE_TOKEN", ""))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
