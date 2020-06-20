package me.shedaniel.linkie

import org.boon.primitive.ByteBuf
import org.boon.primitive.InputByteArray

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
    writeInt(aClass.methods.size)
    for (method in aClass.methods) {
        writeMethod(method)
    }
    writeInt(aClass.fields.size)
    for (field in aClass.fields) {
        writeField(field)
    }
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
    writeBoolean(obf.isMerged())
    if (obf.isMerged()) {
        writeString(obf.merged)
    } else {
        writeString(obf.client)
        writeString(obf.server)
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
    for (i in 0 until readInt())
        aClass.methods.add(readMethod())
    for (i in 0 until readInt())
        aClass.fields.add(readField())
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
    val merged = readBoolean()
    return if (merged) {
        Obf(merged = readStringOrNull())
    } else {
        val client = readStringOrNull()
        val server = readStringOrNull()
        Obf(client, server)
    }
}

fun inputBuffer(capacity: Int = 2048): ByteBuffer = ByteBuffer(input = ByteBuf.create(capacity))
fun outputBuffer(byteArray: ByteArray): ByteBuffer = ByteBuffer(output = InputByteArray(byteArray))

@Suppress("unused")
class ByteBuffer(private val input: ByteBuf? = null, private val output: InputByteArray? = null) {
    fun writeByte(byte: Byte) {
        input!!.add(byte.toInt())
    }

    fun writeByteArray(array: ByteArray) {
        input!!.add(array)
    }

    fun writeBoolean(boolean: Boolean) {
        input!!.writeBoolean(boolean)
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
    
    fun toByteArray(): ByteArray = input!!.toBytes()

    fun readByte(): Byte = output!!.readByte()
    fun readByteArray(length: Int): ByteArray = output!!.readBytes(length)
    fun readBoolean(): Boolean = output!!.readBoolean()
    fun readInt(): Int = output!!.readInt()
    fun readLong(): Long = output!!.readLong()
    fun readFloat(): Float = output!!.readFloat()
    fun readDouble(): Double = output!!.readDouble()
    fun readChar(): Char = output!!.readChar()

    fun writeString(string: String?) {
        if (string != null) {
            writeInt(string.length)
            writeByteArray(string.toByteArray())
        } else {
            writeInt(0)
        }
    }

    fun readStringOrNull(): String? {
        val length = readInt()
        if (length == 0) return null
        return readByteArray(length).toString(Charsets.UTF_8)
    }

    fun readString(): String = readStringOrNull() ?: ""
}