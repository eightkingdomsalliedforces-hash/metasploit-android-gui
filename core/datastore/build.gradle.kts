plugins {
    id("mago.android.library")
    alias(libs.plugins.protobuf)
}

dependencies {
    implementation(libs.datastore)
    implementation(libs.protobuf.javalite)
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:4.35.1" }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("java") { option("lite") }
            }
        }
    }
}
