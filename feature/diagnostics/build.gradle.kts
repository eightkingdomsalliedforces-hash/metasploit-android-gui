plugins {
    id("mago.android.library")
    id("mago.android.compose")
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:model"))
    implementation(libs.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
}