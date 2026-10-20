package com.example.logipass.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.*
import java.time.LocalDate

class BackupManager(private val context: Context, private val vault: VaultRepository, private val codec: BackupCodec = BackupCodec()) {
    fun suggestedFileName(): String = "logipass-${LocalDate.now()}.json"
    suspend fun export(uri: Uri, password: String): Boolean {
        val snapshot = vault.snapshot() ?: return false
        val text = withContext(Dispatchers.Default) { codec.encode(snapshot, password) }
        return withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@withContext false
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
                true
            } catch (_: java.io.IOException) { false }
            catch (_: SecurityException) { false }
        }
    }
    suspend fun probe(uri: Uri): ImportProbe = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return@withContext ImportProbe.Invalid
        } catch (_: java.io.IOException) { return@withContext ImportProbe.Invalid }
        catch (_: SecurityException) { return@withContext ImportProbe.Invalid }
        withContext(Dispatchers.Default) { codec.probe(text) }
    }
    suspend fun decrypt(probe: ImportProbe.Encrypted, password: String): DecryptResult = withContext(Dispatchers.Default) { codec.decrypt(probe, password) }
}
