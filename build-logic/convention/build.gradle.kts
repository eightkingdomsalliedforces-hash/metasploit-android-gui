plugins {
    `kotlin-dsl`
}

group = "dev.mago.android.buildlogic"

dependencies {
    implementation("com.android.tools.build:gradle:9.3.0")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "mago.android.application"
            implementationClass = "MagoAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "mago.android.library"
            implementationClass = "MagoAndroidLibraryPlugin"
        }
        register("androidCompose") {
            id = "mago.android.compose"
            implementationClass = "MagoAndroidComposePlugin"
        }
    }
}
