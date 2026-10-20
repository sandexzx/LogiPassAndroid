package com.example.logipass

import android.content.Context
import com.example.logipass.data.*
import com.example.logipass.platform.SecureClipboard
import com.example.logipass.security.*
import com.example.logipass.session.AuthManager
import kotlinx.coroutines.*
import java.io.File

class AppContainer(context: Context) {
    private val app = context.applicationContext
    /** Outlives activities: UI writes run here so rotating or leaving a screen never cancels a save. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val vault = VaultRepository(FileVaultStore(File(app.noBackupFilesDir, "vault.bin")))
    val auth = AuthManager(app, vault, KeystoreKeys(), scope)
    val backup = BackupManager(app, vault)
    val clipboard = SecureClipboard(app, scope)
    val biometric = BiometricAuthenticator()
}
