package me.shedaniel.linkie.utils

actual suspend fun URL.readBytes(): ByteArray = runCatching { java.net.URL(url).readBytes() }.getOrThrow()
actual fun getMillis(): Long = System.currentTimeMillis()
actual fun gc() = System.gc()
