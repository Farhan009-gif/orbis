import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Signing material is NEVER stored in this repo. The workflow decodes
// the ORBIS_KEYSTORE_B64 secret into a temp keystore outside the repo
// tree and passes its path + passwords in through the environment.
// No env present -> the build fails loudly below instead of signing
// with anything else (a silent fallback is how an app loses its key).
val orbisKeystorePath = System.getenv("ORBIS_KEYSTORE_PATH")
val orbisStorePassword = System.getenv("ORBIS_STORE_PASSWORD")
val orbisKeyAlias = System.getenv("ORBIS_KEY_ALIAS")
val orbisKeyPassword = System.getenv("ORBIS_KEY_PASSWORD")
if (orbisKeystorePath.isNullOrBlank() || !File(orbisKeystorePath).isFile ||
    orbisStorePassword.isNullOrBlank() || orbisKeyAlias.isNullOrBlank() ||
    orbisKeyPassword.isNullOrBlank()
) {
    throw GradleException(
        "Orbis signing env missing: ORBIS_KEYSTORE_PATH / ORBIS_STORE_PASSWORD / " +
            "ORBIS_KEY_ALIAS / ORBIS_KEY_PASSWORD must be set (from repo secrets in CI). " +
            "Refusing to build unsigned or with any fallback key."
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
    // and new APKs install over the old app as updates. The key itself
    // lives only in GitHub Secrets (ORBIS_*) plus the owner's local
    // record — never in this repository.
    signingConfigs {
        create("orbis") {
            storeFile = File(orbisKeystorePath)
            storeType = "PKCS12"
            storePassword = orbisStorePassword
            keyAlias = orbisKeyAlias
            keyPassword = orbisKeyPassword
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
