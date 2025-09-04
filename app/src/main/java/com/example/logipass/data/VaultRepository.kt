package com.example.logipass.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.example.logipass.model.ServiceItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class VaultRepository(private val context: Context) {
    private val gson = Gson()
    private val appFile: File by lazy { File(context.filesDir, APP_FILE_NAME) }

    fun loadFromAppStorage(): List<ServiceItem> {
        if (!appFile.exists()) return emptyList()
        return runCatching {
            appFile.inputStream().use { input ->
                InputStreamReader(input).use { reader ->
                    val listType = object : TypeToken<List<ServiceItem>>() {}.type
                    gson.fromJson<List<ServiceItem>>(reader, listType) ?: emptyList()
                }
            }
        }.getOrElse { emptyList() }
    }

    fun saveToAppStorage(items: List<ServiceItem>) {
        runCatching {
            appFile.outputStream().use { output ->
                OutputStreamWriter(output).use { writer ->
                    gson.toJson(items, writer)
                }
            }
        }
    }

    fun importFromUri(contentResolver: ContentResolver, uri: Uri): List<ServiceItem> {
        val imported = runCatching {
            contentResolver.openInputStream(uri).use { input ->
                InputStreamReader(input).use { reader ->
                    val listType = object : TypeToken<List<ServiceItem>>() {}.type
                    gson.fromJson<List<ServiceItem>>(reader, listType) ?: emptyList()
                }
            }
        }.getOrElse { emptyList() }
        if (imported.isNotEmpty()) saveToAppStorage(imported)
        return imported
    }

    fun exportToUri(contentResolver: ContentResolver, uri: Uri, items: List<ServiceItem>): Boolean {
        return runCatching {
            contentResolver.openOutputStream(uri)?.use { output ->
                OutputStreamWriter(output).use { writer ->
                    gson.toJson(items, writer)
                }
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val APP_FILE_NAME = "vault.json"
    }
}

