package com.whatsappbackup.premium.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whatsappbackup.premium.domain.model.ParsedMessage
import com.whatsappbackup.premium.domain.model.MessageType

/**
 * Chat Simulator - Visualizador de Linha do Tempo
 * Renderiza mensagens parseradas em interface idêntica ao WhatsApp
 */
@Composable
fun ChatSimulator(
    chatName: String,
    messages: List<ParsedMessage>,
    isIncognitoMode: Boolean = false,
    contactIndex: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFECE5DD)) // Cor de fundo estilo WhatsApp
    ) {
        // Header do chat
        ChatHeader(
            chatName = if (isIncognitoMode) "Contato #$contactIndex" else chatName,
            messageCount = messages.size
        )
        
        // Lista de mensagens
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages.size) { index ->
                val message = messages[index]
                MessageBubble(
                    message = message,
                    isIncognitoMode = isIncognitoMode,
                    contactIndex = contactIndex
                )
            }
        }
    }
}

/**
 * Header do Chat Simulator
 */
@Composable
private fun ChatHeader(
    chatName: String,
    messageCount: Int
) {
    Surface(
        color = Color(0xFF075E54), // Verde escuro estilo WhatsApp
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = chatName.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Informações
            Column {
                Text(
                    text = chatName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "$messageCount mensagens",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Balão de mensagem estilo WhatsApp
 */
@Composable
private fun MessageBubble(
    message: ParsedMessage,
    isIncognitoMode: Boolean,
    contactIndex: Int
) {
    val isFromMe = message.isFromMe
    val bubbleColor = if (isFromMe) {
        Color(0xFFDCF8C6) // Verde claro para mensagens enviadas
    } else {
        Color.White // Branco para mensagens recebidas
    }
    
    val senderName = if (isIncognitoMode) {
        message.getObfuscatedSender(contactIndex)
    } else {
        message.sender
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 4.dp,
                bottomStart = if (isFromMe) 4.dp else 0.dp,
                bottomEnd = if (isFromMe) 0.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(8.dp)
            ) {
                // Nome do remetente (apenas para grupos)
                if (!message.isFromMe && message.sender.isNotBlank()) {
                    Text(
                        text = senderName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE5426E), // Cor típica de nome no WhatsApp
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                
                // Conteúdo da mensagem
                when (message.messageType) {
                    MessageType.TEXT -> {
                        Text(
                            text = message.content,
                            fontSize = 15.sp,
                            color = Color.Black
                        )
                    }
                    MessageType.IMAGE -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📷 ", fontSize = 20.sp)
                            Text(
                                text = message.content.ifBlank { "Foto" },
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }
                    }
                    MessageType.VIDEO -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎥 ", fontSize = 20.sp)
                            Text(
                                text = message.content.ifBlank { "Vídeo" },
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }
                    }
                    MessageType.AUDIO -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎤 ", fontSize = 20.sp)
                            Text(
                                text = message.content.ifBlank { "Áudio" },
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }
                    }
                    MessageType.DOCUMENT -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📄 ", fontSize = 20.sp)
                            Text(
                                text = message.content.ifBlank { "Documento" },
                                fontSize = 15.sp,
                                color = Color.Black
                            )
                        }
                    }
                    MessageType.SYSTEM -> {
                        Text(
                            text = message.content,
                            fontSize = 13.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }
                }
                
                // Timestamp
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = message.timeFormatted,
                        fontSize = 11.sp,
                        color = Color.Gray.copy(alpha = 0.7f)
                    )
                    
                    // Check de leitura (apenas para mensagens enviadas)
                    if (isFromMe) {
                        Text(
                            text = " ✓✓",
                            fontSize = 11.sp,
                            color = Color(0xFF34B7F1), // Azul de leitura
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
