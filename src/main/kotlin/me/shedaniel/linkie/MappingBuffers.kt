package me.shedaniel.linkie

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import kotlin.properties.Delegates

fun ByteBuffer.writeMappingsContainer(mappingsContainer: MappingsContainer) {
    writeNotNullString(mappingsContainer.version)
    writeNotNullString(mappingsContainer.name)
    writeStringOrNull(mappingsContainer.mappingSource?.name)
    writeCollection(mappingsContainer.classes) { writeClass(it) }
}

fun ByteBuffer.writeClass(aClass: Class) {
    writeNotNullString(aClass.intermediaryName)
    writeMagicObf(aClass.intermediaryName, aClass.obfName)
    writeMagic(aClass.intermediaryName, aClass.mappedName)
    writeCollection(aClass.methods) { writeMethod(it) }
    writeCollection(aClass.fields) { writeField(it) }
}

fun ByteBuffer.writeMethod(method: Method) {
    writeNotNullString(method.intermediaryName)
    writeNotNullString(method.intermediaryDesc)
    writeMagicObf(method.intermediaryName, method.obfName)
    writeMagicObf(method.intermediaryDesc, method.obfDesc)
    writeMagic(method.intermediaryName, method.mappedName)
    writeMagic(method.intermediaryDesc, method.mappedDesc)
}

fun ByteBuffer.writeField(field: Field) {
    writeNotNullString(field.intermediaryName)
    writeNotNullString(field.intermediaryDesc)
    writeMagicObf(field.intermediaryName, field.obfName)
    writeMagicObf(field.intermediaryDesc, field.obfDesc)
    writeMagic(field.intermediaryName, field.mappedName)
    writeMagic(field.intermediaryDesc, field.mappedDesc)
}

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

fun ByteBuffer.writeObf(obf: Obf) {
    when {
        obf.isEmpty() -> writeByte(0)
        obf.isMerged() -> when (obf.merged) {
            null -> writeByte(2)
            else -> {
                writeByte(3)
                writeNotNullString(obf.merged!!)
            }
        }
        else -> {
            writeByte(4)
            writeStringOrNull(obf.client)
            writeStringOrNull(obf.server)
        }
    }
}

fun ByteBuffer.writeMagicObf(original: String, obf: Obf) {
    when {
        obf.isEmpty() -> writeByte(0)
        obf.isMerged() -> writeMagic(original, obf.merged)
        else -> {
            writeByte(4)
            writeStringOrNull(obf.client)
            writeStringOrNull(obf.server)
        }
    }
}

fun ByteBuffer.readMappingsContainer(): MappingsContainer {
    val version = readNotNullString()
    val name = readNotNullString()
    val mappingSource = readStringOrNull()?.let { MappingsContainer.MappingSource.valueOf(it) }
    val mappingsContainer = MappingsContainer(version, name = name, mappingSource = mappingSource)
    mappingsContainer.classes.addAll(readCollection { readClass() })
    return mappingsContainer
}

fun ByteBuffer.readClass(): Class {
    val intermediaryName = readNotNullString()
    val obfName = readMagicObf(intermediaryName)
    val mappedName = readMagic(intermediaryName)
    val aClass = Class(intermediaryName, obfName, mappedName)
    aClass.methods.addAll(readCollection { readMethod() })
    aClass.fields.addAll(readCollection { readField() })
    return aClass
}

fun ByteBuffer.readMethod(): Method {
    val intermediaryName = readNotNullString()
    val intermediaryDesc = readNotNullString()
    val obfName = readMagicObf(intermediaryName)
    val obfDesc = readMagicObf(intermediaryDesc)
    val mappedName = readMagic(intermediaryName)
    val mappedDesc = readMagic(intermediaryDesc)
    return Method(intermediaryName, intermediaryDesc, obfName, obfDesc, mappedName, mappedDesc)
}

fun ByteBuffer.readField(): Field {
    val intermediaryName = readNotNullString()
    val intermediaryDesc = readNotNullString()
    val obfName = readMagicObf(intermediaryName)
    val obfDesc = readMagicObf(intermediaryDesc)
    val mappedName = readMagic(intermediaryName)
    val mappedDesc = readMagic(intermediaryDesc)
    return Field(intermediaryName, intermediaryDesc, obfName, obfDesc, mappedName, mappedDesc)
}

fun ByteBuffer.readObf(): Obf {
    return when (readByte().toInt()) {
        0 -> Obf()
        2 -> Obf(merged = null)
        3 -> Obf(merged = readNotNullString())
        else -> {
            val client = readStringOrNull()
            val server = readStringOrNull()
            Obf(client, server)
        }
    }
}

fun ByteBuffer.readMagicObf(original: String): Obf {
    return when (readByte().toInt()) {
        0 -> Obf()
        1 -> Obf(merged = original)
        2 -> Obf(merged = null)
        3 -> Obf(merged = readNotNullString())
        else -> {
            val client = readStringOrNull()
            val server = readStringOrNull()
            Obf(client, server)
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

fun inputBuffer(): ByteBuffer = ByteBuffer(Buffer() as BufferedSink)
fun outputBuffer(byteArray: ByteArray): ByteBuffer = ByteBuffer(Buffer() as BufferedSource).apply { writeByteArray(byteArray) }
@JvmName("outputBuffer_")
fun ByteArray.outputBuffer(): ByteBuffer = outputBuffer(this)

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("unused")
class ByteBuffer {
    var source by Delegates.notNull<BufferedSource>()
    var sink by Delegates.notNull<BufferedSink>()

    constructor(source: BufferedSource) {
        this.source = source
    }

    constructor(sink: BufferedSink) {
        this.sink = sink
    }

    fun writeByte(byte: Byte) = sink.writeByte(byte.toInt())
    fun writeByteArray(array: ByteArray) = sink.write(array)
    fun writeBoolean(boolean: Boolean) = sink.writeByte(if (boolean) 1 else 0)
    fun writeShort(short: Short) = sink.writeShort(short.toInt())
    fun writeUnsignedShort(short: UShort) = writeShort(short.toShort())
    fun writeInt(int: Int) = sink.writeInt(int)
    fun writeLong(long: Long) = sink.writeLong(long)
    fun writeFloat(float: Float) = writeInt(float.toBits())
    fun writeDouble(double: Double) = writeLong(double.toBits())
    fun writeChar(char: Char) = writeByte(char.toByte())

    inline fun <T> writeCollection(collection: Collection<T>, crossinline writer: ByteBuffer.(T) -> Unit) {
        writeInt(collection.size)
        collection.forEach { writer(this, it) }
    }

    fun toByteArray(): ByteArray = source.readByteArray()

    fun readByte(): Byte = source.readByte()
    fun readByteArray(length: Int): ByteArray = source.readByteArray(length.toLong())
    fun readBoolean(): Boolean = source.readByte().toInt() == 1
    fun readShort(): Short = source.readShort()
    fun readUnsignedShort(): UShort = source.readShort().toUShort()
    fun readInt(): Int = source.readInt()
    fun readLong(): Long = source.readLong()
    fun readFloat(): Float = Float.fromBits(source.readInt())
    fun readDouble(): Double = Double.fromBits(source.readLong())
    fun readChar(): Char = source.readByte().toChar()

    inline fun <T> readCollection(crossinline reader: ByteBuffer.() -> T): List<T> {
        val size = readInt()
        val list = ArrayList<T>(size)
        for (i in 0 until size) {
            list.add(reader(this))
        }
        return list
    }

    fun writeStringOrNull(string: String?) {
        if (string != null) {
            writeNotNullString(string)
        } else {
            writeUnsignedShort(0U)
        }
    }

    fun writeNotNullString(string: String) {
        writeUnsignedShort((string.length + 1).toUShort())
        sink.writeUtf8(string)
    }

    fun readStringOrNull(): String? {
        val length = readUnsignedShort().toLong()
        if (length == 0L) return null
        return source.readUtf8(length - 1)
    }

    fun readNotNullString(): String = readStringOrNull()!!
}
