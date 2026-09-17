package com.whatsappbackup.premium.data.parser

import com.whatsappbackup.premium.domain.model.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern

/**
 * Parser interno que converte arquivos .txt exportados do WhatsApp
 * em estrutura visual idêntica ao Chat Simulator
 */
class ChatTxtParser {
    
    companion object {
        // Padrão para mensagens do WhatsApp: [dd/mm/aa, HH:MM:SS] Remetente: Mensagem
        private val MESSAGE_PATTERN = Pattern.compile(
            """^\[(\d{2}/\d{2}/\d{2,4}),?\s*(\d{1,2}:\d{2}(?::\d{2})?)\]\s*(.+?):\s*(.*)$"""
        )
        
        // Padrão para mensagens de sistema (sem remetente)
        private val SYSTEM_MESSAGE_PATTERN = Pattern.compile(
            """^\[(\d{2}/\d{2}/\d{2,4}),?\s*(\d{1,2}:\d{2}(?::\d{2})?)\]\s*(.*)$"""
        )
        
        private val DATE_FORMATS = listOf(
            "dd/MM/yyyy",
            "dd/MM/yy",
            "MM/dd/yyyy",
            "MM/dd/yy"
        )
    }
    
    /**
     * Parseia arquivo .txt exportado do WhatsApp
     * @param txtFile Arquivo de texto exportado
     * @param chatId Identificador único do chat
     * @param chatName Nome original do chat
     * @param chatType Tipo de chat (Privado ou Grupo)
     */
    fun parseChatFile(
        txtFile: File,
        chatId: String,
        chatName: String,
        chatType: ChatType
    ): ParsedChat {
        val messages = mutableListOf<ParsedMessage>()
        var currentMessage: StringBuilder? = null
        var lastTimestamp: Long = 0L
        
        txtFile.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                
                val messageMatch = MESSAGE_PATTERN.matcher(line)
                val systemMatch = SYSTEM_MESSAGE_PATTERN.matcher(line)
                
                when {
                    messageMatch.matches() -> {
                        // Salva mensagem anterior se existir
                        currentMessage?.let { msg ->
                            if (lastTimestamp > 0) {
                                messages.add(createMessage(msg.toString(), lastTimestamp))
                            }
                        }
                        
                        // Nova mensagem
                        val dateStr = messageMatch.group(1)
                        val timeStr = messageMatch.group(2)
                        val sender = messageMatch.group(3).trim()
                        val content = messageMatch.group(4)
                        
                        lastTimestamp = parseTimestamp(dateStr, timeStr)
                        currentMessage = StringBuilder(content)
                    }
                    
                    systemMatch.matches() -> {
                        // Salva mensagem anterior se existir
                        currentMessage?.let { msg ->
                            if (lastTimestamp > 0) {
                                messages.add(createMessage(msg.toString(), lastTimestamp))
                            }
                        }
                        
                        // Mensagem de sistema
                        val dateStr = systemMatch.group(1)
                        val timeStr = systemMatch.group(2)
                        val content = systemMatch.group(3)
                        
                        lastTimestamp = parseTimestamp(dateStr, timeStr)
                        currentMessage = StringBuilder("⚙️ $content")
                    }
                    
                    else -> {
                        // Continuação da mensagem anterior (quebra de linha)
                        currentMessage?.append("\n$line")
                    }
                }
            }
            
            // Adiciona última mensagem
            currentMessage?.let { msg ->
                if (lastTimestamp > 0) {
                    messages.add(createMessage(msg.toString(), lastTimestamp))
                }
            }
        }
        
        return ParsedChat(
            chatId = chatId,
            chatName = chatName,
            chatType = chatType,
            messages = messages.sortedBy { it.timestamp },
            exportDate = txtFile.lastModified()
        )
    }
    
    /**
     * Cria objeto ParsedMessage a partir do conteúdo parserado
     */
    private fun createMessage(content: String, timestamp: Long): ParsedMessage {
        val date = Date(timestamp)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        
        // Detecta se é mensagem enviada por mim
        val isFromMe = content.startsWith("Você:") || content.contains("> Você")
        
        // Detecta tipo de mensagem
        val messageType = detectMessageType(content)
        
        return ParsedMessage(
            timestamp = timestamp,
            dateFormatted = dateFormat.format(date),
            timeFormatted = timeFormat.format(date),
            sender = extractSender(content),
            content = cleanMessageContent(content),
            isFromMe = isFromMe,
            messageType = messageType
        )
    }
    
    /**
     * Detecta tipo de mensagem baseado no conteúdo
     */
    private fun detectMessageType(content: String): MessageType {
        return when {
            content.contains("<arquivo de mídia oculto>") -> MessageType.IMAGE
            content.contains("vídeo anexado") -> MessageType.VIDEO
            content.contains("áudio") -> MessageType.AUDIO
            content.contains("documento") -> MessageType.DOCUMENT
            content.startsWith("⚙️") -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }
    }
    
    /**
     * Extrai nome do remetente do conteúdo da mensagem
     */
    private fun extractSender(content: String): String {
        val match = MESSAGE_PATTERN.matcher(content)
        return if (match.matches()) {
            match.group(3).trim()
        } else {
            "Sistema"
        }
    }
    
    /**
     * Limpa conteúdo preservando corpo INTEGRAL da mensagem
     */
    private fun cleanMessageContentPreservingBody(content: String, mediaPath: String?): String {
        var cleaned = content
        
        // Remove padrão de timestamp e remetente apenas do início
        cleaned = cleaned.replace(Regex("""^\[.+\]\s*.+?:\s*"""), "")
        cleaned = cleaned.replace(Regex("""^\[.+\]\s*"""), "")
        
        // Remove indicação de mídia do texto (já está em mediaPath)
        if (mediaPath != null) {
            cleaned = cleaned.replace(Regex("""IMG-\d+-WA\d+\.\w+\s*(anexado|attached)?""", RegexOption.IGNORE_CASE), "")
                .trim()
        }
        
        // Substitui descrições de mídia por emojis
        cleaned = cleaned
            .replace("<arquivo de mídia oculto>", "📷 Foto")
            .replace("<vídeo anexado>", "🎥 Vídeo")
            .replace("<áudio>", "🎤 Áudio")
            .replace("<documento>", "📄 Documento")
            .trim()
        
        return cleaned
    }
    
    /**
     * Detecta tipo de mensagem baseado no conteúdo
     */
    private fun detectMessageType(content: String): MessageType {
        return when {
            content.contains("<arquivo de mídia oculto>") || content.contains("IMG-") -> MessageType.IMAGE
            content.contains("vídeo anexado") -> MessageType.VIDEO
            content.contains("áudio") -> MessageType.AUDIO
            content.contains("documento") -> MessageType.DOCUMENT
            content.startsWith("⚙️") -> MessageType.SYSTEM
            else -> MessageType.TEXT
        }
    }
    
    /**
     * Extrai nome do remetente do conteúdo da mensagem
     */
    private fun extractSender(content: String): String {
        val match = MESSAGE_PATTERN.matcher(content)
        return if (match.matches()) {
            match.group(3).trim()
        } else {
            "Sistema"
        }
    }
    
    /**
     * Parseia timestamp da data e hora
     */
    private fun parseTimestamp(dateStr: String, timeStr: String): Long {
        return try {
            val normalizedDate = normalizeDate(dateStr)
            val fullDateTime = "$normalizedDate $timeStr"
            
            for (format in DATE_FORMATS) {
                try {
                    val sdf = SimpleDateFormat("$format HH:mm:ss", Locale.getDefault())
                    sdf.isLenient = true
                    return sdf.parse(fullDateTime)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    continue
                }
            }
            
            // Fallback: usa formato padrão
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            sdf.isLenient = true
            sdf.parse(fullDateTime)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
    
    /**
     * Normaliza data para formato consistente
     */
    private fun normalizeDate(dateStr: String): String {
        return dateStr.trim()
    }
}
