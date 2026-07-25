package dev.mago.android.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.mago.android.common.AppResult
import dev.mago.android.model.AppError
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun saveRpcPassword(value: CharArray): AppResult<Unit> {
        val plaintext = ByteArray(value.size)
        return try {
            value.forEachIndexed { index, char ->
                if (char.code !in 0x21..0x7e) {
                    return failure("RPC_PASSWORD_INVALID", "RPC 密碼格式不正確")
                }
                plaintext[index] = char.code.toByte()
            }
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val record = RpcSecretRecord(cipher.iv, cipher.doFinal(plaintext))
            if (preferences.edit().putString(PASSWORD_KEY, RpcSecretRecordCodec.encode(record)).commit()) {
                AppResult.Success(Unit)
            } else {
                failure("RPC_PASSWORD_SAVE_FAILED", "無法儲存 RPC 密碼", retryable = true)
            }
        } catch (error: Exception) {
            failure(
                code = "RPC_PASSWORD_ENCRYPTION_FAILED",
                message = "無法加密 RPC 密碼",
                technical = error.message,
                retryable = true,
            )
        } finally {
            plaintext.fill(0)
            value.fill('\u0000')
        }
    }

    override suspend fun readRpcPassword(): AppResult<CharArray?> {
        val encoded = preferences.getString(PASSWORD_KEY, null) ?: return AppResult.Success(null)
        val record = RpcSecretRecordCodec.decode(encoded)
            ?: return failure("RPC_PASSWORD_RECORD_INVALID", "RPC 密碼資料已損壞")
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, record.iv))
            val plaintext = cipher.doFinal(record.ciphertext)
            try {
                AppResult.Success(CharArray(plaintext.size) { index -> (plaintext[index].toInt() and 0xff).toChar() })
            } finally {
                plaintext.fill(0)
            }
        } catch (error: Exception) {
            failure(
                code = "RPC_PASSWORD_DECRYPTION_FAILED",
                message = "無法解密 RPC 密碼",
                technical = error.message,
                retryable = false,
            )
        }
    }

    override suspend fun clearRpcPassword(): AppResult<Unit> = if (
        preferences.edit().remove(PASSWORD_KEY).commit()
    ) {
        AppResult.Success(Unit)
    } else {
        failure("RPC_PASSWORD_CLEAR_FAILED", "無法清除 RPC 密碼", retryable = true)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
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

    private fun failure(
        code: String,
        message: String,
        technical: String? = null,
        retryable: Boolean = false,
    ): AppResult.Failure = AppResult.Failure(
        AppError(
            errorCode = code,
            userMessage = message,
            technicalMessage = technical,
            retryable = retryable,
        ),
    )

    private companion object {
        const val PREFERENCES = "mago_secure_credentials"
        const val PASSWORD_KEY = "rpc_password"
        const val KEY_ALIAS = "mago_rpc_password_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
