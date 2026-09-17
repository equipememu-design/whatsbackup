package com.whatsappbackup.premium.data.local.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Entidade Room para armazenar mensagens parseadas
 * 
 * Suporta:
 * - Corpo INTEGRAL de mensagens (sem truncamento)
 * - Mídia vinculada (IMG-...jpg)
 * - Deduplicação por hash
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["hash"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["chatId", "timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    val chatId: String,
    val sender: String,
    val timestamp: Long,
    val bodyText: String,              // Corpo INTEGRAL (nunca truncar)
    val mediaPath: String? = null,
    val hash: String,
    val isMediaLinked: Boolean = false,
    val isExpanded: Boolean = true,
    val lineNumber: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
) {
    /**
     * Converte para modelo de domínio
     */
    fun toDomain(): com.whatsappbackup.premium.domain.model.Message {
        return com.whatsappbackup.premium.domain.model.Message(
            id = id,
            chatId = chatId,
            sender = sender,
            timestamp = timestamp,
            bodyText = bodyText,
            mediaPath = mediaPath,
            hash = hash,
            isMediaLinked = isMediaLinked,
            isExpanded = isExpanded,
            lineNumber = lineNumber
        )
    }
    
    companion object {
        /**
         * Cria entidade a partir de modelo de domínio
         */
        fun fromDomain(message: com.whatsappbackup.premium.domain.model.Message): MessageEntity {
            return MessageEntity(
                id = message.id,
                chatId = message.chatId,
                sender = message.sender,
                timestamp = message.timestamp,
                bodyText = message.bodyText,
                mediaPath = message.mediaPath,
                hash = message.hash,
                isMediaLinked = message.isMediaLinked,
                isExpanded = message.isExpanded,
                lineNumber = message.lineNumber
            )
        }
    }
}
