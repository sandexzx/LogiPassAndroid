package com.example.logipass

import com.example.logipass.data.*
import com.example.logipass.model.*
import com.example.logipass.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class SecureCoreTest {
    private val kdf = PasswordKdf(10)
    private val sample = Vault(services = listOf(Service("s", "ChatGPT", "Генеративная нейросеть",
        listOf(Credential("c", "user@example.com", "p1", "Запасная", 1)), 1)))
    @Test fun aead() {
        val key = Aead.key(Aead.random(32))
        val blob = Aead.seal(key, "secret".toByteArray(), "aad")
        assertEquals("secret", Aead.open(key, blob, "aad").decodeToString())
        assertThrows(AEADBadTagException::class.java) { Aead.open(key, blob, "wrong") }
        val bytes = Aead.decode(blob.ciphertext).also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertThrows(AEADBadTagException::class.java) { Aead.open(key, blob.copy(ciphertext = Aead.encode(bytes)), "aad") }
        val dek = Aead.random(32)
        assertEquals(sample, VaultCodec.open(VaultCodec.seal(sample, dek), dek))
    }
    @Test fun passwordWrapping() {
        val key = Aead.key(Aead.random(32))
        val fake = object : KeyWrapper {
            override fun seal(plain: ByteArray) = Aead.seal(key, plain, "device")
            override fun open(blob: SealedBlob) = Aead.open(key, blob, "device")
        }
        val manager = VaultKeyManager(fake, kdf)
        val dek = Aead.random(32)
        val file = manager.wrap("password", dek)
        assertArrayEquals(dek, manager.unwrap("password", file))
        assertThrows(AEADBadTagException::class.java) { manager.unwrap("bad", file) }
        val changed = manager.wrap("new password", dek, file.biometricWrapped)
        assertArrayEquals(dek, manager.unwrap("new password", changed))
    }
    @Test fun backup() {
        val codec = BackupCodec(kdf)
        val text = codec.encode(sample, "password")
        val probe = codec.probe(text) as ImportProbe.Encrypted
        assertEquals(DecryptResult.Success(sample), codec.decrypt(probe, "password"))
        assertEquals(DecryptResult.WrongPassword, codec.decrypt(probe, "bad"))
        assertEquals(ImportProbe.Invalid, codec.probe(text.replace("\"version\":1", "\"version\":99")))
        assertEquals(DecryptResult.Invalid, codec.decrypt(ImportProbe.Encrypted(text.replace("\"version\":1", "\"version\":99")), "password"))
        listOf("garbage", "{}", "null", "[", "42").forEach { assertEquals(ImportProbe.Invalid, codec.probe(it)) }
        assertEquals(DecryptResult.Invalid, codec.decrypt(ImportProbe.Encrypted("garbage"), "password"))
    }
    @Test fun legacy() {
        val codec = BackupCodec(kdf)
        val text = """[{"service":"ChatGPT","description":"Генеративная нейросеть","credentials":[{"username":"user@example.com","password":"p1","additional_info":"Запасная"}]}]"""
        val service = (codec.probe(text) as ImportProbe.Legacy).vault.services.single()
        assertEquals("ChatGPT", service.name)
        assertEquals("Генеративная нейросеть", service.description)
        assertEquals("Запасная", service.credentials.single().note)
        assertEquals("p1", service.credentials.single().password)
        assertEquals("user@example.com", service.credentials.single().username)
        assertTrue(service.id.isNotBlank())
        val nulls = LegacyFormat.parse("""[{"service":null,"description":null,"credentials":[{"username":null,"password":null,"additional_info":null}]},{"service":"  "},{}]""")
        assertEquals(3, nulls.services.size)
        assertTrue(nulls.services.all { it.name == "Без названия" && it.description == "" })
        assertEquals("", nulls.services.first().credentials.single().password)
        assertEquals("", nulls.services.first().credentials.single().username)
        assertEquals("", nulls.services.first().credentials.single().note)
        assertTrue(nulls.services.last().credentials.isEmpty())
        assertEquals(Vault(), LegacyFormat.parse("[]"))
        assertEquals(ImportProbe.Invalid, codec.probe("{\"service\":\"x\"}"))
    }
    @Test fun invalidKdfParameters() {
        assertThrows(IllegalArgumentException::class.java) { kdf.derive("x", kdf.params().copy(iterations = 0)) }
        assertThrows(IllegalArgumentException::class.java) { kdf.derive("x", kdf.params().copy(alg = "unknown")) }
        assertThrows(IllegalArgumentException::class.java) { kdf.derive("x", kdf.params().copy(salt = "AA==")) }
    }
    @Test fun throttle() {
        val expected = listOf(0L, 0, 0, 0, 30_000, 60_000, 300_000, 900_000, 3_600_000, 3_600_000, 3_600_000, 3_600_000)
        (1..12).forEach { assertEquals(expected[it - 1], UnlockThrottle.delayMillis(it)) }
    }
    private class MemoryStore : VaultStore {
        var bytes: ByteArray? = null
        var fail = false
        override fun read(dek: ByteArray): Vault = bytes?.let { VaultCodec.open(it, dek) } ?: Vault()
        override fun write(vault: Vault, dek: ByteArray) {
            if (fail) throw java.io.IOException("write failed")
            bytes = VaultCodec.seal(vault, dek)
        }
    }
    @Test fun repository() = runTest {
        val store = MemoryStore()
        val repository = VaultRepository(store)
        val key = Aead.random(32)
        repository.open(key)
        val a = repository.addService(" A ", " desc ")
        val b = repository.addService("B", "")
        val c = repository.addService("C", "")
        assertEquals(listOf("A", "B", "C"), repository.services.value.map { it.name })
        repository.updateService(a, " AA ", " D ")
        assertEquals("AA", repository.services.value.first().name)
        val deleted = repository.deleteService(b)!!
        repository.restoreService(deleted)
        assertEquals(listOf(a, b, c), repository.services.value.map { it.id })
        val one = repository.addCredential(a, " user ", " secret ", " note ")
        val two = repository.addCredential(a, "u2", "p2", "")
        assertEquals(" secret ", repository.services.value.first().credentials.first().password)
        assertEquals("user", repository.services.value.first().credentials.first().username)
        repository.updateCredential(a, one, " x ", " p ", " n ")
        val removed = repository.deleteCredential(a, one)!!
        repository.restoreCredential(removed)
        assertEquals(listOf(one, two), repository.services.value.first().credentials.map { it.id })
        assertEquals(" p ", repository.services.value.first().credentials.first().password)
        assertNull(repository.deleteCredential(a, "missing"))
        assertNull(repository.deleteService("missing"))
        val removedService = repository.deleteService(c)!!
        repository.restoreService(removedService.copy(index = 999))
        assertEquals(c, repository.services.value.last().id)
        repository.deleteService(c)
        repository.restoreService(removedService.copy(index = -10))
        assertEquals(c, repository.services.value.first().id)
        val removedCredential = repository.deleteCredential(a, two)!!
        repository.restoreCredential(removedCredential.copy(index = 999))
        assertEquals(two, repository.services.value.first { it.id == a }.credentials.last().id)
        repository.replaceAll(sample)
        assertEquals(sample.services, repository.services.value)
        repository.close()
        assertTrue(repository.services.value.isEmpty())
        repository.open(key)
        assertEquals(sample.services, repository.services.value)
        store.fail = true
        try { repository.addService("fails", ""); fail("Expected write exception") } catch (_: java.io.IOException) { }
    }
    @Test fun lockDuringWrite() = runTest {
        val started = java.util.concurrent.CountDownLatch(1)
        val proceed = java.util.concurrent.CountDownLatch(1)
        val key = Aead.random(32)
        val store = object : VaultStore {
            override fun read(dek: ByteArray) = Vault()
            override fun write(vault: Vault, dek: ByteArray) {
                started.countDown()
                check(proceed.await(5, java.util.concurrent.TimeUnit.SECONDS))
                assertArrayEquals(key, dek)
                assertEquals(vault, VaultCodec.open(VaultCodec.seal(vault, dek), dek))
            }
        }
        val repository = VaultRepository(store)
        repository.open(key)
        val write = async(Dispatchers.Default) { repository.addService("test", "") }
        withContext(Dispatchers.IO) { check(started.await(5, java.util.concurrent.TimeUnit.SECONDS)) }
        repository.close()
        assertTrue(repository.services.value.isEmpty())
        proceed.countDown()
        write.await()
        assertTrue(repository.services.value.isEmpty())
    }
}
