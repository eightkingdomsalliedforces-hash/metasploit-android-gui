plugins {
    id("mago.android.library")
    id("mago.android.compose")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(project(":domain:installation"))
    implementation(project(":domain:metasploit"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.compose.material3)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
