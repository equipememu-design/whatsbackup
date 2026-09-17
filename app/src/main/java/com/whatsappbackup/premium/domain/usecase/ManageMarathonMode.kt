package com.whatsappbackup.premium.domain.usecase

import com.whatsappbackup.premium.data.repository.CheckpointRepository
import com.whatsappbackup.premium.domain.model.ChatType
import com.whatsappbackup.premium.domain.model.ExtractionCheckpoint
import com.whatsappbackup.premium.domain.model.ExtractionPriority
import com.whatsappbackup.premium.domain.model.MarathonState
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Caso de uso para gerenciar o Modo Marathon (Backfilling de histórico)
 * 
 * Responsabilidades:
 * - Iniciar extração em lotes (chunking)
 * - Salvar checkpoints para retomada exata
 * - Aplicar throttling com delays aleatórios
 * - Calcular progresso global
 */
@Singleton
class ManageMarathonMode @Inject constructor(
    private val checkpointRepository: CheckpointRepository
) {
    
    /**
     * Inicia ou retoma extração do histórico de um chat
     * 
     * @param chatId ID do chat
     * @param chatName Nome do chat
     * @param chatType Tipo (PRIVATE ou GROUP)
     * @param scrollsPerExecution Quantidade de rolagens por execução (padrão: 50)
     * @return ExtractionCheckpoint atualizado
     */
    suspend fun executeExtraction(
        chatId: String,
        chatName: String,
        chatType: ChatType,
        scrollsPerExecution: Int = ExtractionCheckpoint.SCROLLS_PER_EXECUTION
    ): ExtractionResult {
        // Obtém checkpoint existente ou cria novo
        val checkpoint = checkpointRepository.getCheckpoint(chatId)
            ?: ExtractionCheckpoint(
                chatId = chatId,
                chatName = chatName,
                chatType = chatType,
                priority = determinePriority(chatType, estimatedMessages = 0)
            )
        
        // Verifica se deve continuar
        if (!checkpoint.shouldContinue()) {
            return ExtractionResult(
                success = false,
                errorMessage = "Máximo de retries atingido ou extração completa",
                checkpoint = checkpoint
            )
        }
        
        // Executa lote de rolagens
        val newScrollPosition = checkpoint.scrollPosition + scrollsPerExecution
        val newMessagesExtracted = checkpoint.messagesExtracted + estimateMessagesPerScroll(scrollsPerExecution)
        
        // Atualiza checkpoint
        val updatedCheckpoint = checkpoint.copy(
            scrollPosition = newScrollPosition,
            messagesExtracted = newMessagesExtracted,
            lastExecutionTime = System.currentTimeMillis(),
            monthsBack = checkpoint.monthsBack + ExtractionCheckpoint.MONTHS_PER_EXECUTION
        )
        
        // Verifica se completou (chegou ao início ou limite de 24 meses)
        val isComplete = updatedCheckpoint.monthsBack >= 24 || reachedChatBeginning(updatedCheckpoint)
        
        val finalCheckpoint = if (isComplete) {
            updatedCheckpoint.copy(
                isComplete = true,
                lastExecutionTime = System.currentTimeMillis()
            )
        } else {
            updatedCheckpoint
        }
        
        // Salva checkpoint
        checkpointRepository.saveCheckpoint(finalCheckpoint)
        
        return ExtractionResult(
            success = true,
            isComplete = isComplete,
            messagesExtracted = finalCheckpoint.messagesExtracted,
            checkpoint = finalCheckpoint
        )
    }
    
    /**
     * Determina prioridade baseada no tipo e tamanho do chat
     */
    private fun determinePriority(chatType: ChatType, estimatedMessages: Long): ExtractionPriority {
        return when {
            chatType == ChatType.PRIVATE && estimatedMessages < 1000 -> ExtractionPriority.HIGH
            chatType == ChatType.PRIVATE -> ExtractionPriority.MEDIUM
            chatType == ChatType.GROUP -> ExtractionPriority.LOW
            else -> ExtractionPriority.MEDIUM
        }
    }
    
    /**
     * Estima mensagens por rolagem (baseado em média empírica)
     */
    private fun estimateMessagesPerScroll(scrolls: Int): Long {
        return scrolls * 20L // Aproximadamente 20 mensagens por rolagem
    }
    
    /**
     * Verifica se chegou ao início do chat
     * (implementação real verificaria se não há mais mensagens antigas)
     */
    private suspend fun reachedChatBeginning(checkpoint: ExtractionCheckpoint): Boolean {
        // Placeholder - implementação real verificaria com AccessibilityService
        return false
    }
    
    /**
     * Aplica delay aleatório para throttling (evita sobrecarga de CPU/bateria)
     * 
     * @param minMs Delay mínimo em ms
     * @param maxMs Delay máximo em ms
     */
    suspend fun applyThrottling(minMs: Long = 100, maxMs: Long = 500) {
        val randomDelay = (minMs..maxMs).random()
        kotlinx.coroutines.delay(randomDelay)
    }
    
    /**
     * Obtém estado global do Modo Marathon
     */
    suspend fun getMarathonState(): MarathonState {
        return checkpointRepository.getMarathonState()
    }
    
    /**
     * Marca chat como completo e migra para modo de manutenção (24h/48h)
     */
    suspend fun completeAndMigrateToMaintenance(chatId: String) {
        checkpointRepository.markAsComplete(chatId)
    }
    
    /**
     * Cancela extração de um chat
     */
    suspend fun cancelExtraction(chatId: String) {
        checkpointRepository.removeCheckpoint(chatId)
    }
}

/**
 * Resultado da execução de extração
 */
data class ExtractionResult(
    val success: Boolean,
    val isComplete: Boolean = false,
    val messagesExtracted: Long = 0L,
    val errorMessage: String? = null,
    val checkpoint: ExtractionCheckpoint
)
