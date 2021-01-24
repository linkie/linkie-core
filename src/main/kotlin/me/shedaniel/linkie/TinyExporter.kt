package me.shedaniel.linkie

import net.fabricmc.stitch.commands.tinyv2.TinyClass
import net.fabricmc.stitch.commands.tinyv2.TinyField
import net.fabricmc.stitch.commands.tinyv2.TinyFile
import net.fabricmc.stitch.commands.tinyv2.TinyHeader
import net.fabricmc.stitch.commands.tinyv2.TinyMethod
import net.fabricmc.stitch.commands.tinyv2.TinyV2Writer
import java.io.File
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
            container.classes.forEach { (_, clazz) ->
                val tinyClass = TinyClass(buildList {
                    add(clazz.intermediaryName)
                    named?.also { add(clazz.mappedName ?: clazz.intermediaryName) }
                    obfMerged?.also { add(clazz.obfMergedName ?: clazz.intermediaryName) }
                    obfClient?.also { add(clazz.obfClientName ?: clazz.intermediaryName) }
                    obfServer?.also { add(clazz.obfServerName ?: clazz.intermediaryName) }
                })
                tinyClass.methods.addAll(buildList {
                    clazz.methods.forEach { method ->
                        if (method.intermediaryDesc.isBlank())
                            println(method)
                        val tinyMethod = TinyMethod(method.intermediaryDesc, buildList {
                            add(method.intermediaryName)
                            named?.also { add(method.optimumName) }
                            obfMerged?.also { add(method.obfMergedName ?: method.intermediaryName) }
                            obfClient?.also { add(method.obfClientName ?: method.intermediaryName) }
                            obfServer?.also { add(method.obfServerName ?: method.intermediaryName) }
                        }, emptyList(), emptyList(), emptyList())
                        add(tinyMethod)
                    }
                })
                tinyClass.fields.addAll(buildList {
                    clazz.fields.forEach { field ->
                        val tinyMethod = TinyField(field.intermediaryDesc, buildList {
                            add(field.intermediaryName)
                            named?.also { add(field.optimumName) }
                            obfMerged?.also { add(field.obfMergedName ?: field.intermediaryName) }
                            obfClient?.also { add(field.obfClientName ?: field.intermediaryName) }
                            obfServer?.also { add(field.obfServerName ?: field.intermediaryName) }
                        }, emptyList())
                        add(tinyMethod)
                    }
                })
                add(tinyClass)
            }
        })
        val tmpPath = File(Namespaces.cacheFolder.absolutePath).toPath().resolve(UUID.randomUUID().toString())
        TinyV2Writer.write(tinyFile, tmpPath)
        val bytes = Files.readAllBytes(tmpPath)
        Files.deleteIfExists(tmpPath)
        return bytes.inputStream()
    }
}