import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// The fixed signing key is kept base64-encoded in the repo
// (signing/orbis.keystore.b64) and decoded to a real keystore
// file here, before the signing config below uses it.
val orbisKeystore = File(rootProject.projectDir, "build/orbis.keystore").apply {
    parentFile.mkdirs()
    writeBytes(
        Base64.getDecoder().decode(
            rootProject.file("signing/orbis.keystore.b64").readText().trim()
        )
    )
}

android {
    namespace = "com.farhan.orbis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.farhan.orbis"
        minSdk = 26
        targetSdk = 35
        // GitHub Actions builds get a fresh, higher version code from the
        // workflow run number, so each new APK installs as an update.
        // Local builds keep version code 1.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(1000)) ?: 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // One fixed key for every build, so the app signature never changes
    // and new APKs install over the old app as updates. The key lives in
    // this private repo (signing/orbis.keystore.b64) by design; if Orbis
    // is ever published, switch to a secret release key stored outside
    // the repo first.
    signingConfigs {
        create("orbis") {
            storeFile = orbisKeystore
            storeType = "PKCS12"
            storePassword = "orbis"
            keyAlias = "orbis"
            keyPassword = "orbis"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("orbis")
        }
        release {
            signingConfig = signingConfigs.getByName("orbis")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
}
