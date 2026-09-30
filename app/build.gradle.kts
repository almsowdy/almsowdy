plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.miro.education.admin"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.miro.education.admin"
        minSdk = 26
        targetSdk = 35
        versionCode = 400
        versionName = "4.0.0"
        buildConfigField("String","SUPABASE_URL","\"https://pdjvopmlndgnbjpqvvzr.supabase.co\"")
        buildConfigField("String","SUPABASE_PUBLISHABLE_KEY","\"sb_publishable_wojwte05R2jYX0nNAhMr2w_1_2DMZWb\"")
        buildConfigField("String","OWNER_EMAIL","\"aa034777@gmail.com\"")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    kotlin { jvmToolchain(17) }
    buildTypes {
        debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
        release { isMinifyEnabled = false }
    }
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)
}