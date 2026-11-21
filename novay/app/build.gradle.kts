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
    // Audit C-01 : versionning reel. Compteur simple, +1 a chaque
    // livraison installee par l owner (V3.1.0 = 35e livraison).
    versionCode = 35
    versionName = "3.1.0"

    // V2.9.6 : libVLC retire — l APK n embarque plus de natives lourdes ;
    // la propriete -PnovayAbi (arm32 / arm64 / les deux) reste disponible
    // pour d eventuelles futures dependances natives. Sans elle : toutes
    // les ABI (par defait, sans effet aujourd hui).
    val novayAbi = (findProperty("novayAbi") as? String)?.trim()
    if (!novayAbi.isNullOrEmpty()) {
      ndk {
        // Une seule ABI ("armeabi-v7a") ou plusieurs separees par des
        // virgules ("armeabi-v7a,arm64-v8a" = APK universel telephones,
        // construit par la CI pour les releases).
        novayAbi.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
          abiFilters.add(it)
        }
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
  // V2.9.6 (essai owner) : libVLC RETIRE — ExoPlayer devient le moteur
  // unique (streaming + fichiers telecharges .ts via constant-bitrate
  // seeking). Gain : ~93 Mo d APK. Retour arriere possible en un commit
  // (revert) si le seek approximatif ne convient pas au owner.
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

// V2.9.7 - diagnostic CI : chaque test est logge au demarrage et au verdict
// (STARTED/PASSED/FAILED) dans la console Gradle. En cas de test bloque, le
// dernier STARTED sans verdict dans le log designe le coupable (remonte en
// annotation par l'etape Diagnostic du workflow).
tasks.withType<Test>().configureEach {
  testLogging {
    events("started", "passed", "failed", "skipped")
  }
}
