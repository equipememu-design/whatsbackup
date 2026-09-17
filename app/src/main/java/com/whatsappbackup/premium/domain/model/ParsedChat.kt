package com.whatsappbackup.premium.domain.model

/**
 * Modelo para mensagens parseradas de arquivos .txt exportados
 * Usado pelo Chat Simulator para renderização estilo WhatsApp
 */
data class ParsedMessage(
    val timestamp: Long,
    val dateFormatted: String,
    val timeFormatted: String,
    val sender: String,
    val content: String,
    val isFromMe: Boolean,
    val messageType: MessageType = MessageType.TEXT
) {
    /**
     * Ofusca o remetente para Modo Incógnito
     */
    fun getObfuscatedSender(contactId: Int): String {
        return when {
            sender.isBlank() -> "Desconhecido"
            sender.length < 3 -> "Contato #$contactId"
            else -> "Contato #${contactId}"
        }
    }
}

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    SYSTEM // Mensagens de sistema (ex: "Você mudou a descrição do grupo")
}

/**
 * Conversa completa parserada para visualização offline
 */
data class ParsedChat(
    val chatId: String,
    val chatName: String,
    val chatType: ChatType,
    val messages: List<ParsedMessage>,
    val exportDate: Long,
    val messageCount: Int = messages.size,
    val firstMessageDate: Long = messages.minOfOrNull { it.timestamp } ?: 0L,
    val lastMessageDate: Long = messages.maxOfOrNull { it.timestamp } ?: 0L
) {
    /**
     * Filtra mensagens por período para visualização paginada
     */
    fun getMessagesInRange(startIndex: Int, count: Int): List<ParsedMessage> {
        return messages.drop(startIndex).take(count)
    }
    
    /**
     * Obtém nome ofuscado para Modo Incógnito
     */
    fun getObfuscatedName(chatIndex: Int): String {
        return when (chatType) {
            ChatType.PRIVATE -> "Contato #$chatIndex"
            ChatType.GROUP -> "Grupo #$chatIndex"
        }
    }
}
