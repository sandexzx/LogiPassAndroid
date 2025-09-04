package com.example.logipass.model

data class Credential(
    val username: String,
    val password: String,
    val additional_info: String?
)

data class ServiceItem(
    val service: String,
    val description: String,
    val credentials: List<Credential>
)

