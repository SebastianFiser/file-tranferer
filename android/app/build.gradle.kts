plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun signingSecret(name: String): String =
    providers.environmentVariable(name).orNull
        ?: providers.gradleProperty(name).orNull
        ?: error("Missing signing secret: $name")

android {
    namespace = "org.dpdns.sebastianfiser.filetransferer"
    compileSdk = 37

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "org.dpdns.sebastianfiser.filetransferer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("sharedDebug") {
            storeFile = file(
                System.getenv("FILE_TRANSFERER_KEZSTORE")
                    ?: "${System.getProperty("user.home")}/.config/file-transferer/debug.keystore"
            )
            storePassword = signingSecret("FILE_TRANSFERER_STORE_PASSWORD")
            keyAlias = "file-transferer-debug"
            keyPassword = signingSecret("FILE_TRANSFERER_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("sharedDebug")
        }

    }

}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
