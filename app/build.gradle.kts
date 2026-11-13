plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.joyous.remap"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.joyous.remap"
        minSdk = 33
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "100").toInt() + 100
        versionName = "1.4"
    }

    // Firma: si existe la clave en las variables de entorno (CI) se usa; si no, la clave de depuración de Android.
    val keystorePath = System.getenv("JOYOUS_KEYSTORE")
    val hasKey = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        if (hasKey) {
            create("joyous") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("JOYOUS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("JOYOUS_KEY_ALIAS")
                keyPassword = System.getenv("JOYOUS_KEY_PASSWORD")
            }
        }
    }

    // Dos variantes: "normal" (la app de siempre) y "testing" (funciones en pruebas, instalable a la vez).
    flavorDimensions += "channel"
    productFlavors {
        create("normal") {
            dimension = "channel"
            buildConfigField("boolean", "TESTING", "false")
        }
        create("lab") {
            dimension = "channel"
            applicationIdSuffix = ".testing"
            versionNameSuffix = "-testing"
            buildConfigField("boolean", "TESTING", "true")
        }
    }
    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName(if (hasKey) "joyous" else "debug")
        }
        release {
            signingConfig = signingConfigs.getByName(if (hasKey) "joyous" else "debug")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
