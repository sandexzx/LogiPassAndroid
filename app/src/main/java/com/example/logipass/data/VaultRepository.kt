package com.example.logipass.data

import com.example.logipass.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class DeletedService(val service: Service, val index: Int)
data class DeletedCredential(val serviceId: String, val credential: Credential, val index: Int)

class VaultRepository(private val store: VaultStore) {
    private val mutex = Mutex()
    private val guard = Any()
    private var dek: ByteArray? = null
    private var value = Vault()
    private val mutable = MutableStateFlow<List<Service>>(emptyList())
    val services: StateFlow<List<Service>> = mutable.asStateFlow()
    internal suspend fun open(key: ByteArray) = withContext(Dispatchers.IO) {
        mutex.withLock {
            attach(key, store.read(key))
        }
    }
    internal suspend fun load(key: ByteArray): Vault = withContext(Dispatchers.IO) {
        mutex.withLock { store.read(key) }
    }
    internal fun attach(key: ByteArray, loaded: Vault) = synchronized(guard) {
        dek?.fill(0); dek = key.copyOf(); value = loaded; mutable.value = loaded.services
    }
    internal fun close() = synchronized(guard) { dek?.fill(0); dek = null; value = Vault(); mutable.value = emptyList() }
    internal fun snapshot(): Vault? = synchronized(guard) { if (dek == null) null else value }
    private suspend fun <T> change(block: (Vault) -> Pair<Vault, T>): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val (next, result, key) = synchronized(guard) {
                val currentKey = checkNotNull(dek) { "Vault is locked" }
                val (next, result) = block(value)
                value = next
                mutable.value = next.services
                Triple(next, result, currentKey.copyOf())
            }
            try { store.write(next, key) } finally { key.fill(0) }
            result
        }
    }
    suspend fun addService(name: String, description: String): String = change { v ->
        val id = UUID.randomUUID().toString()
        v.copy(services = v.services + Service(id, name.trim(), description.trim(), updatedAt = System.currentTimeMillis())) to id
    }
    suspend fun updateService(id: String, name: String, description: String) { change { v ->
        v.copy(services = v.services.map { if (it.id == id) it.copy(name = name.trim(), description = description.trim(), updatedAt = System.currentTimeMillis()) else it }) to Unit
    } }
    suspend fun deleteService(id: String): DeletedService? = change { v ->
        val index = v.services.indexOfFirst { it.id == id }
        v.copy(services = v.services.filterNot { it.id == id }) to if (index < 0) null else DeletedService(v.services[index], index)
    }
    suspend fun restoreService(deleted: DeletedService) { change { v ->
        require(v.services.none { it.id == deleted.service.id })
        val list = v.services.toMutableList()
        list.add(deleted.index.coerceIn(0, list.size), deleted.service.copy(updatedAt = System.currentTimeMillis()))
        v.copy(services = list) to Unit
    } }
    private suspend fun <T> credentials(id: String, block: (List<Credential>) -> Pair<List<Credential>, T>): T = change { v ->
        val service = v.services.first { it.id == id }
        val (items, result) = block(service.credentials)
        v.copy(services = v.services.map { if (it.id == id) it.copy(credentials = items, updatedAt = System.currentTimeMillis()) else it }) to result
    }
    suspend fun addCredential(serviceId: String, username: String, password: String, note: String): String = credentials(serviceId) { list ->
        val id = UUID.randomUUID().toString()
        (list + Credential(id, username.trim(), password, note.trim(), System.currentTimeMillis())) to id
    }
    suspend fun updateCredential(serviceId: String, credentialId: String, username: String, password: String, note: String) { credentials(serviceId) { list ->
        list.map { if (it.id == credentialId) it.copy(username = username.trim(), password = password, note = note.trim(), updatedAt = System.currentTimeMillis()) else it } to Unit
    } }
    suspend fun deleteCredential(serviceId: String, credentialId: String): DeletedCredential? = credentials(serviceId) { list ->
        val index = list.indexOfFirst { it.id == credentialId }
        list.filterNot { it.id == credentialId } to if (index < 0) null else DeletedCredential(serviceId, list[index], index)
    }
    suspend fun restoreCredential(deleted: DeletedCredential) { credentials(deleted.serviceId) { items ->
        require(items.none { it.id == deleted.credential.id })
        val list = items.toMutableList()
        list.add(deleted.index.coerceIn(0, list.size), deleted.credential.copy(updatedAt = System.currentTimeMillis()))
        list to Unit
    } }
    suspend fun replaceAll(vault: Vault) { change { VaultCodec.decode(VaultCodec.encode(vault)) to Unit } }
    internal suspend fun awaitWrites() { mutex.withLock { } }
}
