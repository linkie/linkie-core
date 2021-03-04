package me.shedaniel.linkie.utils

import me.shedaniel.linkie.ByteBuffer

class StringSwimmingPool {
    private val pool = mutableMapOf<String?, Int>()
    private val poolInverse = mutableMapOf<Int, String?>()

    fun writeString(buf: ByteBuffer, string: String?) {
        val existing = pool[string]
        if (existing != null) {
            buf.writeInt(existing)
        } else {
            val new = pool.size
            pool[string] = new
            poolInverse[new] = string
            buf.writeInt(new)
        }
    }

    fun readString(buf: ByteBuffer): String? {
        return poolInverse[buf.readInt()]
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun read(buf: ByteBuffer) = buf.apply {
        val size = readInt()
        for (i in 0 until size) {
            val length = readUnsignedShort().toLong()
            val string = if (length == 0L) null
            else buffer.readUtf8(length - 1)
            pool[string] = i
            poolInverse[i] = string
        }
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    fun write(buf: ByteBuffer) = buf.apply {
        writeInt(pool.size)
        pool.keys.forEach { string ->
            if (string == null) {
                writeUnsignedShort(0U)
            } else {
                writeUnsignedShort((string.length + 1).toUShort())
                buffer.writeUtf8(string)
            }
        }
    }
}
