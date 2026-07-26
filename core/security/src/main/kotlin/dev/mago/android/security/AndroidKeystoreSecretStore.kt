package dev.mago.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import dev.mago.android.model.SuggestedAction
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretStore(
    context: Context,
    private val recordCodec: RpcSecretRecordCodec = RpcSecretRecordCodec(),
) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun saveRpcPassword(value: CharArray): AppResult<Unit> {
        var plaintext: ByteArray? = null
        return try {
            if (value.isEmpty()) return secretFailure("RPC_SECRET_EMPTY", "RPC 密碼不可為空", false)
            val encodedPassword = encodeChars(value)
            plaintext = encodedPassword
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(encodedPassword)
            val record = recordCodec.encode(cipher.iv, encrypted)
            if (!preferences.edit().putString(RPC_PASSWORD, record).commit()) {
                secretFailure("RPC_SECRET_SAVE_FAILED", "無法儲存 RPC 密碼", true)
            } else {
                AppResult.Success(Unit)
            }
        } catch (error: Exception) {
            secretFailure("RPC_SECRET_SAVE_FAILED", "無法安全儲存 RPC 密碼", true, error)
        } finally {
            plaintext?.fill(0)
            value.fill('\u0000')
        }
    }

    override suspend fun readRpcPassword(): AppResult<CharArray?> {
        val encoded = preferences.getString(RPC_PASSWORD, null) ?: return AppResult.Success(null)
        val record = recordCodec.decode(encoded)
            ?: return secretFailure("RPC_SECRET_INVALID", "RPC 密碼資料已損壞", false)
        var plaintext: ByteArray? = null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, record.iv))
            val decrypted = cipher.doFinal(record.ciphertext)
            plaintext = decrypted
            AppResult.Success(decodeChars(decrypted))
        } catch (error: Exception) {
            secretFailure("RPC_SECRET_READ_FAILED", "無法讀取 RPC 密碼", true, error)
        } finally {
            plaintext?.fill(0)
            record.iv.fill(0)
            record.ciphertext.fill(0)
        }
    }

    override suspend fun clearRpcPassword(): AppResult<Unit> = try {
        if (preferences.edit().remove(RPC_PASSWORD).commit()) AppResult.Success(Unit)
        else secretFailure("RPC_SECRET_CLEAR_FAILED", "無法清除 RPC 密碼", true)
    } catch (error: RuntimeException) {
        secretFailure("RPC_SECRET_CLEAR_FAILED", "無法清除 RPC 密碼", true, error)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encodeChars(value: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value))
        val result = ByteArray(buffer.remaining())
        buffer.get(result)
        return result
    }

    private fun decodeChars(value: ByteArray): CharArray {
        val buffer = StandardCharsets.UTF_8.decode(java.nio.ByteBuffer.wrap(value))
        val result = CharArray(buffer.remaining())
        buffer.get(result)
        return result
    }

    private fun secretFailure(
        code: String,
        message: String,
        retryable: Boolean,
        cause: Throwable? = null,
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = code,
            userMessage = message,
            technicalMessage = cause?.message,
            suggestedAction = if (retryable) SuggestedAction.RETRY else SuggestedAction.VIEW_TECHNICAL_DETAILS,
            retryable = retryable,
        ),
    )

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "mago_rpc_secret_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES = "mago_secrets"
        const val RPC_PASSWORD = "rpc_password"
    }
}
