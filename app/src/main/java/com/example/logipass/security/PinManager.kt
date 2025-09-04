package com.example.logipass.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

class PinManager(private val context: Context) {
    private val keyAlias = "LogiPassPinKey"
    private val prefs: SharedPreferences = context.getSharedPreferences("LogiPassSecure", Context.MODE_PRIVATE)
    
    init {
        generateKey()
    }
    
    private fun generateKey() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            
            if (!keyStore.containsAlias(keyAlias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .setUserAuthenticationRequired(false)
                    .build()
                
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            keyStore.getKey(keyAlias, null) as SecretKey
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    fun savePin(pin: String): Boolean {
        return try {
            val secretKey = getSecretKey() ?: return false
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            
            val hashedPin = hashPin(pin)
            val encryptedPin = cipher.doFinal(hashedPin.toByteArray())
            val iv = cipher.iv
            
            val encryptedPinBase64 = Base64.encodeToString(encryptedPin, Base64.DEFAULT)
            val ivBase64 = Base64.encodeToString(iv, Base64.DEFAULT)
            
            prefs.edit()
                .putString("encrypted_pin", encryptedPinBase64)
                .putString("pin_iv", ivBase64)
                .putBoolean("pin_set", true)
                .apply()
            
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun verifyPin(pin: String): Boolean {
        return try {
            val secretKey = getSecretKey() ?: return false
            val encryptedPinBase64 = prefs.getString("encrypted_pin", null) ?: return false
            val ivBase64 = prefs.getString("pin_iv", null) ?: return false
            
            val encryptedPin = Base64.decode(encryptedPinBase64, Base64.DEFAULT)
            val iv = Base64.decode(ivBase64, Base64.DEFAULT)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            
            val decryptedPin = String(cipher.doFinal(encryptedPin))
            val hashedInputPin = hashPin(pin)
            
            decryptedPin == hashedInputPin
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    fun isPinSet(): Boolean {
        return prefs.getBoolean("pin_set", false)
    }
    
    fun clearPin() {
        prefs.edit()
            .remove("encrypted_pin")
            .remove("pin_iv")
            .putBoolean("pin_set", false)
            .apply()
    }
    
    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(pin.toByteArray())
        return Base64.encodeToString(hash, Base64.DEFAULT)
    }
}