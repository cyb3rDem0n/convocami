plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "it.cyb3rdm0n.convocami"
    compileSdk = 35

    defaultConfig {
        applicationId = "it.cyb3rdm0n.convocami"
        minSdk = 26          // Android 8.0: copre pressoche tutti i telefoni in uso
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // Le chiavi NON stanno nel repo: si leggono da local.properties, che
        // e ignorato da git. Vedi docs/build-e-deploy.md.
        val props = java.util.Properties().apply {
            val f = rootProject.file("local.properties")
            if (f.exists()) f.inputStream().use { load(it) }
        }
        buildConfigField("String", "SUPABASE_URL",
            "\"${props.getProperty("supabase.url") ?: System.getenv("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY",
            "\"${props.getProperty("supabase.anonKey") ?: System.getenv("SUPABASE_ANON_KEY") ?: ""}\"")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // La firma si configura in un file non versionato: vedi la guida.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:teamdraw"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    // Firebase Cloud Messaging entra in fase 2, insieme a google-services.json
    // implementation(platform(libs.firebase.bom))
    // implementation(libs.firebase.messaging)

    implementation(libs.coil.compose)
}
