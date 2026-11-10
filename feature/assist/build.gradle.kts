plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android { namespace = "dev.fuelpilot.feature.assist"; compileSdk = 35; defaultConfig { minSdk = 28 }; buildFeatures { compose = true }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; kotlinOptions { jvmTarget = "17" }; lint { abortOnError = true; warningsAsErrors = true } }
dependencies {
    implementation(project(":core:model")); implementation(project(":data:energy")); implementation(project(":data:location")); implementation(project(":data:poi")); implementation(project(":data:navigation"))
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material3); implementation(libs.lifecycle.viewmodel.compose); implementation(libs.coroutines.android)
    testImplementation(libs.junit); testImplementation(libs.coroutines.test)
}
