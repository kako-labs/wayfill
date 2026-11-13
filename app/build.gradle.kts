import com.android.build.api.dsl.ManagedVirtualDevice
import java.util.Properties


plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
fun quotedBuildConfig(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""
// Release signing comes from local.properties (release.storeFile, release.storePassword, release.keyAlias,
// release.keyPassword), which Git ignores. Without it assembleRelease still builds, unsigned.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val releaseStore = localProperties.getProperty("release.storeFile")?.let(::file)?.takeIf { it.exists() }
android {
    namespace = "dev.fuelpilot"; compileSdk = 35
    defaultConfig { applicationId = "dev.fuelpilot"; minSdk = 28; targetSdk = 35; versionCode = 3; versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "STATION_GATEWAY_ORIGIN", quotedBuildConfig(providers.gradleProperty("stationGatewayOrigin").orElse("").get()))
    }
    if (releaseStore != null) {
        signingConfigs {
            create("release") {
                storeFile = releaseStore
                storePassword = localProperties.getProperty("release.storePassword")
                keyAlias = localProperties.getProperty("release.keyAlias")
                keyPassword = localProperties.getProperty("release.keyPassword")
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
    buildFeatures { compose = true; buildConfig = true }
    testOptions {
        animationsDisabled = true
        managedDevices { devices {
            create<ManagedVirtualDevice>("pixel2api35") {
                device = "Pixel 2"; apiLevel = 35; systemImageSource = "aosp"
            }
        } }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = true; warningsAsErrors = true; disable += setOf("OldTargetApi", "AndroidGradlePluginVersion", "GradleDependency"); lintConfig = file("lint.xml") }
}
dependencies {
    implementation(project(":core:model")); implementation(project(":data:energy")); implementation(project(":data:location")); implementation(project(":data:poi")); implementation(project(":data:navigation")); implementation(project(":feature:assist"))
    implementation(platform(libs.compose.bom)); implementation(libs.compose.ui); implementation(libs.compose.material3); implementation(libs.core.ktx); implementation(libs.activity.compose)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    implementation(libs.coroutines.android)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.pleos.tts)
}
