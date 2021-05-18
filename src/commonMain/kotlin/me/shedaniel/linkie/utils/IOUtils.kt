package me.shedaniel.linkie.utils

import com.soywiz.korio.async.runBlockingNoJs
import com.soywiz.korio.file.VfsFile
import kotlin.jvm.JvmInline

suspend fun URL.readText() = readBytes().decodeToString()
suspend fun URL.readLines() = readBytes().lines()
suspend fun ByteArray.lines() = decodeToString().lineSequence()
expect suspend fun URL.readBytes(): ByteArray
expect fun getMillis(): Long
val URL.bytes: ByteArray
    get() = runBlockingNoJs { readBytes() }

expect fun gc()

@JvmInline
data class URL(val url: String)

operator fun VfsFile.div(related: String): VfsFile = this[related]
