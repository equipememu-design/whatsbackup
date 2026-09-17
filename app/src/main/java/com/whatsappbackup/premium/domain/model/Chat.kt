package com.whatsappbackup.premium.domain.model

/**
 * Modelo de Chat que suporta classificação Inteligente:
 * - Privados: Backup infinito, nunca apaga
 * - Grupos: Seleção manual + Limpeza configurável
 */
data class Chat(
    val id: String,
    val name: String,
    val type: ChatType,
    val lastMessageTime: Long,
    val messageCount: Int,
    val backupSizeBytes: Long,
    // Configurações de backup
    val includeInBackup: Boolean = true,
    val autoCleanupEnabled: Boolean = false,
    val cleanupDaysThreshold: Int = 90, // Dias para limpeza automática
    // Estado
    val lastBackupTime: Long? = null,
    val isValidated: Boolean = false
) {
    fun isPrivate(): Boolean = type == ChatType.PRIVATE
    fun isGroup(): Boolean = type == ChatType.GROUP
    
    /**
     * Regra de negócio: Privados nunca são apagados
     */
    fun canAutoCleanup(): Boolean {
        return when (type) {
            ChatType.PRIVATE -> false // Nunca apaga privados
            ChatType.GROUP -> autoCleanupEnabled
        }
    }
    
    /**
     * Calcula idade do backup em dias
     */
    fun getBackupAgeDays(): Int {
        return if (lastBackupTime != null) {
            val daysInMillis = System.currentTimeMillis() - lastBackupTime
            (daysInMillis / (1000 * 60 * 60 * 24)).toInt()
        } else {
            Int.MAX_VALUE
        }
    }
}

enum class ChatType {
    PRIVATE,  // Conversas individuais - Backup infinito
    GROUP     // Grupos - Sujeito a limpeza configurável
}
