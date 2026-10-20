package com.example.logipass.security

import kotlinx.serialization.Serializable

@Serializable data class KeyFile(
    val version: Int = 1, val kdf: KdfParams, val passwordWrapped: SealedBlob,
    val biometricWrapped: SealedBlob? = null,
)
@Serializable data class KdfParams(val alg: String, val iterations: Int, val salt: String)
@Serializable data class SealedBlob(val nonce: String, val ciphertext: String)
