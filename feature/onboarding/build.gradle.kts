plugins {
    id("mago.android.library")
    id("mago.android.compose")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":domain:installation"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material3)
}
