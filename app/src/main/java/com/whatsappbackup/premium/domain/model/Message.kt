package com.whatsappbackup.premium.domain.model

/**
 * Modelo de mensagem individual para banco de dados
 * 
 * Armazena mensagens parseadas com corpo INTEGRAL (sem truncamento)
 * e suporte a mídia vinculada.
 */
data class Message(
    val id: Long? = null,              // ID auto-increment no Room
    val chatId: String,                // ID do chat pai
    val sender: String,                // Remetente (nome ou número)
    val timestamp: Long,               // Timestamp da mensagem
    val bodyText: String,              // Corpo INTEGRAL da mensagem (nunca truncar)
    val mediaPath: String? = null,     // Caminho para arquivo de mídia (IMG-...jpg)
    val hash: String,                  // Hash para deduplicação
    val isMediaLinked: Boolean = false,// True se mensagem tem mídia vinculada
    val isExpanded: Boolean = true,    // True se "Ler mais" foi expandido
    val lineNumber: Int = 0            // Linha original no arquivo .txt
) {
    companion object {
        /**
         * Gera hash único para deduplicação
         * Baseado em: chatId + timestamp + sender + bodyText (primeiros 500 chars)
         */
        fun generateHash(chatId: String, sender: String, timestamp: Long, bodyText: String): String {
            val content = "$chatId|$sender|$timestamp|${bodyText.take(500)}"
            return java.security.MessageDigest
                .getInstance("SHA-256")
                .digest(content.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16) // Hash de 16 caracteres
        }
    }
    
    /**
     * Verifica se mensagem é muito longa (> 10k caracteres)
     */
    fun isLongMessage(): Boolean = bodyText.length > 10000
    
    /**
     * Obtém preview da mensagem (para UI)
     */
    fun getPreview(maxLength: Int = 100): String {
        return if (bodyText.length <= maxLength) {
            bodyText
        } else {
            bodyText.take(maxLength) + "..."
        }
    }
}

/**
 * Resultado do parser de .txt com informações de truncamento
 */
data class ParseResult(
    val messages: List<Message>,
    val totalLines: Int,
    val parsedLines: Int,
    val truncatedMessages: Int = 0,    // Mensagens que excederam teto do .txt
    val hasPartialHistory: Boolean = false,
    val earliestTimestamp: Long? = null,
    val latestTimestamp: Long? = null,
    val mediaFilesCount: Int = 0,
    val expandedReadMoreCount: Int = 0 // Quantos "Ler mais" foram expandidos
) {
    val successRate: Double
        get() = if (totalLines > 0) parsedLines.toDouble() / totalLines else 0.0
}

/**
 * Estado do caçador de "Ler mais"
 */
data class ReadMoreState(
    val hasCollapsedBubbles: Boolean = false,
    val collapsedCount: Int = 0,
    val expandedCount: Int = 0,
    val lastExpansionTime: Long = 0L,
    val timeoutReached: Boolean = false
)
