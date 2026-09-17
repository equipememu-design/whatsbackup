package com.whatsappbackup.premium.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Criptografia AES-256 para backups locais
 * Implementa criptografia simétrica com chave armazenada no Android Keystore
 */
class BackupCryptoManager {
    
    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "whatsapp_backup_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply {
        load(null)
    }
    
    init {
        generateKeyIfNotExists()
    }
    
    /**
     * Gera chave AES-256 se não existir
     */
    private fun generateKeyIfNotExists() {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }
    
    /**
     * Criptografa arquivo de backup usando AES-256-GCM
     * @param inputFile Arquivo original (.crypt ou .txt)
     * @param outputFile Arquivo criptografado de saída
     */
    fun encryptFile(inputFile: File, outputFile: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        
        val iv = cipher.iv
        
        FileOutputStream(outputFile).use { outputStream ->
            // Escreve IV no início do arquivo
            outputStream.write(iv)
            
            FileInputStream(inputFile).use { inputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val encryptedBytes = cipher.update(buffer, 0, bytesRead)
                    if (encryptedBytes != null) {
                        outputStream.write(encryptedBytes)
                    }
                }
                
                // Escreve bytes finais (tag de autenticação)
                val finalBytes = cipher.doFinal()
                if (finalBytes.isNotEmpty()) {
                    outputStream.write(finalBytes)
                }
            }
        }
        
        // Deleta arquivo original após criptografia bem-sucedida
        inputFile.delete()
    }
    
    /**
     * Descriptografa arquivo de backup
     * @param inputFile Arquivo criptografado
     * @param outputFile Arquivo descriptografado de saída
     */
    fun decryptFile(inputFile: File, outputFile: File) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        FileInputStream(inputFile).use { inputStream ->
            // Lê IV do início do arquivo
            val iv = ByteArray(GCM_IV_LENGTH)
            inputStream.read(iv)
            
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            
            FileOutputStream(outputFile).use { outputStream ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    val decryptedBytes = cipher.update(buffer, 0, bytesRead)
                    if (decryptedBytes != null) {
                        outputStream.write(decryptedBytes)
                    }
                }
                
                // Processa bytes finais
                val finalBytes = cipher.doFinal()
                if (finalBytes.isNotEmpty()) {
                    outputStream.write(finalBytes)
                }
            }
        }
    }
    
    /**
     * Calcula checksum SHA-256 para validação de integridade
     */
    fun calculateChecksum(file: File): String {
        val messageDigest = java.security.MessageDigest.getInstance("SHA-256")
        
        FileInputStream(file).use { inputStream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                messageDigest.update(buffer, 0, bytesRead)
            }
        }
        
        return Base64.getEncoder().encodeToString(messageDigest.digest())
    }
    
    /**
     * Verifica se arquivo criptografado está íntegro
     */
    fun verifyFileIntegrity(encryptedFile: File, expectedChecksum: String): Boolean {
        return try {
            val tempFile = File.createTempFile("decrypt_verify_", ".tmp")
            try {
                decryptFile(encryptedFile, tempFile)
                val actualChecksum = calculateChecksum(tempFile)
                actualChecksum == expectedChecksum
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            false
        }
    }
}
