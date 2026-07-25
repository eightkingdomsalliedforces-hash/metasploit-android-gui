plugins { id("mago.android.library") }

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
}
