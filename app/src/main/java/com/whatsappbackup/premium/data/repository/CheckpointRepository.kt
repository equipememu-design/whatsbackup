package com.whatsappbackup.premium.data.repository

import com.whatsappbackup.premium.data.local.database.CheckpointDao
import com.whatsappbackup.premium.data.local.database.CheckpointEntity
import com.whatsappbackup.premium.domain.model.ChatType
import com.whatsappbackup.premium.domain.model.ExtractionCheckpoint
import com.whatsappbackup.premium.domain.model.ExtractionPriority
import com.whatsappbackup.premium.domain.model.MarathonState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório para gerenciamento de checkpoints do Modo Marathon
 * 
 * Responsabilidades:
 * - Salvar/recuperar checkpoints de extração
 * - Gerenciar fila priorizada (privados menores -> privados maiores -> grupos gigantes)
 * - Calcular progresso global e tempo restante estimado
 */
@Singleton
class CheckpointRepository @Inject constructor(
    private val checkpointDao: CheckpointDao
) {
    
    /**
     * Obtém checkpoint de um chat específico
     */
    suspend fun getCheckpoint(chatId: String): ExtractionCheckpoint? {
        return checkpointDao.getCheckpointByChatId(chatId)?.toDomain()
    }
    
    /**
     * Flow para observar mudanças em um checkpoint
     */
    fun getCheckpointFlow(chatId: String): Flow<ExtractionCheckpoint?> {
        return checkpointDao.getCheckpointFlow(chatId).map { it?.toDomain() }
    }
    
    /**
     * Salva ou atualiza checkpoint
     */
    suspend fun saveCheckpoint(checkpoint: ExtractionCheckpoint) {
        checkpointDao.insertCheckpoint(CheckpointEntity.fromDomain(checkpoint))
    }
    
    /**
     * Atualiza checkpoint existente
     */
    suspend fun updateCheckpoint(checkpoint: ExtractionCheckpoint) {
        checkpointDao.updateCheckpoint(CheckpointEntity.fromDomain(checkpoint))
    }
    
    /**
     * Marca checkpoint como completo e migra para modo de manutenção
     */
    suspend fun markAsComplete(chatId: String) {
        checkpointDao.markAsComplete(chatId)
    }
    
    /**
     * Remove checkpoint de um chat
     */
    suspend fun removeCheckpoint(chatId: String) {
        checkpointDao.deleteCheckpointByChatId(chatId)
    }
    
    /**
     * Obtém próximo checkpoint pendente baseado na prioridade
     * Ordem: HIGH (privados menores) -> MEDIUM (privados maiores) -> LOW (grupos)
     */
    suspend fun getNextPendingCheckpoint(): ExtractionCheckpoint? {
        // Tenta primeiro os de alta prioridade
        val highPriority = checkpointDao.getCheckpointsByPriority(ExtractionPriority.HIGH)
        if (highPriority.isNotEmpty()) {
            return highPriority.firstOrNull { !it.isComplete }?.toDomain()
        }
        
        // Depois média prioridade
        val mediumPriority = checkpointDao.getCheckpointsByPriority(ExtractionPriority.MEDIUM)
        if (mediumPriority.isNotEmpty()) {
            return mediumPriority.firstOrNull { !it.isComplete }?.toDomain()
        }
        
        // Por último baixa prioridade
        val lowPriority = checkpointDao.getCheckpointsByPriority(ExtractionPriority.LOW)
        return lowPriority.firstOrNull { !it.isComplete }?.toDomain()
    }
    
    /**
     * Obtém todos checkpoints incompletos ordenados por prioridade
     */
    suspend fun getAllIncompleteCheckpoints(): List<ExtractionCheckpoint> {
        return checkpointDao.getIncompleteCheckpoints().map { it.toDomain() }
    }
    
    /**
     * Cria fila priorizada para Modo Marathon
     * 
     * Regras de prioridade:
     * 1. Privados menores (HIGH)
     * 2. Privados maiores (MEDIUM)
     * 3. Grupos gigantes (LOW)
     */
    suspend fun createPrioritizedQueue(chatIds: List<String>): List<ExtractionCheckpoint> {
        val checkpoints = chatIds.map { id ->
            checkpointDao.getCheckpointByChatId(id) ?: run {
                // Cria novo checkpoint se não existir
                val newCheckpoint = CheckpointEntity(
                    chatId = id,
                    chatName = "Chat $id",
                    chatType = ChatType.PRIVATE, // Será atualizado depois
                    priority = ExtractionPriority.MEDIUM
                )
                checkpointDao.insertCheckpoint(newCheckpoint)
                newCheckpoint
            }
        }
        
        // Ordena por prioridade e tamanho
        return checkpoints.sortedWith(
            compareBy<CheckpointEntity> { it.priority }
                .thenBy { it.chatType }  // PRIVATE antes de GROUP
                .thenByDescending { it.estimatedTotalMessages ?: 0L }
        ).map { it.toDomain() }
    }
    
    /**
     * Calcula estado global do Modo Marathon
     */
    suspend fun getMarathonState(): MarathonState {
        val incompleteCount = checkpointDao.getIncompleteCount()
        val completedCount = checkpointDao.getCompletedCount()
        val total = incompleteCount + completedCount
        
        val allCheckpoints = checkpointDao.getAllCheckpoints()
        val avgMessagesPerHour = 5000 // Estimativa baseada em testes empíricos
        
        val totalMessagesRemaining = allCheckpoints
            .filter { !it.isComplete }
            .sumOf { it.estimatedTotalMessages ?: 0L } - 
             allCheckpoints.filter { !it.isComplete }.sumOf { it.messagesExtracted }
        
        val estimatedHours = if (avgMessagesPerHour > 0) {
            totalMessagesRemaining.toDouble() / avgMessagesPerHour
        } else {
            0.0
        }
        
        val globalProgress = if (total > 0) {
            (completedCount * 100) / total
        } else {
            0
        }
        
        return MarathonState(
            isRunning = incompleteCount > 0,
            currentChatId = allCheckpoints.firstOrNull { !it.isComplete }?.chatId,
            totalChatsQueued = total,
            chatsCompleted = completedCount,
            estimatedRemainingHours = estimatedHours,
            globalProgressPercent = globalProgress,
            startTime = allCheckpoints.minOfOrNull { it.lastExecutionTime }
        )
    }
    
    /**
     * Incrementa contador de retry e salva erro
     */
    suspend fun incrementRetry(chatId: String, errorMessage: String?) {
        checkpointDao.incrementRetryCount(chatId, errorMessage)
    }
    
    /**
     * Verifica se deve continuar extração (retry < MAX_RETRIES)
     */
    suspend fun shouldContinue(chatId: String): Boolean {
        val checkpoint = getCheckpoint(chatId)
        return checkpoint?.shouldContinue() == true
    }
}
