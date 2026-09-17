package com.whatsappbackup.premium

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WhatsAppBackupApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
