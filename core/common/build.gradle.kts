plugins { id("mago.android.library") }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.coroutines.core)
}
