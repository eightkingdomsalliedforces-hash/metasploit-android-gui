plugins {
    id("mago.android.library")
    id("mago.android.compose")
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":domain:installation"))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
