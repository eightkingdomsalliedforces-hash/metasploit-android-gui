package dev.mago.android.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installation_state")
data class InstallationStateEntity(
    @PrimaryKey val singletonId: Int = 1,
    val stage: String,
    val progress: Int,
    val operationId: String?,
    val lastSuccessfulStage: String?,
    val retryCount: Int,
    val failureKind: String?,
    val errorCode: String?,
    val errorUserMessage: String?,
    val errorTechnicalMessage: String?,
    val errorRetryable: Boolean,
    val updatedAtEpochMillis: Long,
)
