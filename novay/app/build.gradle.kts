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
    // minSdk 24 = Android 7.0.
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0"

    // V2.8 : libvlc-all embarque les 4 ABI natives (~40 Mo chacune).
    // La CI construit un APK par architecture :
    //   ./gradlew :app:assembleRelease -PnovayAbi=armeabi-v7a
    //   ./gradlew :app:assembleRelease -PnovayAbi=arm64-v8a
    // Le filtre au niveau du MODULE app evince aussi les natives des
    // dependances transitives. Sans la propriete (tests, debug local) :
    // toutes les ABI.
    val novayAbi = (findProperty("novayAbi") as? String)?.trim()
    if (!novayAbi.isNullOrEmpty()) {
      ndk {
        abiFilters.add(novayAbi)
      }
    }
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
  sourceSets {
    // AGP 9 (built-in Kotlin) : les .kt des srcDirs "java" compilent en
    // Kotlin (comme src/main/java du panel) — on declare explicitement le
    // dossier test/kotlin pour ne pas dependre de la convention.
    getByName("test").java.srcDir("src/test/kotlin")
    // Tests unitaires Robolectric : manifest dedie (activite lanzable
    // pour les regles Compose — les manifests des libs de test ne sont
    // pas fusionnes dans la config unitaire).
    getByName("test").manifest.srcFile("src/test/AndroidManifest.xml")
  }
  testOptions {
    // V2.6 (owner) : tests UI Robolectric (cadrage video plein ecran).
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = false
    }
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
  // V2.6 (owner) : systeme d icones maison NovayIcons - icons-extended
  // (plusieurs Mo) n est plus reference nulle part.
  // implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.okhttp)
  // V2.1 : affiches reelles du catalogue (Coil + OkHttp existant).
  implementation(libs.coil.compose)
  implementation(libs.haze)
  implementation(libs.lottie.compose)
  implementation(libs.media3.exoplayer)
  implementation(libs.media3.exoplayer.hls)
  implementation(libs.media3.ui)
  // V2.8 : moitie "fichiers telecharges" du lecteur hybride. Le streaming
  // en ligne reste ExoPlayer (libVLC 3 n'envoie pas d'en-tetes HTTP
  // arbitraires -> pas de Referer possible).
  implementation(libs.libvlc.all)
  testImplementation(libs.junit)
  // V2.6 (owner) : test UI Robolectric du cadrage video plein ecran.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.compose.ui.test.junit4)
  testImplementation(libs.compose.ui.test.manifest)
  // org.json reel pour les tests JVM (celui d'android.jar est un stub).
  testImplementation(libs.org.json)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
