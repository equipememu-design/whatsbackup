package com.whatsappbackup.premium.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import com.whatsappbackup.premium.domain.model.ChatType
import com.whatsappbackup.premium.domain.model.ExtractionPriority

/**
 * Entidade Room para armazenar checkpoints de extração do Modo Marathon
 * 
 * Permite retomar exatamente de onde parou em caso de:
 * - Processo morto pelo sistema
 * - Bateria acabar
 * - App fechar inesperadamente
 */
@Entity(
    tableName = "extraction_checkpoints",
    indices = [
        Index(value = ["chatId"], unique = true),
        Index(value = ["chatType"]),
        Index(value = ["priority"]),
        Index(value = ["isComplete"])
    ]
)
data class CheckpointEntity(
    @PrimaryKey
    val chatId: String,
    val chatName: String,
    val chatType: ChatType,
    
    // Estado da extração
    val scrollPosition: Int = 0,
    val messagesExtracted: Long = 0L,
    val lastTimestamp: Long? = null,
    val monthsBack: Int = 0,
    
    // Metadados
    val isComplete: Boolean = false,
    val isPartial: Boolean = false,
    val priority: ExtractionPriority = ExtractionPriority.MEDIUM,
    
    // Controle de execução
    val lastExecutionTime: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    
    // Dados adicionais para fila priorizada
    val estimatedTotalMessages: Long? = null,
    val chatSizeBytes: Long = 0L
) {
    /**
     * Converte para modelo de domínio
     */
    fun toDomain(): com.whatsappbackup.premium.domain.model.ExtractionCheckpoint {
        return com.whatsappbackup.premium.domain.model.ExtractionCheckpoint(
            chatId = chatId,
            chatName = chatName,
            chatType = chatType,
            scrollPosition = scrollPosition,
            messagesExtracted = messagesExtracted,
            lastTimestamp = lastTimestamp,
            monthsBack = monthsBack,
            isComplete = isComplete,
            isPartial = isPartial,
            priority = priority,
            lastExecutionTime = lastExecutionTime,
            retryCount = retryCount,
            errorMessage = errorMessage
        )
    }
    
    companion object {
        /**
         * Cria entidade a partir de modelo de domínio
         */
        fun fromDomain(checkpoint: com.whatsappbackup.premium.domain.model.ExtractionCheckpoint): CheckpointEntity {
            return CheckpointEntity(
                chatId = checkpoint.chatId,
                chatName = checkpoint.chatName,
                chatType = checkpoint.chatType,
                scrollPosition = checkpoint.scrollPosition,
                messagesExtracted = checkpoint.messagesExtracted,
                lastTimestamp = checkpoint.lastTimestamp,
                monthsBack = checkpoint.monthsBack,
                isComplete = checkpoint.isComplete,
                isPartial = checkpoint.isPartial,
                priority = checkpoint.priority,
                lastExecutionTime = checkpoint.lastExecutionTime,
                retryCount = checkpoint.retryCount,
                errorMessage = checkpoint.errorMessage
            )
        }
    }
}
