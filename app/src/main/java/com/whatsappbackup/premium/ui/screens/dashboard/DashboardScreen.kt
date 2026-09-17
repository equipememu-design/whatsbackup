package com.whatsappbackup.premium.ui.screens.dashboard

import android.Manifest
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.core.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.whatsappbackup.premium.data.local.BackupPreferences
import com.whatsappbackup.premium.domain.model.BackupHealth
import com.whatsappbackup.premium.domain.model.HealthStatus
import com.whatsappbackup.premium.ui.components.HealthDashboard
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

/**
 * Dashboard Screen - Tela principal com Dashboard de Saúde (Semáforo)
 * e Zona de Silêncio (Modo Incógnito)
 */
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var healthState by remember { mutableStateOf(BackupHealth.createEmpty()) }
    var isIncognitoMode by remember { mutableStateOf(false) }
    var showBiometricPrompt by remember { mutableStateOf(false) }
    
    // Simula carregamento do estado de saúde
    LaunchedEffect(Unit) {
        // Em produção, isso viria de um ViewModel
        healthState = simulateHealthData()
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header com Modo Incógnito
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isIncognitoMode) "👤 Zona de Silêncio" else "📊 Dashboard",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Toggle Modo Incógnito
            IncognitoToggle(
                isIncognitoMode = isIncognitoMode,
                onToggle = { 
                    if (!isIncognitoMode) {
                        showBiometricPrompt = true
                    } else {
                        isIncognitoMode = false
                    }
                }
            )
        }
        
        // Dashboard de Saúde (Semáforo)
        HealthDashboard(
            status = healthState.status,
            lastBackupTime = healthState.lastBackupTime,
            nextScheduledBackup = healthState.nextScheduledBackup,
            storageUsedBytes = healthState.storageUsedBytes,
            storageAvailableBytes = healthState.storageAvailableBytes,
            validatedBackupsCount = healthState.validatedBackupsCount,
            estimatedDaysUntilFull = healthState.estimatedDaysUntilFull,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Cards de ação rápida
        QuickActionsRow()
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Lista de últimos backups
        RecentBackupsList(isIncognitoMode = isIncognitoMode)
    }
    
    // Prompt biométrico para desbloquear Modo Incógnito
    if (showBiometricPrompt) {
        BiometricAuthDialog(
            onAuthenticated = {
                isIncognitoMode = true
                showBiometricPrompt = false
            },
            onError = {
                showBiometricPrompt = false
            },
            onDismiss = {
                showBiometricPrompt = false
            }
        )
    }
}

/**
 * Toggle para Zona de Silêncio (Modo Incógnito)
 */
@Composable
private fun IncognitoToggle(
    isIncognitoMode: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(
            containerColor = if (isIncognitoMode) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isIncognitoMode) 
                    androidx.compose.material.icons.Icons.Filled.VisibilityOff 
                else 
                    androidx.compose.material.icons.Icons.Filled.Visibility,
                contentDescription = "Modo Incógnito",
                tint = if (isIncognitoMode) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isIncognitoMode) "Ativado" else "Toque para ativar",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isIncognitoMode) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Ações rápidas
 */
@Composable
private fun QuickActionsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = androidx.compose.material.icons.Icons.Filled.Refresh,
            label = "Novo Backup",
            onClick = { /* Ação de backup */ }
        )
        
        QuickActionButton(
            icon = androidx.compose.material.icons.Icons.Filled.Verified,
            label = "Validar",
            onClick = { /* Ação de validação */ }
        )
        
        QuickActionButton(
            icon = androidx.compose.material.icons.Icons.Filled.Group,
            label = "Grupos",
            onClick = { /* Navegar para gestão de grupos */ }
        )
    }
}

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    ElevatedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.elevatedButtonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentBackupsList(isIncognitoMode: Boolean) {
    Text(
        text = "Últimos Backups",
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    
    // Lista simulada
    listOf("Familia", "Trabalho", "João Silva").forEachIndexed { index, name ->
        BackupListItem(
            chatName = if (isIncognitoMode) "Grupo #${index + 1}" else name,
            lastBackup = System.currentTimeMillis() - (index * 3600000L),
            size = (index + 1) * 15 * 1024 * 1024L, // 15MB, 30MB, 45MB
            isValidated = index != 1
        )
    }
}

@Composable
private fun BackupListItem(
    chatName: String,
    lastBackup: Long,
    size: Long,
    isValidated: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = chatName,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${formatTimeAgo(lastBackup)} • ${formatBytes(size)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (isValidated) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.CheckCircle,
                    contentDescription = "Validado",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BiometricAuthDialog(
    onAuthenticated: () -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    LaunchedEffect(Unit) {
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Mostra prompt biométrico
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(
                    androidx.activity.ComponentActivity::class.java.cast(context),
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onAuthenticated()
                        }
                        
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                                onDismiss()
                            } else {
                                onError(errString.toString())
                            }
                        }
                    }
                )
                
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Desbloquear Modo Incógnito")
                    .setSubtitle("Use sua biometria para revelar identidades reais")
                    .setNegativeButtonText("Cancelar")
                    .build()
                
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // Biometria não disponível, permite desbloqueio alternativo
                onAuthenticated()
            }
        }
    }
}

// Helpers

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "agora"
        diff < 3600_000 -> "${diff / 60_000} min atrás"
        diff < 86400_000 -> "${diff / 3600_000} horas atrás"
        else -> "${diff / 86400_000} dias atrás"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun simulateHealthData(): BackupHealth {
    return BackupHealth(
        status = HealthStatus.GREEN,
        lastBackupTime = System.currentTimeMillis() - 7200000, // 2 horas atrás
        nextScheduledBackup = System.currentTimeMillis() + 14400000, // 4 horas
        totalBackupsCount = 47,
        storageUsedBytes = 2L * 1024 * 1024 * 1024, // 2GB
        storageAvailableBytes = 30L * 1024 * 1024 * 1024, // 30GB
        failedBackupsCount = 0,
        pendingPermissions = emptyList(),
        validatedBackupsCount = 45,
        dailyGrowthRateBytes = 50 * 1024 * 1024, // 50MB/dia
        estimatedDaysUntilFull = 600, // ~600 dias
        statusMessage = "Tudo sincronizado"
    )
}
