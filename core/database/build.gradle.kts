plugins {
    id("mago.android.library")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":domain:installation"))
    implementation(project(":domain:metasploit"))
    implementation(libs.coroutines.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}

room {
    schemaDirectory("$projectDir/schemas")
}
