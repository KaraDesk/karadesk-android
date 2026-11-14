plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.karadesk.sample"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.karadesk.sample"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "KARADESK_SPACE_ID", "\"${providers.gradleProperty("karadesk.spaceId").getOrElse("your-space-id")}\"")
        buildConfigField("String", "KARADESK_APP_ID", "\"${providers.gradleProperty("karadesk.appId").getOrElse("your-android-app-id")}\"")
        manifestPlaceholders["karadeskApiBaseUrl"] = providers.gradleProperty("karadesk.apiBaseUrl").getOrElse("")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":karadesk"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
