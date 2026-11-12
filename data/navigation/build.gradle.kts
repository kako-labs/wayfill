plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.fuelpilot.data.navigation"; compileSdk = 35; defaultConfig { minSdk = 28 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; kotlinOptions { jvmTarget = "17" }; lint { abortOnError = true; warningsAsErrors = true } }
dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.pleos.navi.helper)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
