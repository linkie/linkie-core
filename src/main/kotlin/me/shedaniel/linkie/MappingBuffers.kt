package me.shedaniel.linkie

import org.boon.primitive.ByteBuf
import org.boon.primitive.InputByteArray
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun ByteBuffer.writeMappingsContainer(mappingsContainer: MappingsContainer) {
    writeString(mappingsContainer.version)
    writeString(mappingsContainer.name)
    writeString(mappingsContainer.mappingSource?.name)
    writeInt(mappingsContainer.classes.size)
    for (aClass in mappingsContainer.classes) {
        writeClass(aClass)
    }
}

fun ByteBuffer.writeClass(aClass: Class) {
    writeString(aClass.intermediaryName)
    writeObf(aClass.obfName)
    writeString(aClass.mappedName)
    writeCollection(aClass.methods) { writeMethod(it) }
    writeCollection(aClass.fields) { writeField(it) }
}

fun ByteBuffer.writeMethod(method: Method) {
    writeString(method.intermediaryName)
    writeString(method.intermediaryDesc)
    writeObf(method.obfName)
    writeObf(method.obfDesc)
    writeString(method.mappedName)
    writeString(method.mappedDesc)
}

fun ByteBuffer.writeField(field: Field) {
    writeString(field.intermediaryName)
    writeString(field.intermediaryDesc)
    writeObf(field.obfName)
    writeObf(field.obfDesc)
    writeString(field.mappedName)
    writeString(field.mappedDesc)
}

fun ByteBuffer.writeObf(obf: Obf) {
    when {
        obf.isEmpty() -> writeByte(0)
        obf.isMerged() -> {
            writeByte(1)
            writeString(obf.merged)
        }
        else -> {
            writeByte(2)
            writeString(obf.client)
            writeString(obf.server)
        }
    }
}

fun ByteBuffer.readMappingsContainer(): MappingsContainer {
    val version = readString()
    val name = readString()
    val mappingSource = readStringOrNull()?.let { MappingsContainer.MappingSource.valueOf(it) }
    val mappingsContainer = MappingsContainer(version, name = name, mappingSource = mappingSource)
    for (i in 0 until readInt())
        mappingsContainer.classes.add(readClass())
    return mappingsContainer
}

fun ByteBuffer.readClass(): Class {
    val intermediaryName = readString()
    val obfName = readObf()
    val mappedName = readStringOrNull()
    val aClass = Class(intermediaryName, obfName, mappedName)
    aClass.methods.addAll(readCollection { readMethod() })
    aClass.fields.addAll(readCollection { readField() })
    return aClass
}

fun ByteBuffer.readMethod(): Method {
    val intermediaryName = readString()
    val intermediaryDesc = readString()
    val obfName = readObf()
    val obfDesc = readObf()
    val mappedName = readStringOrNull()
    val mappedDesc = readStringOrNull()
    return Method(intermediaryName, intermediaryDesc, obfName, obfDesc, mappedName, mappedDesc)
}

fun ByteBuffer.readField(): Field {
    val intermediaryName = readString()
    val intermediaryDesc = readString()
    val obfName = readObf()
    val obfDesc = readObf()
    val mappedName = readStringOrNull()
    val mappedDesc = readStringOrNull()
    return Field(intermediaryName, intermediaryDesc, obfName, obfDesc, mappedName, mappedDesc)
}

fun ByteBuffer.readObf(): Obf {
    return when (readByte().toInt()) {
        0 -> Obf()
        1 -> Obf(merged = readStringOrNull())
        else -> {
            val client = readStringOrNull()
            val server = readStringOrNull()
            Obf(client, server)
        }
    }
}

fun inputBuffer(capacity: Int = 2048): ByteBuffer = ByteBuffer(input = ByteBuf.create(capacity))
fun outputBuffer(byteArray: ByteArray): ByteBuffer = ByteBuffer(output = InputByteArray(byteArray))
fun outputCompressedBuffer(byteArray: ByteArray): ByteBuffer = outputBuffer(GZIPInputStream(ByteArrayInputStream(byteArray)).readAllBytes())

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

    fun writeString(string: String?) {
        if (string != null) {
            writeUnsignedShort(string.length.toUShort())
            writeByteArray(string.toByteArray())
        } else {
            writeUnsignedShort(0U)
        }
    }

    fun readStringOrNull(): String? {
        val length = readUnsignedShort().toInt()
        if (length == 0) return null
        return readByteArray(length).toString(Charsets.UTF_8)
    }

    fun readString(): String = readStringOrNull() ?: ""
}