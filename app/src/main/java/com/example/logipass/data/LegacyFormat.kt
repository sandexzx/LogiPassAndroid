package com.example.logipass.data

import com.example.logipass.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import java.util.UUID

@Serializable private data class LegacyService(val service: String? = null, val description: String? = null, val credentials: List<LegacyCredential>? = null)
@Serializable private data class LegacyCredential(val username: String? = null, val password: String? = null, @SerialName("additional_info") val note: String? = null)
object LegacyFormat {
    fun parse(text: String): Vault {
        val entries = vaultJson.decodeFromString<List<LegacyService?>>(text)
        val now = System.currentTimeMillis()
        return Vault(services = entries.map { entry ->
            Service(UUID.randomUUID().toString(), entry?.service?.takeIf { it.isNotBlank() } ?: "Без названия",
                entry?.description.orEmpty(), entry?.credentials.orEmpty().map { c ->
                    Credential(UUID.randomUUID().toString(), c.username.orEmpty(), c.password.orEmpty(), c.note.orEmpty(), now)
                }, now)
        })
    }
}
