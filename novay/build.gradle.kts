// NoVaY — build racine. Versions = parité exacte avec l'appli panel
// android/ (AGP 9.1.1 + Kotlin 2.2.10, CI verte depuis 9.18).
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
}
