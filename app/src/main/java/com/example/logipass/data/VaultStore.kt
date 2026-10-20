package com.example.logipass.data

import android.util.AtomicFile
import java.io.File

interface VaultStore {
    fun read(dek: ByteArray): com.example.logipass.model.Vault
    fun write(vault: com.example.logipass.model.Vault, dek: ByteArray)
}
internal fun atomicWrite(file: File, bytes: ByteArray) {
    val atomic = AtomicFile(file)
    val stream = atomic.startWrite()
    try { stream.write(bytes); atomic.finishWrite(stream) }
    catch (error: Throwable) { atomic.failWrite(stream); throw error }
}
class FileVaultStore(private val file: File) : VaultStore {
    override fun read(dek: ByteArray) = VaultCodec.open(AtomicFile(file).readFully(), dek)
    override fun write(vault: com.example.logipass.model.Vault, dek: ByteArray) = atomicWrite(file, VaultCodec.seal(vault, dek))
}
