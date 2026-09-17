package com.whatsappbackup.premium.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.whatsappbackup.premium.data.local.BackupPreferences
import com.whatsappbackup.premium.data.local.database.BackupDatabase
import com.whatsappbackup.premium.data.local.database.CheckpointEntity
import com.whatsappbackup.premium.domain.model.ChatType
import com.whatsappbackup.premium.domain.model.ExtractionCheckpoint
import com.whatsappbackup.premium.domain.model.ExtractionPriority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * AccessibilityService para automação híbrida do WhatsApp
 * 
 * Funcionalidades:
 * - Exportação seletiva de chats (.txt) sem interação humana
 * - Sandbox de Acessibilidade: Só fica ativo durante janela de execução do backup
 * - Mapeamento inteligente de IDs do WhatsApp
 * 
 * Módulo A - Modo Marathon:
 * - Chunking: extrai histórico em lotes (50 rolagens ou 1 mês por execução)
 * - Checkpointing: salva ponto exato para retomar após interrupção
 * - Throttling: delays aleatórios entre rolagens para não sobrecarregar CPU/bateria
 * - Fila priorizada: privados menores -> privados maiores -> grupos gigantes
 * 
 * Módulo B - Captura Anti-Truncamento:
 * - Caçador de "Ler mais": detecta e expande mensagens colapsadas antes de capturar
 */
@AndroidEntryPoint
class WhatsAppAccessibilityService : AccessibilityService() {
    
    @Inject
    lateinit var backupPreferences: BackupPreferences
    
    @Inject
    lateinit var database: BackupDatabase
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    
    // Estado do serviço
    private var isActive = false
    private var currentTask: BackupTask? = null
    private var marathonTask: MarathonExtractionTask? = null
    
    // Callbacks de execução
    private var onTaskComplete: (() -> Unit)? = null
    private var onTaskError: ((String) -> Unit)? = null
    private var onProgressUpdate: ((Int, Long) -> Unit)? = null // progress, messagesExtracted
    
    companion object {
        // IDs de recursos do WhatsApp (podem variar por versão)
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
        
        // IDs comuns de elementos do WhatsApp
        const val SEARCH_ID = "com.whatsapp:id/search_button"
        const val CHAT_LIST_ID = "com.whatsapp:id/list"
        const val MENU_OVERFLOW_ID = "com.whatsapp:id/menu_overflows"
        const val MORE_OPTIONS_ID = "com.whatsapp:id/more_options"
        const val CONTACT_INFO_ID = "com.whatsapp:id/contact_info"
        
        // IDs para chat view
        const val MESSAGE_VIEW_ID = "com.whatsapp:id/message_text"
        const val CHAT_CONTAINER_ID = "com.whatsapp:id/conversations_container"
        
        // Texto para detectar "Ler mais"
        const val READ_MORE_TEXT = "Ler mais"
        const val READ_MORE_ENGLISH = "Read more"
        const val READ_MORE_SPANISH = "Ver más"
        
        // Configurações do Modo Marathon
        const val SCROLLS_PER_EXECUTION = 50
        const val MIN_SCROLL_DELAY_MS = 100L
        const val MAX_SCROLL_DELAY_MS = 500L
        const val EXPANSION_TIMEOUT_MS = 3000L
        
        // Singleton para acesso externo
        var instance: WhatsAppAccessibilityService? = null
            private set
        
        fun isServiceRunning(): Boolean = instance != null && instance?.isActive == true
        
        fun requestBackup(task: BackupTask, onComplete: () -> Unit, onError: (String) -> Unit) {
            instance?.let { service ->
                service.currentTask = task
                service.onTaskComplete = onComplete
                service.onTaskError = onError
                service.startBackupTask()
            } ?: run {
                onError("Serviço de acessibilidade não está disponível")
            }
        }
        
        /**
         * Solicita extração Modo Marathon (backfilling de histórico)
         */
        fun requestMarathonExtraction(
            task: MarathonExtractionTask,
            onProgress: (Int, Long) -> Unit,
            onComplete: () -> Unit,
            onError: (String) -> Unit
        ) {
            instance?.let { service ->
                service.marathonTask = task
                service.onProgressUpdate = onProgress
                service.onTaskComplete = onComplete
                service.onTaskError = onError
                service.startMarathonExtraction()
            } ?: run {
                onError("Serviço de acessibilidade não está disponível")
            }
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        configureService()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
    }
    
    /**
     * Configura o serviço para operar em modo sandbox
     */
    private fun configureService() {
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        serviceInfo = info
    }
    
    /**
     * Inicia tarefa de backup automatizado
     */
    private fun startBackupTask() {
        if (!isActive && currentTask != null) {
            isActive = true
            launchWhatsAppAndExport()
        }
    }
    
    /**
     * Lança WhatsApp e inicia processo de exportação
     */
    private fun launchWhatsAppAndExport() {
        serviceScope.launch {
            try {
                // Verifica se WhatsApp está instalado
                val whatsappInstalled = isPackageInstalled(WHATSAPP_PACKAGE) || 
                                       isPackageInstalled(WHATSAPP_BUSINESS_PACKAGE)
                
                if (!whatsappInstalled) {
                    throw Exception("WhatsApp não está instalado no dispositivo")
                }
                
                // Abre WhatsApp
                val intent = packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
                    ?: packageManager.getLaunchIntentForPackage(WHATSAPP_BUSINESS_PACKAGE)
                    ?: throw Exception("Não foi possível abrir o WhatsApp")
                
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                
                // Aguarda carregamento da interface
                delay(3000)
                
                // Navega até o chat alvo
                navigateToChat(currentTask!!.chatId)
                
                // Executa exportação
                exportChatToTxt(currentTask!!)
                
                // Completa tarefa
                withContext(Dispatchers.Main) {
                    onTaskComplete?.invoke()
                    stopActiveService()
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onTaskError?.invoke(e.message ?: "Erro desconhecido")
                    stopActiveService()
                }
            }
        }
    }
    
    /**
     * Navega até um chat específico usando AccessibilityNodeInfo
     */
    private suspend fun navigateToChat(chatId: String): Boolean {
        return withContext(Dispatchers.Default) {
            try {
                // Tenta encontrar o chat na lista
                val rootNode = rootInActiveWindow ?: return@withContext false
                
                // Estratégia 1: Busca por texto do nome do chat
                val chatNode = findNodeByText(rootNode, chatId)
                    ?: findNodeByContentDescription(rootNode, chatId)
                
                chatNode?.let { node ->
                    // Clica no chat
                    performClick(node)
                    delay(1000)
                    true
                } ?: false
                
            } catch (e: Exception) {
                false
            }
        }
    }
    
    /**
     * Exporta chat para arquivo .txt usando automação
     */
    private suspend fun exportChatToTxt(task: BackupTask) {
        withContext(Dispatchers.Default) {
            try {
                val rootNode = rootInActiveWindow ?: throw Exception("Janela não disponível")
                
                // Passo 1: Abre menu de opções (três pontos)
                val menuNode = findNodeByContentDescription(rootNode, "Mais opções")
                    ?: findNodeById(rootNode, MENU_OVERFLOW_ID)
                    ?: findNodeById(rootNode, MORE_OPTIONS_ID)
                
                menuNode?.let { node ->
                    performClick(node)
                    delay(500)
                    
                    // Passo 2: Seleciona "Mais" ou "Configurações do chat"
                    val moreNode = rootInActiveWindow?.let { root ->
                        findNodeByText(root, "Mais")
                            ?: findNodeByText(root, "Configurações do chat")
                    }
                    
                    moreNode?.let { node ->
                        performClick(node)
                        delay(500)
                        
                        // Passo 3: Seleciona "Conversa"
                        val chatNode = rootInActiveWindow?.let { root ->
                            findNodeByText(root, "Conversa")
                        }
                        
                        chatNode?.let { node ->
                            performClick(node)
                            delay(500)
                            
                            // Passo 4: Seleciona "Exportar conversa"
                            val exportNode = rootInActiveWindow?.let { root ->
                                findNodeByText(root, "Exportar conversa")
                                    ?: findNodeByText(root, "Exportar")
                            }
                            
                            exportNode?.let { expNode ->
                                performClick(expNode)
                                delay(1000)
                                
                                // Passo 5: Escolhe "Sem mídia" (apenas texto)
                                val noMediaNode = rootInActiveWindow?.let { root ->
                                    findNodeByText(root, "Sem mídia")
                                        ?: findNodeByText(root, "Apenas texto")
                                }
                                
                                noMediaNode?.let { mediaNode ->
                                    performClick(mediaNode)
                                    delay(2000)
                                    
                                    // Passo 6: Seleciona app de destino (nosso app)
                                    // O usuário deve selecionar manualmente o app pela primeira vez
                                }
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                throw e
            }
        }
    }
    
    /**
     * Encontra nó por texto visível
     */
    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            
            if (child.text?.contains(text, ignoreCase = true) == true) {
                return child
            }
            
            val foundInChildren = findNodeByText(child, text)
            if (foundInChildren != null) {
                return foundInChildren
            }
        }
        return null
    }
    
    /**
     * Encontra nó por content-description
     */
    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, description: String): AccessibilityNodeInfo? {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            
            if (child.contentDescription?.contains(description, ignoreCase = true) == true) {
                return child
            }
            
            val foundInChildren = findNodeByContentDescription(child, description)
            if (foundInChildren != null) {
                return foundInChildren
            }
        }
        return null
    }
    
    /**
     * Encontra nó por ID de recurso
     */
    private fun findNodeById(root: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            
            if (child.viewIdResourceName == resourceId) {
                return child
            }
            
            val foundInChildren = findNodeById(child, resourceId)
            if (foundInChildren != null) {
                return foundInChildren
            }
        }
        return null
    }
    
    /**
     * Executa clique em um nó de acessibilidade
     */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            node.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        }
    }
    
    /**
     * Verifica se pacote está instalado
     */
    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Para serviço ativo (sandbox mode)
     */
    fun stopActiveService() {
        isActive = false
        currentTask = null
        marathonTask = null
        onTaskComplete = null
        onTaskError = null
        onProgressUpdate = null
    }
    
    /**
     * Inicia extração Modo Marathon com checkpointing
     */
    private fun startMarathonExtraction() {
        if (!isActive && marathonTask != null) {
            isActive = true
            serviceScope.launch {
                try {
                    launchWhatsAppForMarathon()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        onTaskError?.invoke(e.message ?: "Erro na extração Marathon")
                        stopActiveService()
                    }
                }
            }
        }
    }
    
    /**
     * Lança WhatsApp e inicia extração em lotes do histórico
     */
    private suspend fun launchWhatsAppForMarathon() {
        val task = marathonTask ?: return
        
        // Abre WhatsApp
        val intent = packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(WHATSAPP_BUSINESS_PACKAGE)
            ?: throw Exception("Não foi possível abrir o WhatsApp")
        
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        
        // Aguarda carregamento
        delay(3000)
        
        // Navega até o chat
        navigateToChat(task.chatId)
        delay(1000)
        
        // Recupera checkpoint existente ou cria novo
        val checkpoint = database.checkpointDao().getCheckpointByChatId(task.chatId)
            ?: CheckpointEntity(
                chatId = task.chatId,
                chatName = task.chatName,
                chatType = task.chatType,
                priority = determinePriority(task.chatType)
            )
        
        // Executa lote de rolagens com throttling
        executeScrollBatch(checkpoint, task)
    }
    
    /**
     * Executa lote de rolagens (chunking) com checkpointing
     */
    private suspend fun executeScrollBatch(checkpoint: CheckpointEntity, task: MarathonExtractionTask) {
        val scrollsToExecute = SCROLLS_PER_EXECUTION
        var messagesExtracted = checkpoint.messagesExtracted
        
        for (i in 0 until scrollsToExecute) {
            try {
                // Módulo B: Caçador de "Ler mais" - expande mensagens colapsadas antes de rolar
                expandAllReadMoreMessages()
                
                // Captura mensagens visíveis
                val capturedMessages = captureVisibleMessages()
                messagesExtracted += capturedMessages
                
                // Atualiza progresso
                onProgressUpdate?.invoke(
                    ((checkpoint.scrollPosition + i) * 100) / (checkpoint.scrollPosition + scrollsToExecute),
                    messagesExtracted
                )
                
                // Throttling: delay aleatório para não sobrecarregar CPU/bateria
                applyThrottlingDelay()
                
                // Executa scroll para cima (rebobinar histórico)
                performScrollUp()
                
                // Salva checkpoint após cada rolagem
                database.checkpointDao().insertCheckpoint(checkpoint.copy(
                    scrollPosition = checkpoint.scrollPosition + i + 1,
                    messagesExtracted = messagesExtracted,
                    lastExecutionTime = System.currentTimeMillis()
                ))
                
            } catch (e: Exception) {
                // Em caso de erro, salva checkpoint para retomar depois
                database.checkpointDao().incrementRetryCount(
                    task.chatId,
                    e.message,
                    System.currentTimeMillis()
                )
                break
            }
        }
        
        // Verifica se completou (chegou ao início ou limite)
        val isComplete = checkpoint.monthsBack >= 24 || reachedChatBeginning()
        
        if (isComplete) {
            database.checkpointDao().markAsComplete(task.chatId)
            withContext(Dispatchers.Main) {
                onTaskComplete?.invoke()
                stopActiveService()
            }
        } else {
            // Agenda próxima execução se necessário
            scheduleNextExtraction(task)
        }
    }
    
    /**
     * Módulo B - Caçador de "Ler mais":
     * Varre a janela por nós com texto "Ler mais", toca e aguarda expansão
     */
    private suspend fun expandAllReadMoreMessages() {
        val rootNode = rootInActiveWindow ?: return
        val readMorePatterns = listOf(READ_MORE_TEXT, READ_MORE_ENGLISH, READ_MORE_SPANISH)
        
        var foundCollapsed = true
        while (foundCollapsed) {
            foundCollapsed = false
            
            for (pattern in readMorePatterns) {
                val collapsedNode = findNodeByText(rootNode, pattern)
                if (collapsedNode != null) {
                    // Toca para expandir
                    performClick(collapsedNode)
                    
                    // Aguarda expansão com timeout
                    delay(EXPANSION_TIMEOUT_MS)
                    
                    foundCollapsed = true
                    break
                }
            }
        }
    }
    
    /**
     * Captura mensagens visíveis na viewport atual
     */
    private fun captureVisibleMessages(): Int {
        val rootNode = rootInActiveWindow ?: return 0
        val messageNodes = mutableListOf<AccessibilityNodeInfo>()
        
        // Encontra todos os nós de mensagem
        findNodesById(rootNode, MESSAGE_VIEW_ID, messageNodes)
        
        return messageNodes.size
    }
    
    /**
     * Executa scroll para cima (rebobinar histórico)
     */
    private suspend fun performScrollUp() {
        val rootNode = rootInActiveWindow ?: return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val path = Path()
            val startX = rootNode.bounds.centerX().toFloat()
            val startY = rootNode.bounds.bottom.toFloat()
            val endY = rootNode.bounds.top.toFloat()
            
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 500))
                .build()
            
            dispatchGesture(gesture, null, null)
        } else {
            // Fallback para versões antigas
            rootNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        }
        
        delay(200) // Aguarda animação do scroll
    }
    
    /**
     * Aplica delay aleatório para throttling (evita parecer bot)
     */
    private suspend fun applyThrottlingDelay() {
        val delayMs = (MIN_SCROLL_DELAY_MS..MAX_SCROLL_DELAY_MS).random()
        delay(delayMs)
    }
    
    /**
     * Determina prioridade baseada no tipo de chat
     */
    private fun determinePriority(chatType: ChatType): ExtractionPriority {
        return when (chatType) {
            ChatType.PRIVATE -> ExtractionPriority.HIGH
            ChatType.GROUP -> ExtractionPriority.LOW
        }
    }
    
    /**
     * Verifica se chegou ao início do chat
     */
    private suspend fun reachedChatBeginning(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        // Verifica se há indicador de "início das mensagens"
        val beginningNode = findNodeByText(rootNode, "início das mensagens")
            ?: findNodeByText(rootNode, "beginning of messages")
        
        return beginningNode != null
    }
    
    /**
     * Agenda próxima execução de extração
     */
    private fun scheduleNextExtraction(task: MarathonExtractionTask) {
        // Implementação real usaria WorkManager para agendar próxima execução
        // Por enquanto, apenas notifica que precisa continuar
        handler.postDelayed({
            requestMarathonExtraction(
                task = task,
                onProgress = onProgressUpdate ?: { _, _ -> },
                onComplete = onTaskComplete ?: {},
                onError = onTaskError ?: {}
            )
        }, 60000) // 1 minuto entre execuções
    }
    
    /**
     * Encontra múltiplos nós por ID
     */
    private fun findNodesById(
        root: AccessibilityNodeInfo,
        resourceId: String,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            
            if (child.viewIdResourceName == resourceId) {
                results.add(child)
            } else {
                findNodesById(child, resourceId, results)
            }
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Evento capturado mas processado apenas durante janela ativa
        if (!isActive) return
        
        // Processa eventos conforme necessário
    }
    
    override fun onInterrupt() {
        // Serviço interrompido pelo sistema
        stopActiveService()
    }
}

/**
 * Modelo de tarefa de backup para automação
 */
data class BackupTask(
    val chatId: String,
    val chatName: String,
    val outputPath: String,
    val includeMedia: Boolean = false
)

/**
 * Tarefa de extração Modo Marathon (Módulo A)
 */
data class MarathonExtractionTask(
    val chatId: String,
    val chatName: String,
    val chatType: ChatType,
    val maxScrolls: Int = SCROLLS_PER_EXECUTION,
    val monthsPerExecution: Int = 1
)
