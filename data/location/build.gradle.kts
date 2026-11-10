plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android) }
android { namespace = "dev.fuelpilot.data.location"; compileSdk = 35; defaultConfig { minSdk = 28 }; compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }; kotlinOptions { jvmTarget = "17" }; lint { abortOnError = true; warningsAsErrors = true } }
dependencies { implementation(project(":core:model")); implementation(libs.coroutines.core); testImplementation(libs.junit) }
