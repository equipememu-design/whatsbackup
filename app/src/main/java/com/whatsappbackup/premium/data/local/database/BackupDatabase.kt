package com.whatsappbackup.premium.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.whatsappbackup.premium.domain.model.ChatType
import com.whatsappbackup.premium.domain.model.ExtractionPriority

/**
 * Database Room para armazenamento local de checkpoints e mensagens
 * 
 * Versões:
 * - v1: Schema inicial com CheckpointEntity e MessageEntity
 */
@Database(
    entities = [
        CheckpointEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class BackupDatabase : RoomDatabase() {
    
    abstract fun checkpointDao(): CheckpointDao
    abstract fun messageDao(): MessageDao
    
    companion object {
        private const val DATABASE_NAME = "whatsapp_backup_db"
        
        @Volatile
        private var INSTANCE: BackupDatabase? = null
        
        fun getInstance(context: Context): BackupDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BackupDatabase::class.java,
                    DATABASE_NAME
                )
                .fallbackToDestructiveMigration() // Em produção, usar migrações adequadas
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Cria fila priorizada de checkpoints para Modo Marathon
         * Ordem: Privados menores -> Privados maiores -> Grupos gigantes
         */
        suspend fun createPrioritizedQueue(
            checkpoints: List<CheckpointEntity>
        ): List<CheckpointEntity> {
            return checkpoints.sortedWith(
                compareBy<CheckpointEntity> { it.priority }
                    .thenBy { it.chatType }  // PRIVATE antes de GROUP
                    .thenByDescending { it.estimatedTotalMessages ?: 0L } // Menores primeiro
            )
        }
    }
}
