package me.shedaniel.linkie

import org.boon.primitive.ByteBuf
import org.boon.primitive.InputByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

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

fun inputBuffer(capacity: Int = 2048): ByteBuffer = ByteBuffer(input = ByteBuf.create(capacity))
fun outputBuffer(byteArray: ByteArray): ByteBuffer = ByteBuffer(output = InputByteArray(byteArray))
fun outputCompressedBuffer(byteArray: ByteArray): ByteBuffer = outputBuffer(GZIPInputStream(ByteArrayInputStream(byteArray)).use { it.readBytes() })

@OptIn(ExperimentalUnsignedTypes::class)
@Suppress("unused")
class ByteBuffer(private val input: ByteBuf? = null, private val output: InputByteArray? = null) {
    fun writeByte(byte: Byte) {
        input!!.add(byte)
    }

    fun writeByteArray(array: ByteArray) {
        input!!.add(array)
    }

    fun writeBoolean(boolean: Boolean) {
        input!!.writeBoolean(boolean)
    }

    fun writeShort(short: Short) {
        input!!.add(short)
    }

    fun writeUnsignedShort(short: UShort) {
        input!!.add(short.toShort())
    }

    fun writeInt(int: Int) {
        input!!.add(int)
    }

    fun writeLong(long: Long) {
        input!!.add(long)
    }

    fun writeFloat(float: Float) {
        input!!.add(float)
    }

    fun writeDouble(double: Double) {
        input!!.add(double)
    }

    fun writeChar(char: Char) {
        input!!.add(char)
    }

    inline fun <T> writeCollection(collection: Collection<T>, crossinline writer: ByteBuffer.(T) -> Unit) {
        writeInt(collection.size)
        collection.forEach { writer(this, it) }
    }

    fun toByteArray(): ByteArray = input!!.toBytes()
    fun toCompressedByteArray(): ByteArray = ByteArrayOutputStream().also { outputStream ->
        GZIPOutputStream(outputStream).use { it.write(toByteArray()) }
    }.toByteArray()

    fun readByte(): Byte = output!!.readByte()
    fun readByteArray(length: Int): ByteArray = output!!.readBytes(length)
    fun readBoolean(): Boolean = output!!.readBoolean()
    fun readShort(): Short = output!!.readShort()
    fun readUnsignedShort(): UShort = output!!.readShort().toUShort()
    fun readInt(): Int = output!!.readInt()
    fun readLong(): Long = output!!.readLong()
    fun readFloat(): Float = output!!.readFloat()
    fun readDouble(): Double = output!!.readDouble()
    fun readChar(): Char = output!!.readChar()

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
        writeUnsignedShort(string.length.toUShort())
        writeByteArray(string.toByteArray())
    }

    fun readStringOrNull(): String? {
        val length = readUnsignedShort().toInt()
        if (length == 0) return null
        return readByteArray(length).toString(Charsets.UTF_8)
    }

    fun readNotNullString(): String = readStringOrNull()!!
}