package com.example.logipass.platform

import android.content.*
import android.os.PersistableBundle
import kotlinx.coroutines.*
import java.util.UUID

class SecureClipboard(context: Context, private val scope: CoroutineScope) {
    private val clipboard = context.getSystemService(ClipboardManager::class.java)
    private var clear: Job? = null
    fun copy(text: String, sensitive: Boolean) {
        clear?.cancel()
        val label = "logipass-${UUID.randomUUID()}"
        val clip = ClipData.newPlainText(label, text)
        if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        clipboard.setPrimaryClip(clip)
        if (sensitive) clear = scope.launch {
            delay(45_000)
            val description = try { clipboard.primaryClipDescription } catch (_: SecurityException) { null }
            if (description != null && description.label?.toString() != label) return@launch
            clipboard.clearPrimaryClip()
        }
    }
}
