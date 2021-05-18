package me.shedaniel.linkie.buffer

import me.shedaniel.linkie.utils.StringPool
import okio.Buffer

fun ByteBuffer.writeMagic(original: String, string: String?) {
    when (string) {
        original -> writeByte(1)
        null -> writeByte(2)
        else -> {
            writeByte(3)
            writeNotNullString(string)
        }
    }
}

fun ByteBuffer.readMagic(original: String): String? {
    return when (readByte().toInt()) {
        1 -> original
        2 -> null
        else -> readNotNullString()
    }
}

fun writer(): ByteBuffer = ByteBuffer.writer()
fun reader(byteArray: ByteArray): ByteBuffer = ByteBuffer.reader(byteArray)

@JvmName("reader_")
fun ByteArray.reader(): ByteBuffer = reader(this)

private val pool = StringPool()

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("unused")
open class ByteBuffer(val buffer: Buffer) {
    companion object {
        fun writer(): ByteBuffer = ByteBuffer(Buffer())
        fun reader(byteArray: ByteArray): ByteBuffer = ByteBuffer(Buffer()).apply {
            writeByteArray(byteArray)
        }
    }

    fun writeByte(byte: Byte) = buffer.writeByte(byte.toInt())
    fun writeByte(byte: Int) = buffer.writeByte(byte)
    fun writeByteArray(array: ByteArray) = buffer.write(array)
    fun writeBoolean(boolean: Boolean) = buffer.writeByte(if (boolean) 1 else 0)
    fun writeShort(short: Short) = buffer.writeShort(short.toInt())
    fun writeUnsignedShort(short: UShort) = writeShort(short.toShort())
    fun writeInt(int: Int) = buffer.writeInt(int)
    fun writeLong(long: Long) = buffer.writeLong(long)
    fun writeFloat(float: Float) = writeInt(float.toBits())
    fun writeDouble(double: Double) = writeLong(double.toBits())
    fun writeChar(char: Char) = writeShort(char.code.toShort())

    inline fun <T> writeCollection(collection: Collection<T>, crossinline writer: ByteBuffer.(T) -> Unit) {
        writeInt(collection.size)
        collection.forEach { writer(this, it) }
    }

    open fun writeTo(): ByteArray = buffer.inputStream().readBytes()

    fun readByte(): Byte = buffer.readByte()
    fun readByteArray(length: Int): ByteArray = buffer.readByteArray(length.toLong())
    fun readBoolean(): Boolean = buffer.readByte().toInt() == 1
    fun readShort(): Short = buffer.readShort()
    fun readUnsignedShort(): UShort = buffer.readShort().toUShort()
    fun readInt(): Int = buffer.readInt()
    fun readLong(): Long = buffer.readLong()
    fun readFloat(): Float = Float.fromBits(buffer.readInt())
    fun readDouble(): Double = Double.fromBits(buffer.readLong())
    fun readChar(): Char = buffer.readShort().toInt().toChar()

    inline fun <T> readCollection(crossinline reader: ByteBuffer.() -> T): List<T> {
        val size = readInt()
        val list = ArrayList<T>(size)
        for (i in 0 until size) {
            list.add(reader(this))
        }
        return list
    }

    open fun writeStringOrNull(string: String?) {
        if (string != null) {
            writeNotNullString(string)
        } else {
            writeUnsignedShort(0U)
        }
    }

    open fun writeNotNullString(string: String) {
        writeUnsignedShort((string.length + 1).toUShort())
        buffer.writeUtf8(string)
    }

    open fun readStringOrNull(): String? {
        val length = readUnsignedShort().toLong()
        if (length == 0L) return null
        return pool[buffer.readUtf8(length - 1)]
    }

    fun readNotNullString(): String = readStringOrNull()!!
}
