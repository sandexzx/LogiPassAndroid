package com.example.logipass.security

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PasswordKdf(val iterations: Int = 600_000, private val duration: (Long) -> Unit = {}) {
    fun params() = KdfParams("PBKDF2WithHmacSHA256", iterations, Aead.encode(Aead.random(16)))
    fun derive(password: String, params: KdfParams): ByteArray {
        require(params.alg == "PBKDF2WithHmacSHA256")
        require(params.iterations in 1..10_000_000)
        val salt = Aead.decode(params.salt)
        require(salt.size == 16)
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, params.iterations, 256)
        chars.fill('\u0000')
        val start = System.nanoTime()
        return try { SecretKeyFactory.getInstance(params.alg).generateSecret(spec).encoded }
        finally { spec.clearPassword(); duration((System.nanoTime() - start) / 1_000_000) }
    }
}
