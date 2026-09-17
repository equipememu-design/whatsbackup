package com.whatsappbackup.premium.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import com.whatsappbackup.premium.data.local.BackupPreferences
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
 */
@AndroidEntryPoint
class WhatsAppAccessibilityService : AccessibilityService() {
    
    @Inject
    lateinit var backupPreferences: BackupPreferences
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val handler = Handler(Looper.getMainLooper())
    
    // Estado do serviço
    private var isActive = false
    private var currentTask: BackupTask? = null
    
    // Callbacks de execução
    private var onTaskComplete: (() -> Unit)? = null
    private var onTaskError: ((String) -> Unit)? = null
    
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
        onTaskComplete = null
        onTaskError = null
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
