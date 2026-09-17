package com.whatsappbackup.premium.ui.di

import android.content.Context
import androidx.work.WorkManager
import com.whatsappbackup.premium.data.crypto.BackupCryptoManager
import com.whatsappbackup.premium.data.local.BackupPreferences
import com.whatsappbackup.premium.data.parser.ChatTxtParser
import com.whatsappbackup.premium.data.worker.BackupWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Módulo Hilt para Dependency Injection
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideBackupCryptoManager(): BackupCryptoManager {
        return BackupCryptoManager()
    }
    
    @Provides
    @Singleton
    fun provideBackupPreferences(
        @ApplicationContext context: Context
    ): BackupPreferences {
        return BackupPreferences(context)
    }
    
    @Provides
    @Singleton
    fun provideChatTxtParser(): ChatTxtParser {
        return ChatTxtParser()
    }
    
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
    
    @Provides
    @Singleton
    fun provideBackupWorkFactory(
        @ApplicationContext context: Context
    ): BackupWorker.BackupWorkFactory {
        return BackupWorker.BackupWorkFactory(context)
    }
}
