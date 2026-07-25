plugins {
    id("mago.android.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.serialization.json)
}
