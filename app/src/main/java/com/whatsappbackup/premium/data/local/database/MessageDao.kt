package com.whatsappbackup.premium.data.local.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operações de mensagens no banco de dados local
 */
@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    suspend fun getMessagesByChatId(chatId: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesFlow(chatId: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE hash = :hash LIMIT 1")
    suspend fun getMessageByHash(hash: String): MessageEntity?
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND timestamp >= :fromTimestamp AND timestamp <= :toTimestamp ORDER BY timestamp ASC")
    suspend fun getMessagesByPeriod(chatId: String, fromTimestamp: Long, toTimestamp: Long): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE chatId = :chatId AND isMediaLinked = 1 ORDER BY timestamp ASC")
    suspend fun getMessagesWithMedia(chatId: String): List<MessageEntity>
    
    @Query("SELECT * FROM messages WHERE LENGTH(bodyText) > :minLength ORDER BY LENGTH(bodyText) DESC")
    suspend fun getLongMessages(minLength: Int = 10000): List<MessageEntity>
    
    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId")
    suspend fun getMessageCount(chatId: String): Int
    
    @Query("SELECT COUNT(*) FROM messages WHERE hash = :hash")
    suspend fun countByHash(hash: String): Int
    
    @Query("SELECT DISTINCT chatId FROM messages")
    suspend fun getAllChatIds(): List<String>
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllMessages(messages: List<MessageEntity>): List<Long>
    
    @Update
    suspend fun updateMessage(message: MessageEntity)
    
    @Delete
    suspend fun deleteMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesByChatId(chatId: String)
    
    @Query("DELETE FROM messages WHERE hash = :hash")
    suspend fun deleteMessageByHash(hash: String)
    
    /**
     * Insere mensagem com deduplicação por hash
     * Retorna ID da mensagem inserida ou ID existente se duplicada
     */
    @Transaction
    suspend fun insertOrSkipDuplicate(message: MessageEntity): Long? {
        val existing = getMessageByHash(message.hash)
        return if (existing != null) {
            existing.id // Já existe, retorna ID existente
        } else {
            val newId = insertMessage(message)
            if (newId > 0) newId else null
        }
    }
    
    /**
     * Insere lote de mensagens com deduplicação
     * Retorna quantidade de mensagens novas inseridas
     */
    @Transaction
    suspend fun insertBatchWithDeduplication(messages: List<MessageEntity>): Int {
        var insertedCount = 0
        
        for (message in messages) {
            val existing = getMessageByHash(message.hash)
            if (existing == null) {
                val result = insertMessage(message)
                if (result > 0) insertedCount++
            }
        }
        
        return insertedCount
    }
}
