package com.example.logipass.security

import com.example.logipass.data.vaultJson
import kotlinx.serialization.encodeToString

interface KeyWrapper {
    fun seal(plain: ByteArray): SealedBlob
    fun open(blob: SealedBlob): ByteArray
}
class VaultKeyManager(private val device: KeyWrapper, private val kdf: PasswordKdf = PasswordKdf()) {
    fun wrap(password: String, dek: ByteArray, biometric: SealedBlob? = null): KeyFile {
        val params = kdf.params()
        val kek = kdf.derive(password, params)
        try {
            val inner = Aead.seal(Aead.key(kek), dek, "logipass-dek-pw")
            return KeyFile(kdf = params, passwordWrapped = device.seal(vaultJson.encodeToString(inner).toByteArray()), biometricWrapped = biometric)
        } finally { kek.fill(0) }
    }
    fun unwrap(password: String, file: KeyFile): ByteArray {
        require(file.version == 1)
        val bytes = device.open(file.passwordWrapped)
        val inner = try { vaultJson.decodeFromString<SealedBlob>(bytes.toString(Charsets.UTF_8)) } finally { bytes.fill(0) }
        val kek = kdf.derive(password, file.kdf)
        return try { Aead.open(Aead.key(kek), inner, "logipass-dek-pw").also { require(it.size == 32) } }
        finally { kek.fill(0) }
    }
}
