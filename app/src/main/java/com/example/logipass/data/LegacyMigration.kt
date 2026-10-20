package com.example.logipass.data

import android.content.Context
import android.util.Log
import com.example.logipass.model.Vault
import com.example.logipass.security.KeystoreKeys
import java.io.File

class LegacyMigration(private val context: Context, private val keys: KeystoreKeys) {
    private val file = File(context.filesDir, "vault.json")
    fun read(): Vault? {
        if (!file.exists()) return null
        return try { LegacyFormat.parse(file.readText()) }
        catch (_: IllegalArgumentException) { Log.w("LogiPass", "Legacy vault is invalid; preserved"); null }
    }
    fun finish() {
        if (file.exists()) check(file.delete()) { "Cannot remove migrated legacy vault" }
        context.deleteSharedPreferences("LogiPassSecure")
        keys.delete("LogiPassPinKey")
    }
}
