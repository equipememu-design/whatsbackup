package com.whatsappbackup.premium.domain.model

/**
 * Modelo para checkpoint de extração do Modo Marathon
 * 
 * Armazena o ponto exato de rolagem de cada chat para retomar
 * exatamente de onde parou em caso de interrupção.
 */
data class ExtractionCheckpoint(
    val chatId: String,
    val chatName: String,
    val chatType: ChatType,
    // Estado da extração
    val scrollPosition: Int = 0,           // Posição atual na lista (número de rolagens)
    val messagesExtracted: Long = 0L,      // Quantidade de mensagens já extraídas
    val lastTimestamp: Long? = null,       // Timestamp da última mensagem extraída
    val monthsBack: Int = 0,               // Quantos meses já foram rebobinados
    // Metadados
    val isComplete: Boolean = false,       // True quando chegou ao início do chat
    val isPartial: Boolean = false,        // True se há histórico parcial (teto do .txt)
    val priority: ExtractionPriority = ExtractionPriority.MEDIUM,
    // Controle de execução
    val lastExecutionTime: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val errorMessage: String? = null
) {
    /**
     * Calcula progresso percentual estimado
     * Baseado em heurística de tempo (mensagens no período)
     */
    fun getProgressPercentage(): Int {
        return if (isComplete) 100
        else if (monthsBack >= 24) 100 // Limite de 2 anos
        else (monthsBack * 100) / 24
    }
    
    /**
     * Verifica se deve continuar extração
     */
    fun shouldContinue(): Boolean = !isComplete && retryCount < MAX_RETRIES
    
    companion object {
        const val MAX_RETRIES = 3
        const val SCROLLS_PER_EXECUTION = 50
        const val MONTHS_PER_EXECUTION = 1
    }
}

/**
 * Prioridade de extração para fila priorizada
 */
enum class ExtractionPriority {
    HIGH,       // Privados menores primeiro
    MEDIUM,     // Privados maiores depois
    LOW         // Grupos gigantes por último
}

/**
 * Estado global do Modo Marathon
 */
data class MarathonState(
    val isRunning: Boolean = false,
    val currentChatId: String? = null,
    val totalChatsQueued: Int = 0,
    val chatsCompleted: Int = 0,
    val estimatedRemainingHours: Double = 0.0,
    val globalProgressPercent: Int = 0,
    val startTime: Long? = null
) {
    fun getFormattedRemainingTime(): String {
        if (estimatedRemainingHours <= 0) return "Calculando..."
        
        val hours = estimatedRemainingHours.toInt()
        val minutes = ((estimatedRemainingHours - hours) * 60).toInt()
        
        return if (hours > 0) {
            "${hours}h ${minutes}m"
        } else {
            "${minutes}m"
        }
    }
}
