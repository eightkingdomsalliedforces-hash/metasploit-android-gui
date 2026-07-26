import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class MagoAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        val configuredVersionName = providers.gradleProperty("mago.versionName")
            .orElse(DEFAULT_VERSION_NAME)
            .get()
        require(VERSION_NAME_PATTERN.matches(configuredVersionName)) {
            "mago.versionName must use semantic version form such as 0.7.0 or 0.7.0-rc.1"
        }

        val configuredVersionCodeText = providers.gradleProperty("mago.versionCode")
            .orElse(DEFAULT_VERSION_CODE.toString())
            .get()
        val configuredVersionCode = configuredVersionCodeText.toIntOrNull()
        require(configuredVersionCode != null && configuredVersionCode > 0) {
            "mago.versionCode must be a positive 32-bit integer"
        }

        extensions.configure<ApplicationExtension> {
            namespace = "dev.mago.android"
            compileSdk = 36

            defaultConfig {
                applicationId = "dev.mago.android"
                minSdk = 31
                targetSdk = 36
                versionCode = configuredVersionCode
                versionName = configuredVersionName
            }

            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }

            buildFeatures {
                buildConfig = true
            }
        }
    }

    private companion object {
        const val DEFAULT_VERSION_NAME = "0.7.0"
        const val DEFAULT_VERSION_CODE = 7
        val VERSION_NAME_PATTERN = Regex("^[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z][0-9A-Za-z.-]*)?$")
    }
}
