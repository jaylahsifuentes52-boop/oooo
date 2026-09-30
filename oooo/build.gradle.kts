plugins {
    id("com.android.application")
}

android {
    namespace = "com.juniormods.juniorpatcher"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.juniormods.juniorpatcher"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "2.0.1"
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }

        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-ktx:1.11.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.14.0")

    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
