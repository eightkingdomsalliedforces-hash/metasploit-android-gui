package dev.mago.android.termux

import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import java.security.MessageDigest
import java.util.Base64

data class TermuxCommand(
    val executable: String,
    val arguments: Array<String>,
    val workingDirectory: String,
)

class TermuxBootstrapCommandFactory(
    private val expectedSha256: String,
) {
    fun create(bundle: ByteArray): AppResult<TermuxCommand> {
        val actual = MessageDigest.getInstance("SHA-256")
            .digest(bundle)
            .joinToString("") { "%02x".format(it) }
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            return AppResult.Failure(
                AppError(
                    errorCode = "BRIDGE_BUNDLE_DIGEST_MISMATCH",
                    userMessage = "Bridge 安裝包驗證失敗",
                    technicalMessage = "Expected=$expectedSha256 actual=$actual",
                    retryable = false,
                ),
            )
        }

        val encoded = Base64.getEncoder().encodeToString(bundle)
        return AppResult.Success(
            TermuxCommand(
                executable = TermuxRunCommandContract.BASH,
                arguments = arrayOf(
                    "-c",
                    BOOTSTRAP_SCRIPT,
                    "mago-bootstrap",
                    encoded,
                ),
                workingDirectory = TermuxRunCommandContract.HOME,
            ),
        )
    }

    private companion object {
        val BOOTSTRAP_SCRIPT = """
            set -euo pipefail
            umask 077
            bootstrap_dir=\"${'$'}HOME/.mago/bootstrap\"
            bridge_dir=\"${'$'}HOME/.mago/bridge-v1\"
            archive=\"${'$'}bootstrap_dir/mago_bridge_v1.tgz\"
            mkdir -p \"${'$'}bootstrap_dir\"
            rm -rf \"${'$'}bridge_dir\"
            mkdir -p \"${'$'}bridge_dir\"
            printf '%s' \"${'$'}1\" | base64 -d > \"${'$'}archive\"
            tar -xzf \"${'$'}archive\" -C \"${'$'}bridge_dir\" --strip-components=1
            find \"${'$'}bridge_dir\" -type f -name '*.sh' -exec chmod 700 {} +
        """.trimIndent()
    }
}
