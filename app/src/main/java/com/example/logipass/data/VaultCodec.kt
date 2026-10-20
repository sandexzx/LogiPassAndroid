package com.example.logipass.data

import com.example.logipass.model.Vault
import com.example.logipass.security.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val vaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
object VaultCodec {
    fun encode(vault: Vault): ByteArray = vaultJson.encodeToString(vault).toByteArray(Charsets.UTF_8)
    fun decode(bytes: ByteArray): Vault = vaultJson.decodeFromString<Vault>(bytes.toString(Charsets.UTF_8)).also {
        require(it.version == 1)
        require(it.services.map { s -> s.id }.distinct().size == it.services.size)
        require(it.services.all { s -> s.id.isNotBlank() && s.credentials.all { c -> c.id.isNotBlank() } &&
            s.credentials.map { c -> c.id }.distinct().size == s.credentials.size })
    }
    fun seal(vault: Vault, dek: ByteArray): ByteArray {
        val plain = encode(vault)
        return try {
            val blob = Aead.seal(Aead.key(dek), plain, "logipass-vault-v1")
            byteArrayOf(1) + Aead.decode(blob.nonce) + Aead.decode(blob.ciphertext)
        } finally { plain.fill(0) }
    }
    fun open(bytes: ByteArray, dek: ByteArray): Vault {
        require(bytes.size >= 29 && bytes[0] == 1.toByte())
        val plain = Aead.open(Aead.key(dek), SealedBlob(Aead.encode(bytes.copyOfRange(1, 13)),
            Aead.encode(bytes.copyOfRange(13, bytes.size))), "logipass-vault-v1")
        return try { decode(plain) } finally { plain.fill(0) }
    }
}
