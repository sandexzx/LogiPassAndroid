package com.example.logipass.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class KeystoreKeys : KeyWrapper {
    private val store get() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    fun delete(alias: String) { store.deleteEntry(alias) }
    fun has(alias: String) = store.containsAlias(alias)
    private fun get(alias: String): SecretKey = store.getKey(alias, null) as? SecretKey
        ?: throw java.security.UnrecoverableKeyException("Missing device-bound key")
    private fun generate(alias: String, biometric: Boolean, strongBox: Boolean = false): SecretKey {
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setIsStrongBoxBacked(strongBox)
        if (biometric) spec.setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run { init(spec.build()); generateKey() }
    }
    private fun device(): SecretKey {
        if (has(DEVICE)) return get(DEVICE)
        return try { generate(DEVICE, false, true) }
        catch (_: StrongBoxUnavailableException) { generate(DEVICE, false) }
    }
    override fun seal(plain: ByteArray): SealedBlob = Aead.seal(device(), plain, "logipass-dek-device")
    override fun open(blob: SealedBlob): ByteArray = Aead.open(get(DEVICE), blob, "logipass-dek-device")
    fun enrollCipher(): Cipher {
        delete(BIO)
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, generate(BIO, true)) }
    }
    fun unlockCipher(blob: SealedBlob): Cipher = Aead.cipher(Cipher.DECRYPT_MODE, get(BIO), Aead.decode(blob.nonce))
    companion object { const val DEVICE = "logipass_device"; const val BIO = "logipass_bio" }
}
