package me.shedaniel.linkie

import net.fabricmc.stitch.commands.tinyv2.*
import java.io.InputStream
import java.nio.file.Files
import java.util.*

object TinyExporter {
    @OptIn(ExperimentalStdlibApi::class)
    fun export(
        container: MappingsContainer,
        intermediary: String,
        named: String? = null,
        obfMerged: String? = null,
        obfClient: String? = null,
        obfServer: String? = null,
    ): InputStream {
        val namespaces = mutableListOf(intermediary)
        named?.also { namespaces.add(it) }
        obfMerged?.also { namespaces.add(it) }
        obfClient?.also { namespaces.add(it) }
        obfServer?.also { namespaces.add(it) }
        val tinyFile = TinyFile(TinyHeader(namespaces, 2, 0, mapOf()), buildList {
            container.classes.forEach { clazz ->
                val tinyClass = TinyClass(buildList {
                    add(clazz.intermediaryName)
                    named?.also { add(clazz.mappedName ?: clazz.intermediaryName) }
                    obfMerged?.also { add(clazz.obfName.merged ?: clazz.intermediaryName) }
                    obfClient?.also { add(clazz.obfName.client ?: clazz.intermediaryName) }
                    obfServer?.also { add(clazz.obfName.server ?: clazz.intermediaryName) }
                })
                tinyClass.methods.addAll(buildList {
                    clazz.methods.forEach { method ->
                        val tinyMethod = TinyMethod(method.intermediaryDesc, buildList {
                            add(method.intermediaryName)
                            named?.also { add(method.mappedName ?: method.intermediaryName) }
                            obfMerged?.also { add(method.obfName.merged ?: method.intermediaryName) }
                            obfClient?.also { add(method.obfName.client ?: method.intermediaryName) }
                            obfServer?.also { add(method.obfName.server ?: method.intermediaryName) }
                        }, emptyList(), emptyList(), emptyList())
                        add(tinyMethod)
                    }
                })
                tinyClass.fields.addAll(buildList {
                    clazz.fields.forEach { field ->
                        val tinyMethod = TinyField(field.intermediaryDesc, buildList {
                            add(field.intermediaryName)
                            named?.also { add(field.mappedName ?: field.intermediaryName) }
                            obfMerged?.also { add(field.obfName.merged ?: field.intermediaryName) }
                            obfClient?.also { add(field.obfName.client ?: field.intermediaryName) }
                            obfServer?.also { add(field.obfName.server ?: field.intermediaryName) }
                        }, emptyList())
                        add(tinyMethod)
                    }
                })
                add(tinyClass)
            }
        })
        val tmpPath = Namespaces.cacheFolder.toPath().resolve(UUID.randomUUID().toString())
        TinyV2Writer.write(tinyFile, tmpPath)
        val bytes = Files.readAllBytes(tmpPath)
        Files.deleteIfExists(tmpPath)
        return bytes.inputStream()
    }
}