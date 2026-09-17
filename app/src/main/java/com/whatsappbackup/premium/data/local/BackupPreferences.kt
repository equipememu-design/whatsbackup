package com.whatsappbackup.premium.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferences criptografadas para configurações sensíveis
 * Usa EncryptedSharedPreferences do AndroidX Security
 */
@Singleton
class BackupPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "whatsapp_backup_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    // Chaves de preferência
    companion object {
        private const val KEY_LAST_BACKUP_PREFIX = "last_backup_"
        private const val KEY_BACKUP_COUNT_PREFIX = "backup_count_"
        private const val KEY_CHECKSUM_PREFIX = "checksum_"
        private const val KEY_INCognito_MODE = "incognito_mode"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_CLEANUP_DAYS = "cleanup_days"
        private const val KEY_DAILY_GROWTH_RATE = "daily_growth_rate"
    }
    
    /**
     * Atualiza timestamp do último backup de um chat
     */
    fun updateLastBackupTime(chatId: String, timestamp: Long) {
        sharedPreferences.edit {
            putLong("${KEY_LAST_BACKUP_PREFIX}$chatId", timestamp)
        }
    }
    
    /**
     * Obtém timestamp do último backup
     */
    fun getLastBackupTime(chatId: String): Long? {
        return sharedPreferences.getLong("${KEY_LAST_BACKUP_PREFIX}$chatId", -1L)
            .takeIf { it != -1L }
    }
    
    /**
     * Incrementa contador de backups realizados
     */
    fun incrementBackupCount(chatId: String) {
        val current = sharedPreferences.getInt("${KEY_BACKUP_COUNT_PREFIX}$chatId", 0)
        sharedPreferences.edit {
            putInt("${KEY_BACKUP_COUNT_PREFIX}$chatId", current + 1)
        }
    }
    
    /**
     * Obtém contador de backups
     */
    fun getBackupCount(chatId: String): Int {
        return sharedPreferences.getInt("${KEY_BACKUP_COUNT_PREFIX}$chatId", 0)
    }
    
    /**
     * Salva checksum de arquivo para validação de integridade
     */
    fun saveChecksum(chatId: String, checksum: String) {
        sharedPreferences.edit {
            putString("${KEY_CHECKSUM_PREFIX}$chatId", checksum)
        }
    }
    
    /**
     * Obtém checksum armazenado
     */
    fun getChecksum(chatId: String): String? {
        return sharedPreferences.getString("${KEY_CHECKSUM_PREFIX}$chatId", null)
    }
    
    /**
     * Estado do Modo Incógnito (Zona de Silêncio)
     */
    var isIncognitoMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_INCognito_MODE, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(KEY_INCognito_MODE, value)
            }
        }
    
    /**
     * Biometria habilitada para desbloqueio
     */
    var isBiometricEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_BIOMETRIC_ENABLED, true)
        set(value) {
            sharedPreferences.edit {
                putBoolean(KEY_BIOMETRIC_ENABLED, value)
            }
        }
    
    /**
     * Backup automático habilitado
     */
    var isAutoBackupEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
        set(value) {
            sharedPreferences.edit {
                putBoolean(KEY_AUTO_BACKUP_ENABLED, value)
            }
        }
    
    /**
     * Dias para limpeza automática de grupos
     */
    var cleanupDaysThreshold: Int
        get() = sharedPreferences.getInt(KEY_CLEANUP_DAYS, 90)
        set(value) {
            sharedPreferences.edit {
                putInt(KEY_CLEANUP_DAYS, value)
            }
        }
    
    /**
     * Taxa diária de crescimento dos backups (bytes/dia)
     * Usado pelo Radar Preditivo
     */
    var dailyGrowthRateBytes: Long
        get() = sharedPreferences.getLong(KEY_DAILY_GROWTH_RATE, 0L)
        set(value) {
            sharedPreferences.edit {
                putLong(KEY_DAILY_GROWTH_RATE, value)
            }
        }
    
    /**
     * Limpa todos os dados de um chat específico
     */
    fun clearChatData(chatId: String) {
        sharedPreferences.edit {
            remove("${KEY_LAST_BACKUP_PREFIX}$chatId")
            remove("${KEY_BACKUP_COUNT_PREFIX}$chatId")
            remove("${KEY_CHECKSUM_PREFIX}$chatId")
        }
    }
    
    /**
     * Obtém todos os IDs de chats com backup
     */
    fun getAllChatIds(): Set<String> {
        return sharedPreferences.all.keys
            .filter { it.startsWith(KEY_LAST_BACKUP_PREFIX) }
            .map { it.removePrefix(KEY_LAST_BACKUP_PREFIX) }
            .toSet()
    }
    
    /**
     * Calcula estimativa de dias até armazenamento cheio
     */
    fun calculateDaysUntilFull(storageAvailableBytes: Long): Int {
        val growthRate = dailyGrowthRateBytes
        return if (growthRate > 0) {
            (storageAvailableBytes / growthRate).toInt()
        } else {
            -1 // Sem dados suficientes para previsão
        }
    }
}
