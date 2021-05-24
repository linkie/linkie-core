package me.shedaniel.linkie.buffer

import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Obf

fun ByteBuffer.writeMappingsContainer(mappingsContainer: Mappings) {
    writeNotNullString(mappingsContainer.version)
    writeNotNullString(mappingsContainer.name)
    writeStringOrNull(mappingsContainer.mappingsSource?.id)
    writeCollection(mappingsContainer.classes.values) { writeClass(it) }
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
    writeMagic(method.intermediaryName, method.mappedName)
}

fun ByteBuffer.writeField(field: Field) {
    writeNotNullString(field.intermediaryName)
    writeNotNullString(field.intermediaryDesc)
    writeMagicObf(field.intermediaryName, field.obfName)
    writeMagic(field.intermediaryName, field.mappedName)
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

fun ByteBuffer.readMappingsContainer(): Mappings {
    val version = readNotNullString()
    val name = readNotNullString()
    val mappingSource = readStringOrNull()?.let { MappingsSource.of(it) }
    val mappingsContainer = Mappings(version, name = name, mappingsSource = mappingSource)
    readCollection { readClass() }.forEach {
        mappingsContainer.classes[it.intermediaryName] = it
    }
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
    val mappedName = readMagic(intermediaryName)
    return Method(intermediaryName, intermediaryDesc, obfName, mappedName)
}

fun ByteBuffer.readField(): Field {
    val intermediaryName = readNotNullString()
    val intermediaryDesc = readNotNullString()
    val obfName = readMagicObf(intermediaryName)
    val mappedName = readMagic(intermediaryName)
    return Field(intermediaryName, intermediaryDesc, obfName, mappedName)
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
