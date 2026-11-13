plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "dot.jcversa.novay"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "dot.jcversa.novay"
    // NoVaY : Android 7+ ET 32 bits (armeabi-v7a) exigees par le owner.
    // minSdk 24 = Android 7.0 ; l'APK "fat" (pas de splits ABI) embarque
    // toutes les architectures dans un seul fichier.
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"
  }

  signingConfigs {
    create("release") {
      // V0 : signe avec la cle debug pour installation directe (owner +
      // proches, pas de Play Store). Le keystore n'est PAS commité ;
      // la CI le cree et le met en cache -> signature stable entre les
      // builds, les APK s'installent l'un par-dessus l'autre.
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      signingConfig = signingConfigs.getByName("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
  }
  lint {
    // media3 marque une grande partie de son API @UnstableApi (stable en
    // pratique, utilisé en production partout) — les usages sont couverts
    // par @androidx.annotation.OptIn dans PlayerScreen.
    disable += "UnsafeOptInUsageError"
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.ui)
  testImplementation(libs.junit)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
