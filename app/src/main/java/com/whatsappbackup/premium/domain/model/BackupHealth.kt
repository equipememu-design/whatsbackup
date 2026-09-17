package com.whatsappbackup.premium.domain.model

/**
 * Status do Dashboard de Saúde (Semáforo)
 */
enum class HealthStatus {
    GREEN,  // Tudo sincronizado
    YELLOW, // Alerta de permissão ou espaço
    RED     // Falha crítica no último agendamento
}

/**
 * Modelo para o Dashboard de Saúde
 */
data class BackupHealth(
    val status: HealthStatus,
    val lastBackupTime: Long?,
    val nextScheduledBackup: Long?,
    val totalBackupsCount: Int,
    val storageUsedBytes: Long,
    val storageAvailableBytes: Long,
    val failedBackupsCount: Int,
    val pendingPermissions: List<String>,
    val validatedBackupsCount: Int,
    // Radar Preditivo
    val dailyGrowthRateBytes: Long,
    val estimatedDaysUntilFull: Int,
    // Mensagem descritiva
    val statusMessage: String
) {
    companion object {
        fun createEmpty(): BackupHealth {
            return BackupHealth(
                status = HealthStatus.YELLOW,
                lastBackupTime = null,
                nextScheduledBackup = null,
                totalBackupsCount = 0,
                storageUsedBytes = 0L,
                storageAvailableBytes = 0L,
                failedBackupsCount = 0,
                pendingPermissions = emptyList(),
                validatedBackupsCount = 0,
                dailyGrowthRateBytes = 0L,
                estimatedDaysUntilFull = -1,
                statusMessage = "Configuração inicial necessária"
            )
        }
    }
}

/**
 * Resultado da validação de integridade (Modo Pânico)
 */
data class ValidationResult(
    val isValid: Boolean,
    val checksumVerified: Boolean,
    val structureValid: Boolean,
    val restoredFilesCount: Int,
    val corruptedFiles: List<String>,
    val validationTimestamp: Long,
    val panicModeAvailable: Boolean
) {
    val sealText: String
        get() = if (isValid) "Backup Validado ✓" else "Falha na Validação"
}
