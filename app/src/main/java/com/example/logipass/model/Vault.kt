package com.example.logipass.model

import kotlinx.serialization.Serializable

@Serializable data class Vault(val version: Int = 1, val services: List<Service> = emptyList())
@Serializable data class Service(
    val id: String, val name: String, val description: String = "",
    val credentials: List<Credential> = emptyList(), val updatedAt: Long,
)
@Serializable data class Credential(
    val id: String, val username: String, val password: String,
    val note: String = "", val updatedAt: Long,
)
