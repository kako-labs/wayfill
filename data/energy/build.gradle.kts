plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.fuelpilot.data.energy"; compileSdk = 35; defaultConfig { minSdk = 28 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; kotlinOptions { jvmTarget = "17" }; lint { abortOnError = true; warningsAsErrors = true } }
val androidCarApi = androidComponents.sdkComponents.sdkDirectory.map {
    it.file("platforms/android-35/optional/android.car.jar")
}
dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
    // Vehicle is deliberately not used to derive range; its Fuel domain exposes percent/economy, not range.
    implementation(libs.pleos.vehicle)
    compileOnly(files(androidCarApi))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
