package com.whatsappbackup.premium.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.whatsappbackup.premium.data.crypto.BackupCryptoManager
import com.whatsappbackup.premium.data.local.BackupPreferences
import com.whatsappbackup.premium.domain.model.Chat
import com.whatsappbackup.premium.domain.model.ChatType
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker para cópia de arquivos .crypt em segundo plano
 * 
 * Características:
 * - Retry policy exponencial para falhas temporárias
 * - Execução em segundo plano com restrições de rede/bateria
 * - Integração com criptografia AES-256
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val backupPreferences: BackupPreferences,
    private val cryptoManager: BackupCryptoManager
) : CoroutineWorker(context, workerParams) {
    
    companion object {
        const val WORK_NAME = "whatsapp_backup_worker"
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_CHAT_NAME = "chat_name"
        const val KEY_CHAT_TYPE = "chat_type"
        const val KEY_SOURCE_PATH = "source_path"
        const val KEY_DEST_PATH = "dest_path"
        
        // Pasta de backups criptografados
        const val BACKUP_FOLDER = "MeusBackups"
    }
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val chatId = inputData.getString(KEY_CHAT_ID) ?: return@withContext Result.failure()
                val chatName = inputData.getString(KEY_CHAT_NAME) ?: ""
                val chatTypeStr = inputData.getString(KEY_CHAT_TYPE) ?: ChatType.PRIVATE.name
                val sourcePath = inputData.getString(KEY_SOURCE_PATH) ?: return@withContext Result.failure()
                val destPath = inputData.getString(KEY_DEST_PATH) ?: getDefaultDestPath(chatId)
                
                val chatType = ChatType.valueOf(chatTypeStr)
                
                // Cria chat model para regras de negócio
                val chat = Chat(
                    id = chatId,
                    name = chatName,
                    type = chatType,
                    lastMessageTime = System.currentTimeMillis(),
                    messageCount = 0,
                    backupSizeBytes = 0L,
                    includeInBackup = true
                )
                
                // Verifica se deve incluir no backup
                if (!chat.includeInBackup) {
                    return@withContext Result.success()
                }
                
                // Executa backup
                performBackup(sourcePath, destPath, chat)
                
                // Atualiza preferências
                backupPreferences.updateLastBackupTime(chatId, System.currentTimeMillis())
                backupPreferences.incrementBackupCount(chatId)
                
                Result.success()
                
            } catch (e: Exception) {
                e.printStackTrace()
                
                // Decide se faz retry baseado no tipo de erro
                if (isRetryableError(e)) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        }
    }
    
    /**
     * Executa processo de backup com criptografia
     */
    private suspend fun performBackup(sourcePath: String, destPath: String, chat: Chat) {
        val sourceFile = File(sourcePath)
        val destFile = File(destPath)
        
        // Garante que diretório de destino existe
        destFile.parentFile?.mkdirs()
        
        when {
            // Backup de arquivo .crypt (banco de dados do WhatsApp)
            sourceFile.extension == "crypt" || sourceFile.extension == "db" -> {
                backupCryptFile(sourceFile, destFile, chat)
            }
            
            // Backup de chat exportado (.txt)
            sourceFile.extension == "txt" -> {
                backupTxtFile(sourceFile, destFile, chat)
            }
            
            else -> {
                throw IllegalArgumentException("Tipo de arquivo não suportado: ${sourceFile.extension}")
            }
        }
    }
    
    /**
     * Backup de arquivo .crypt com criptografia
     */
    private suspend fun backupCryptFile(sourceFile: File, destFile: File, chat: Chat) {
        // Copia arquivo temporário
        val tempFile = File.createTempFile("backup_", ".tmp")
        sourceFile.copyTo(tempFile, overwrite = true)
        
        // Criptografa arquivo
        cryptoManager.encryptFile(tempFile, destFile)
        
        // Calcula e armazena checksum
        val checksum = cryptoManager.calculateChecksum(destFile)
        backupPreferences.saveChecksum(chat.id, checksum)
    }
    
    /**
     * Backup de arquivo .txt com criptografia
     */
    private suspend fun backupTxtFile(sourceFile: File, destFile: File, chat: Chat) {
        // Criptografa arquivo diretamente
        cryptoManager.encryptFile(sourceFile, destFile)
        
        // Calcula e armazena checksum
        val checksum = cryptoManager.calculateChecksum(destFile)
        backupPreferences.saveChecksum(chat.id, checksum)
    }
    
    /**
     * Gera caminho padrão de destino para backup
     */
    private fun getDefaultDestPath(chatId: String): String {
        val backupDir = File(applicationContext.filesDir, BACKUP_FOLDER)
        backupDir.mkdirs()
        return File(backupDir, "${chatId}_${System.currentTimeMillis()}.enc").absolutePath
    }
    
    /**
     * Verifica se erro é passível de retry
     */
    private fun isRetryableError(e: Exception): Boolean {
        return when (e) {
            is java.io.IOException -> true // Erros de I/O podem ser temporários
            is java.net.SocketTimeoutException -> true // Timeout de rede
            is java.util.concurrent.TimeoutException -> true
            else -> false
        }
    }
    
    /**
     * Factory para criar WorkRequest com retry policy exponencial
     */
    class BackupWorkFactory @Inject constructor(
        private val context: Context
    ) {
        fun createOneTimeWorkRequest(
            chatId: String,
            chatName: String,
            chatType: ChatType,
            sourcePath: String,
            destPath: String? = null
        ): OneTimeWorkRequest {
            val inputData = Data.Builder()
                .putString(KEY_CHAT_ID, chatId)
                .putString(KEY_CHAT_NAME, chatName)
                .putString(KEY_CHAT_TYPE, chatType.name)
                .putString(KEY_SOURCE_PATH, sourcePath)
                .putString(KEY_DEST_PATH, destPath)
                .build()
            
            return OneTimeWorkRequestBuilder<BackupWorker>()
                .setInputData(inputData)
                .setConstraints(createConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag(chatId)
                .build()
        }
        
        fun createPeriodicWorkRequest(
            intervalHours: Long = 6,
            chatIds: List<String> = emptyList()
        ): PeriodicWorkRequest {
            val inputData = Data.Builder()
                .putStringArray(KEY_CHAT_ID, chatIds.toTypedArray())
                .build()
            
            return PeriodicWorkRequestBuilder<BackupWorker>(intervalHours, TimeUnit.HOURS)
                .setInputData(inputData)
                .setConstraints(createConstraints())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .addTag("periodic_backup")
                .build()
        }
        
        private fun createConstraints(): Constraints {
            return Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // Permite execução mesmo com bateria baixa
                .setRequiresStorageNotLow(true) // Requer espaço disponível
                .build()
        }
    }
}
