package com.whatsappbackup.premium.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsappbackup.premium.domain.model.HealthStatus

/**
 * Dashboard de Saúde (Semáforo)
 * Componente visual que mostra status do backup com cores semafóricas
 */
@Composable
fun HealthDashboard(
    status: HealthStatus,
    lastBackupTime: Long?,
    nextScheduledBackup: Long?,
    storageUsedBytes: Long,
    storageAvailableBytes: Long,
    validatedBackupsCount: Int,
    estimatedDaysUntilFull: Int,
    modifier: Modifier = Modifier
) {
    var targetColor by remember { mutableStateOf(getStatusColor(status)) }
    val infiniteTransition = rememberInfiniteTransition()
    
    // Animação suave de pulso para o indicador de status
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        )
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .shadow(8.dp, shape = MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Indicador circular de status (Semáforo)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                targetColor.copy(alpha = pulseAlpha),
                                targetColor.copy(alpha = 0.3f)
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getStatusIcon(status),
                    contentDescription = "Status",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Título de status
            Text(
                text = getStatusText(status),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = targetColor
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Informações de último backup
            lastBackupTime?.let { time ->
                Text(
                    text = "Último backup: ${formatTimeAgo(time)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Próximo agendamento
            nextScheduledBackup?.let { time ->
                Text(
                    text = "Próximo: ${formatDateTime(time)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Radar Preditivo de Espaço
            if (estimatedDaysUntilFull > 0) {
                StoragePredictionCard(
                    usedBytes = storageUsedBytes,
                    availableBytes = storageAvailableBytes,
                    estimatedDaysUntilFull = estimatedDaysUntilFull
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Selo de validação
            if (validatedBackupsCount > 0) {
                ValidationSeal(validatedCount = validatedBackupsCount)
            }
        }
    }
}

/**
 * Cartão de previsão de armazenamento (Radar Preditivo)
 */
@Composable
private fun StoragePredictionCard(
    usedBytes: Long,
    availableBytes: Long,
    estimatedDaysUntilFull: Int
) {
    val totalBytes = usedBytes + availableBytes
    val usagePercent = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes * 100) else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📊 Radar Preditivo",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Barra de progresso de armazenamento
            LinearProgressIndicator(
                progress = { usagePercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = getStorageWarningColor(usagePercent),
                trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatBytes(usedBytes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = formatBytes(totalBytes),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Alerta preditivo
            if (estimatedDaysUntilFull <= 7) {
                AlertMessage(
                    text = "⚠️ Armazenamento cheio em $estimatedDaysUntilFull dias!",
                    isError = estimatedDaysUntilFull <= 3
                )
            } else {
                Text(
                    text = "Estimativa: $estimatedDaysUntilFull dias até lotar",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * Selo de "Backup Validado"
 */
@Composable
fun ValidationSeal(validatedCount: Int) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)
        ),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = androidx.compose.material.icons.Icons.Filled.Verified,
                contentDescription = "Validado",
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Backup Validado ✓ ($validatedCount backups)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

/**
 * Mensagem de alerta
 */
@Composable
private fun AlertMessage(text: String, isError: Boolean = false) {
    Surface(
        color = if (isError) Color(0xFFF44336).copy(alpha = 0.1f) else Color(0xFFFFC107).copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (isError) Color(0xFFF44336) else Color(0xFFFF9800),
            modifier = Modifier.padding(8.dp)
        )
    }
}

// Helpers

private fun getStatusColor(status: HealthStatus): Color {
    return when (status) {
        HealthStatus.GREEN -> Color(0xFF4CAF50)
        HealthStatus.YELLOW -> Color(0xFFFFC107)
        HealthStatus.RED -> Color(0xFFF44336)
    }
}

private fun getStatusIcon(status: HealthStatus): androidx.compose.ui.graphics.vector.ImageVector {
    return when (status) {
        HealthStatus.GREEN -> androidx.compose.material.icons.Icons.Filled.CheckCircle
        HealthStatus.YELLOW -> androidx.compose.material.icons.Icons.Filled.Warning
        HealthStatus.RED -> androidx.compose.material.icons.Icons.Filled.Error
    }
}

private fun getStatusText(status: HealthStatus): String {
    return when (status) {
        HealthStatus.GREEN -> "Tudo Sincronizado"
        HealthStatus.YELLOW -> "Atenção Necessária"
        HealthStatus.RED -> "Falha Crítica"
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "agora mesmo"
        diff < 3600_000 -> "${diff / 60_000} min atrás"
        diff < 86400_000 -> "${diff / 3600_000} horas atrás"
        else -> "${diff / 86400_000} dias atrás"
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault())
    sdf.timeZone = java.util.TimeZone.getDefault()
    return sdf.format(java.util.Date(timestamp))
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}

private fun getStorageWarningColor(usagePercent: Float): Color {
    return when {
        usagePercent < 70 -> Color(0xFF4CAF50)
        usagePercent < 90 -> Color(0xFFFFC107)
        else -> Color(0xFFF44336)
    }
}
