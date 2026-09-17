package com.whatsappbackup.premium.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsappbackup.premium.domain.model.Chat
import com.whatsappbackup.premium.domain.model.ChatType

/**
 * Gestão de Grupos Interativa
 * Lista de grupos com toggles individuais para:
 * - Incluir no Backup
 * - Ativar Limpeza Automática
 */
@Composable
fun GroupsManagementScreen(
    groups: List<Chat> = sampleGroups,
    onGroupSettingsChanged: (Chat, Boolean, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "👥 Gestão de Grupos",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // Instruções
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Privados têm backup infinito. Configure grupos individualmente.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        // Lista de grupos
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(groups.size) { index ->
                val group = groups[index]
                GroupListItem(
                    group = group,
                    groupIndex = index + 1,
                    onIncludeInBackupChanged = { include ->
                        onGroupSettingsChanged(group, include, group.autoCleanupEnabled)
                    },
                    onAutoCleanupChanged = { cleanup ->
                        onGroupSettingsChanged(group, group.includeInBackup, cleanup)
                    }
                )
            }
        }
    }
}

/**
 * Item de grupo na lista
 */
@Composable
private fun GroupListItem(
    group: Chat,
    groupIndex: Int,
    onIncludeInBackupChanged: (Boolean) -> Unit,
    onAutoCleanupChanged: (Boolean) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    
    Card(
        onClick = { isExpanded = !isExpanded },
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header do item
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar do grupo
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                Color(0xFF6200EE),
                                shape = MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = group.name.firstOrNull()?.uppercase() ?: "G",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Nome e info
                    Column {
                        Text(
                            text = group.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "${group.messageCount} msgs • ${formatBytes(group.backupSizeBytes)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Ícone de expansão
                Icon(
                    imageVector = if (isExpanded) 
                        androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp 
                    else 
                        androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Configurações expansíveis
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Divider()
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Toggle: Incluir no Backup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "✅ Incluir no Backup",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Grupo será incluído nos backups automáticos",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = group.includeInBackup,
                        onCheckedChange = onIncludeInBackupChanged
                    )
                }
                
                // Toggle: Limpeza Automática
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧹 Limpeza Automática",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Backups antigos serão removidos após ${group.cleanupDaysThreshold} dias",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = group.autoCleanupEnabled,
                        onCheckedChange = onAutoCleanupChanged
                    )
                }
                
                // Slider de dias para limpeza (apenas se habilitado)
                if (group.autoCleanupEnabled) {
                    var sliderPosition by remember { mutableFloatStateOf(group.cleanupDaysThreshold.toFloat()) }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Período de retenção:",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$sliderPosition dias",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = sliderPosition,
                            onValueChange = { sliderPosition = it },
                            valueRange = 7f..365f,
                            steps = 50,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Status do último backup
                group.lastBackupTime?.let { time ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "🕐 Último backup: ${formatTimeAgo(time)}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

// Dados de exemplo para demonstração
private val sampleGroups = listOf(
    Chat(
        id = "group_1",
        name = "Família Silva",
        type = ChatType.GROUP,
        lastMessageTime = System.currentTimeMillis() - 3600000,
        messageCount = 1523,
        backupSizeBytes = 45 * 1024 * 1024,
        includeInBackup = true,
        autoCleanupEnabled = false,
        lastBackupTime = System.currentTimeMillis() - 7200000
    ),
    Chat(
        id = "group_2",
        name = "Trabalho - Equipe",
        type = ChatType.GROUP,
        lastMessageTime = System.currentTimeMillis() - 1800000,
        messageCount = 3421,
        backupSizeBytes = 120 * 1024 * 1024,
        includeInBackup = true,
        autoCleanupEnabled = true,
        cleanupDaysThreshold = 30,
        lastBackupTime = System.currentTimeMillis() - 3600000
    ),
    Chat(
        id = "group_3",
        name = "Amigos Futebol",
        type = ChatType.GROUP,
        lastMessageTime = System.currentTimeMillis() - 86400000,
        messageCount = 892,
        backupSizeBytes = 25 * 1024 * 1024,
        includeInBackup = false,
        autoCleanupEnabled = false,
        lastBackupTime = null
    ),
    Chat(
        id = "group_4",
        name = "Condomínio",
        type = ChatType.GROUP,
        lastMessageTime = System.currentTimeMillis() - 43200000,
        messageCount = 2156,
        backupSizeBytes = 67 * 1024 * 1024,
        includeInBackup = true,
        autoCleanupEnabled = true,
        cleanupDaysThreshold = 90,
        lastBackupTime = System.currentTimeMillis() - 172800000
    )
)

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
