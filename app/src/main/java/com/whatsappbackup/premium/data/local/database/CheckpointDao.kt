package com.whatsappbackup.premium.data.local.database

import androidx.room.*
import com.whatsappbackup.premium.domain.model.ExtractionPriority
import kotlinx.coroutines.flow.Flow

/**
 * DAO para operações de checkpoint no banco de dados local
 */
@Dao
interface CheckpointDao {
    
    @Query("SELECT * FROM extraction_checkpoints WHERE chatId = :chatId")
    suspend fun getCheckpointByChatId(chatId: String): CheckpointEntity?
    
    @Query("SELECT * FROM extraction_checkpoints WHERE chatId = :chatId")
    fun getCheckpointFlow(chatId: String): Flow<CheckpointEntity?>
    
    @Query("SELECT * FROM extraction_checkpoints ORDER BY priority ASC, lastExecutionTime ASC")
    suspend fun getAllCheckpoints(): List<CheckpointEntity>
    
    @Query("SELECT * FROM extraction_checkpoints WHERE isComplete = 0 ORDER BY priority ASC, lastExecutionTime ASC")
    suspend fun getIncompleteCheckpoints(): List<CheckpointEntity>
    
    @Query("SELECT * FROM extraction_checkpoints WHERE priority = :priority AND isComplete = 0")
    suspend fun getCheckpointsByPriority(priority: ExtractionPriority): List<CheckpointEntity>
    
    @Query("SELECT * FROM extraction_checkpoints WHERE chatType = :chatType AND isComplete = 0")
    suspend fun getCheckpointsByChatType(chatType: com.whatsappbackup.premium.domain.model.ChatType): List<CheckpointEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckpoint(checkpoint: CheckpointEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCheckpoints(checkpoints: List<CheckpointEntity>)
    
    @Update
    suspend fun updateCheckpoint(checkpoint: CheckpointEntity)
    
    @Delete
    suspend fun deleteCheckpoint(checkpoint: CheckpointEntity)
    
    @Query("DELETE FROM extraction_checkpoints WHERE chatId = :chatId")
    suspend fun deleteCheckpointByChatId(chatId: String)
    
    @Query("UPDATE extraction_checkpoints SET isComplete = 1, lastExecutionTime = :timestamp WHERE chatId = :chatId")
    suspend fun markAsComplete(chatId: String, timestamp: Long = System.currentTimeMillis())
    
    @Query("UPDATE extraction_checkpoints SET retryCount = retryCount + 1, errorMessage = :error, lastExecutionTime = :timestamp WHERE chatId = :chatId")
    suspend fun incrementRetryCount(chatId: String, error: String?, timestamp: Long = System.currentTimeMillis())
    
    @Query("SELECT COUNT(*) FROM extraction_checkpoints WHERE isComplete = 0")
    suspend fun getIncompleteCount(): Int
    
    @Query("SELECT COUNT(*) FROM extraction_checkpoints WHERE isComplete = 1")
    suspend fun getCompletedCount(): Int
    
    @Query("SELECT * FROM extraction_checkpoints WHERE isComplete = 0 LIMIT 1")
    suspend fun getNextPendingCheckpoint(): CheckpointEntity?
}
