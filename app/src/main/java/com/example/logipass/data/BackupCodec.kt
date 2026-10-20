package com.example.logipass.data

import com.example.logipass.model.Vault
import com.example.logipass.security.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import javax.crypto.AEADBadTagException

sealed interface ImportProbe {
    data class Legacy(val vault: Vault) : ImportProbe
    data class Encrypted(val envelope: String) : ImportProbe
    data object Invalid : ImportProbe
}
sealed interface DecryptResult {
    data class Success(val vault: Vault) : DecryptResult
    data object WrongPassword : DecryptResult
    data object Invalid : DecryptResult
}
@Serializable private data class ExportEnvelope(
    val format: String = "logipass-export", val version: Int = 1,
    val kdf: KdfParams, val nonce: String, val ciphertext: String,
)
class BackupCodec(private val kdf: PasswordKdf = PasswordKdf()) {
    fun encode(vault: Vault, password: String): String {
        val params = kdf.params()
        val key = kdf.derive(password, params)
        val plain = VaultCodec.encode(vault)
        return try {
            val blob = Aead.seal(Aead.key(key), plain, "logipass-export-v1")
            vaultJson.encodeToString(ExportEnvelope(kdf = params, nonce = blob.nonce, ciphertext = blob.ciphertext))
        } finally { key.fill(0); plain.fill(0) }
    }
    fun probe(text: String): ImportProbe = try {
        when (val element = vaultJson.parseToJsonElement(text)) {
            is JsonArray -> ImportProbe.Legacy(LegacyFormat.parse(text))
            is JsonObject -> {
                val env = vaultJson.decodeFromString<ExportEnvelope>(text)
                if (env.format == "logipass-export" && env.version == 1) ImportProbe.Encrypted(text) else ImportProbe.Invalid
            }
            else -> ImportProbe.Invalid
        }
    } catch (_: IllegalArgumentException) { ImportProbe.Invalid }
    fun decrypt(probe: ImportProbe.Encrypted, password: String): DecryptResult = try {
        val env = vaultJson.decodeFromString<ExportEnvelope>(probe.envelope)
        require(env.version == 1 && env.format == "logipass-export")
        val key = kdf.derive(password, env.kdf)
        val plain = try { Aead.open(Aead.key(key), SealedBlob(env.nonce, env.ciphertext), "logipass-export-v1") } finally { key.fill(0) }
        try { DecryptResult.Success(VaultCodec.decode(plain)) } finally { plain.fill(0) }
    } catch (_: AEADBadTagException) { DecryptResult.WrongPassword }
    catch (_: IllegalArgumentException) { DecryptResult.Invalid }
}
