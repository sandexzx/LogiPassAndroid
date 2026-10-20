package com.example.logipass.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Aead {
    fun random(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
    fun key(bytes: ByteArray): SecretKey = SecretKeySpec(bytes, "AES")
    fun cipher(mode: Int, key: SecretKey, nonce: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(128, nonce)) }
    fun seal(key: SecretKey, plain: ByteArray, aad: String): SealedBlob {
        // Android Keystore requires provider-generated IVs for randomized encryption.
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
        require(cipher.iv.size == 12)
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return SealedBlob(encode(cipher.iv), encode(cipher.doFinal(plain)))
    }
    fun open(key: SecretKey, blob: SealedBlob, aad: String): ByteArray {
        val nonce = decode(blob.nonce)
        require(nonce.size == 12)
        val cipher = cipher(Cipher.DECRYPT_MODE, key, nonce)
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(decode(blob.ciphertext))
    }
}
