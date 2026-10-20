package com.example.logipass.session

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.util.AtomicFile
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.lifecycle.*
import com.example.logipass.BuildConfig
import com.example.logipass.data.*
import com.example.logipass.model.Vault
import com.example.logipass.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.Cipher

sealed interface AuthState {
    data object Loading : AuthState
    data class NeedsSetup(val legacyServiceCount: Int?) : AuthState
    data class Locked(val biometricEnabled: Boolean, val lockedOutUntil: Long?) : AuthState
    data object Unlocked : AuthState
}
sealed interface UnlockResult {
    data object Success : UnlockResult
    data class WrongPassword(val failedAttempts: Int, val lockedOutUntil: Long?) : UnlockResult
    data class LockedOut(val until: Long) : UnlockResult
}
sealed interface BiometricCipher {
    data class Ready(val cipher: Cipher) : BiometricCipher
    data object Invalidated : BiometricCipher
    data object NotEnabled : BiometricCipher
}
enum class BiometricAvailability { Available, NoneEnrolled, Unavailable }
sealed interface ChangePasswordResult {
    data object Success : ChangePasswordResult
    data object TooShort : ChangePasswordResult
    data class WrongCurrent(val failedAttempts: Int, val lockedOutUntil: Long?) : ChangePasswordResult
    data class LockedOut(val until: Long) : ChangePasswordResult
}

class AuthManager(
    private val context: Context, private val vault: VaultRepository,
    private val keys: KeystoreKeys, private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val mutable = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = mutable.asStateFlow()
    private val bio = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = bio.asStateFlow()
    private val mutex = Mutex()
    private val guard = Any()
    private var epoch = 0L
    @Volatile private var file: KeyFile? = null
    private var dek: ByteArray? = null
    private val keyPath = File(context.noBackupFilesDir, "keys.json")
    private val vaultPath = File(context.noBackupFilesDir, "vault.bin")
    private val store = FileVaultStore(vaultPath)
    private val migration = LegacyMigration(context, keys)
    private var legacy: Vault? = null
    private val prefs = context.getSharedPreferences("logipass_auth", Context.MODE_PRIVATE)
    private val manager = VaultKeyManager(keys, PasswordKdf(duration = { millis ->
        if (BuildConfig.DEBUG) Log.d("LogiPass", "KDF duration: $millis ms")
    }))
    private var stoppedAt: Long? = null
    private var background = false
    private var suspended = false
    private var autoLock: Job? = null
    private var enroll: Cipher? = null
    private var decryptCipher: Cipher? = null
    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            mutex.withLock {
                withContext(Dispatchers.IO) {
                    // Recover AtomicFile backups before checking for an incomplete pair.
                    listOf(keyPath, vaultPath).forEach { p ->
                        if (File(p.path + ".bak").exists()) AtomicFile(p).openRead().close()
                    }
                    val orphaned = keyPath.exists() != vaultPath.exists()
                    if (orphaned) {
                        listOf(keyPath, vaultPath).filter { it.exists() }.forEach {
                            check(it.renameTo(File(it.path + ".corrupt-${System.currentTimeMillis()}")))
                        }
                    }
                    if (keyPath.exists()) {
                        try {
                            file = vaultJson.decodeFromString<KeyFile>(AtomicFile(keyPath).readFully().toString(Charsets.UTF_8))
                            require(file!!.version == 1)
                            bio.value = file!!.biometricWrapped != null
                            mutable.value = lockedState()
                        } catch (_: IllegalArgumentException) {
                            Log.w("LogiPass", "Invalid key file; encrypted files preserved as corrupt")
                            listOf(keyPath, vaultPath).forEach {
                                check(it.renameTo(File(it.path + ".corrupt-${System.currentTimeMillis()}")))
                            }
                            file = null
                            mutable.value = AuthState.NeedsSetup(null)
                        }
                    } else {
                        legacy = if (orphaned) null else migration.read()
                        mutable.value = AuthState.NeedsSetup(legacy?.services?.size)
                    }
                }
            }
        }
    }
    private fun until(): Long? = prefs.getLong("lockedOutUntil", 0).takeIf { it > System.currentTimeMillis() }
    private fun lockedState() = AuthState.Locked(bio.value, until())
    private fun reset() { check(prefs.edit().clear().commit()) }
    private fun failed(): UnlockResult.WrongPassword {
        val n = (prefs.getInt("failedAttempts", 0).coerceAtMost(Int.MAX_VALUE - 1) + 1)
        val delay = UnlockThrottle.delayMillis(n)
        val until = if (delay == 0L) null else System.currentTimeMillis() + delay
        check(prefs.edit().putInt("failedAttempts", n).putLong("lockedOutUntil", until ?: 0).commit())
        if (mutable.value is AuthState.Locked) mutable.value = lockedState()
        return UnlockResult.WrongPassword(n, until)
    }
    private suspend fun write(next: KeyFile) = withContext(NonCancellable) {
        withContext(Dispatchers.IO) { atomicWrite(keyPath, vaultJson.encodeToString(next).toByteArray()) }
        file = next
        bio.value = next.biometricWrapped != null
        if (mutable.value is AuthState.Locked) mutable.value = lockedState()
    }
    private fun token(): Long = synchronized(guard) { epoch }
    private suspend fun activate(key: ByteArray, token: Long): Boolean {
        val loaded = vault.load(key)
        return synchronized(guard) {
            if (epoch != token) false
            else { vault.attach(key, loaded); dek?.fill(0); dek = key.copyOf(); mutable.value = AuthState.Unlocked; true }
        }.also { if (it && background && !suspended) scheduleLock() }
    }
    fun biometricAvailability(): BiometricAvailability = when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
        else -> BiometricAvailability.Unavailable
    }
    suspend fun setup(password: String): Boolean = mutex.withLock {
        if (password.length < MIN_PASSWORD_LENGTH) return@withLock false
        check(mutable.value is AuthState.NeedsSetup)
        val token = token()
        val key = Aead.random(32)
        var committed = false
        try {
            val next = withContext(Dispatchers.Default) { manager.wrap(password, key) }
            withContext(Dispatchers.IO) {
                store.write(legacy ?: Vault(), key)
                check(store.read(key).services.size == (legacy?.services?.size ?: 0))
                atomicWrite(keyPath, vaultJson.encodeToString(next).toByteArray())
            }
            withContext(Dispatchers.IO) { reset() }
            file = next
            activate(key, token)
            committed = true
            withContext(NonCancellable + Dispatchers.IO) { if (legacy != null) migration.finish() }
            legacy = null
            true
        } catch (error: Throwable) {
            // Once cleanup starts, retain the verified encrypted pair even if cleanup fails.
            if (committed) throw error
            vault.close()
            synchronized(guard) { dek?.fill(0); dek = null }
            withContext(NonCancellable + Dispatchers.IO) { AtomicFile(keyPath).delete(); AtomicFile(vaultPath).delete() }
            file = null
            mutable.value = AuthState.NeedsSetup(legacy?.services?.size)
            throw error
        } finally { key.fill(0) }
    }
    private suspend fun passwordKey(password: String): ByteArray? = try {
        withContext(Dispatchers.Default) { manager.unwrap(password, checkNotNull(file)) }
    } catch (e: GeneralSecurityException) {
        Log.w("LogiPass", "Password unwrap failed: ${e.javaClass.simpleName}")
        null
    }
    suspend fun unlockWithPassword(password: String): UnlockResult = mutex.withLock {
        until()?.let { return@withLock UnlockResult.LockedOut(it) }
        check(file != null)
        val token = token()
        val key = passwordKey(password) ?: return@withLock withContext(Dispatchers.IO) { failed() }
        try {
            if (!activate(key, token)) throw CancellationException("Session locked during password unlock")
            withContext(Dispatchers.IO) { reset() }
            UnlockResult.Success
        } finally { key.fill(0) }
    }
    fun biometricUnlockCipher(): BiometricCipher {
        val blob = file?.biometricWrapped ?: return BiometricCipher.NotEnabled
        return try { BiometricCipher.Ready(keys.unlockCipher(blob).also { decryptCipher = it }) }
        catch (_: KeyPermanentlyInvalidatedException) { invalidateBio(); BiometricCipher.Invalidated }
        catch (_: java.security.UnrecoverableKeyException) { invalidateBio(); BiometricCipher.Invalidated }
    }
    private fun invalidateBio() {
        decryptCipher = null
        keys.delete(KeystoreKeys.BIO)
        bio.value = false
        file = file?.copy(biometricWrapped = null)
        if (mutable.value is AuthState.Locked) mutable.value = lockedState()
        scope.launch { mutex.withLock { file?.let { write(it.copy(biometricWrapped = null)) } } }
    }
    private fun biometricKey(cipher: Cipher): ByteArray? {
        if (cipher !== decryptCipher) return null
        decryptCipher = null
        val blob = file?.biometricWrapped ?: return null
        return try { cipher.doFinal(Aead.decode(blob.ciphertext)).also { require(it.size == 32) } }
        catch (_: GeneralSecurityException) { null }
    }
    suspend fun unlockWithBiometric(cipher: Cipher): Boolean = mutex.withLock {
        val token = token()
        val key = withContext(Dispatchers.Default) { biometricKey(cipher) } ?: return@withLock false
        try { activate(key, token).also { withContext(Dispatchers.IO) { reset() } } } finally { key.fill(0) }
    }
    fun biometricEnrollCipher(): Cipher {
        check(mutable.value == AuthState.Unlocked)
        return keys.enrollCipher().also { enroll = it }
    }
    suspend fun enableBiometric(cipher: Cipher) = mutex.withLock {
        check(cipher === enroll)
        enroll = null
        val key = synchronized(guard) { checkNotNull(dek).copyOf() }
        try {
            val blob = withContext(Dispatchers.Default) { SealedBlob(Aead.encode(cipher.iv), Aead.encode(cipher.doFinal(key))) }
            write(checkNotNull(file).copy(biometricWrapped = blob))
        } finally { key.fill(0) }
    }
    suspend fun disableBiometric() = mutex.withLock {
        check(mutable.value == AuthState.Unlocked)
        withContext(Dispatchers.IO) { keys.delete(KeystoreKeys.BIO) }
        write(checkNotNull(file).copy(biometricWrapped = null))
    }
    private suspend fun rewrap(key: ByteArray, password: String) {
        val next = withContext(Dispatchers.Default) { manager.wrap(password, key, file?.biometricWrapped) }
        write(next)
    }
    suspend fun changePassword(current: String, new: String): ChangePasswordResult = mutex.withLock {
        if (new.length < MIN_PASSWORD_LENGTH) return@withLock ChangePasswordResult.TooShort
        until()?.let { return@withLock ChangePasswordResult.LockedOut(it) }
        val key = passwordKey(current)
        if (key == null) {
            val fail = withContext(Dispatchers.IO) { failed() }
            return@withLock ChangePasswordResult.WrongCurrent(fail.failedAttempts, fail.lockedOutUntil)
        }
        try { rewrap(key, new); withContext(Dispatchers.IO) { reset() }; ChangePasswordResult.Success } finally { key.fill(0) }
    }
    suspend fun changePasswordWithBiometric(cipher: Cipher, new: String): ChangePasswordResult = mutex.withLock {
        if (new.length < MIN_PASSWORD_LENGTH) return@withLock ChangePasswordResult.TooShort
        val key = withContext(Dispatchers.Default) { biometricKey(cipher) }
            ?: throw IllegalStateException("Fresh authenticated biometric cipher required")
        try { rewrap(key, new); withContext(Dispatchers.IO) { reset() }; ChangePasswordResult.Success } finally { key.fill(0) }
    }
    fun lock() {
        synchronized(guard) {
            epoch++
            vault.close()
            dek?.fill(0); dek = null; enroll = null; decryptCipher = null
            if (file != null) mutable.value = lockedState()
        }
        autoLock?.cancel()
    }
    private fun scheduleLock() {
        autoLock?.cancel()
        val start = stoppedAt ?: SystemClock.elapsedRealtime().also { stoppedAt = it }
        autoLock = scope.launch { delay((60_000 - (SystemClock.elapsedRealtime() - start)).coerceAtLeast(0)); lock() }
    }
    override fun onStop(owner: LifecycleOwner) {
        background = true
        if (!suspended && mutable.value == AuthState.Unlocked) { stoppedAt = SystemClock.elapsedRealtime(); scheduleLock() }
    }
    override fun onStart(owner: LifecycleOwner) {
        background = false
        autoLock?.cancel()
        if (!suspended && stoppedAt?.let { SystemClock.elapsedRealtime() - it >= 60_000 } == true) lock()
        stoppedAt = null
    }
    fun suspendAutoLock() { suspended = true; autoLock?.cancel(); stoppedAt = null }
    fun resumeAutoLock() {
        suspended = false
        if (background && mutable.value == AuthState.Unlocked) { stoppedAt = SystemClock.elapsedRealtime(); scheduleLock() }
    }
    suspend fun wipeAll() = mutex.withLock {
        lock()
        vault.awaitWrites()
        withContext(Dispatchers.IO) {
            AtomicFile(vaultPath).delete(); AtomicFile(keyPath).delete()
            keys.delete(KeystoreKeys.DEVICE); keys.delete(KeystoreKeys.BIO)
            // Also remove legacy secrets so a wipe cannot re-import them on restart.
            migration.finish()
            reset()
            check(context.deleteSharedPreferences("logipass_auth"))
        }
        file = null; bio.value = false; legacy = null
        mutable.value = AuthState.NeedsSetup(null)
    }
    companion object { const val MIN_PASSWORD_LENGTH = 8 }
}
