@file:Suppress("unused")

package me.shedaniel.linkie

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File

interface MappingsSupplier {
    fun isApplicable(version: String): Boolean
    fun isCached(version: String): Boolean = false
    fun applyVersion(version: String): MappingsContainer
}

fun Namespace.loggedSupplier(mappingsSupplier: MappingsSupplier): MappingsSupplier =
        LoggedMappingsSupplier(this, mappingsSupplier)

fun Namespace.cachedSupplier(uuidGetter: (String) -> String, mappingsSupplier: MappingsSupplier): MappingsSupplier =
        CachedMappingsSupplier(this, uuidGetter, mappingsSupplier)

fun Namespace.simpleSupplier(version: String, supplier: (String) -> MappingsContainer): MappingsSupplier =
        SimpleMappingsSupplier(version) { supplier(version) }

fun Namespace.simpleCachedSupplier(version: String, uuid: String = version, supplier: (String) -> MappingsContainer): MappingsSupplier =
        cachedSupplier({ uuid }, simpleSupplier(version, supplier))

fun Namespace.multipleSupplier(versions: Iterable<String>, supplier: (String) -> MappingsContainer): MappingsSupplier =
        multipleSupplier({ versions }, supplier)

fun Namespace.multipleSupplier(versions: () -> Iterable<String>, supplier: (String) -> MappingsContainer): MappingsSupplier =
        MultiMappingsSupplier(versions, supplier)

fun Namespace.multipleCachedSupplier(versions: Iterable<String>, uuidGetter: (String) -> String, supplier: (String) -> MappingsContainer): MappingsSupplier =
        cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

fun Namespace.multipleCachedSupplier(versions: () -> Iterable<String>, uuidGetter: (String) -> String, supplier: (String) -> MappingsContainer): MappingsSupplier =
        cachedSupplier(uuidGetter, multipleSupplier(versions, supplier))

private class LoggedMappingsSupplier(val namespace: Namespace, val mappingsSupplier: MappingsSupplier) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override fun applyVersion(version: String): MappingsContainer {
        println("Loading $version in $namespace")
        val start = System.currentTimeMillis()
        return mappingsSupplier.applyVersion(version).also { println("Loaded $version in $namespace within ${System.currentTimeMillis() - start}ms") }
    }
}

private val json = Json(JsonConfiguration.Stable.copy(encodeDefaults = false, ignoreUnknownKeys = true, isLenient = true))

private class CachedMappingsSupplier(val namespace: Namespace, val uuidGetter: (String) -> String, val mappingsSupplier: MappingsSupplier) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = mappingsSupplier.isApplicable(version)

    override fun isCached(version: String): Boolean {
        val cacheFolder = File(File(System.getProperty("user.dir")), ".linkie-cache/mappings").also { it.mkdirs() }
        val uuid = uuidGetter(version)
        val cachedFile = File(cacheFolder, "${namespace.id}-$uuid.dat")
        return cachedFile.exists()
    }

    override fun applyVersion(version: String): MappingsContainer {
        val cacheFolder = File(File(System.getProperty("user.dir")), ".linkie-cache/mappings").also { it.mkdirs() }
        val uuid = uuidGetter(version)
        val cachedFile = File(cacheFolder, "${namespace.id}-$uuid.dat")
        if (cachedFile.exists()) {
            val mappingsContainer = loadFromCachedFile(cachedFile)
            if (mappingsContainer != null) return mappingsContainer
            println("Cache for version $version has failed.")
            cachedFile.delete()
        }
        val mappingsContainer = mappingsSupplier.applyVersion(version)
        mappingsContainer.saveToCachedFile(cachedFile)
        return mappingsContainer
    }

    private fun loadFromCachedFile(cachedFile: File): MappingsContainer? =
            outputBuffer(cachedFile.readBytes()).readMappingsContainer()

    private fun MappingsContainer.saveToCachedFile(cachedFile: File) =
            cachedFile.writeBytes(inputBuffer().also { it.writeMappingsContainer(this) }.toByteArray())
}

private class SimpleMappingsSupplier(val version: String, val supplier: () -> MappingsContainer) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version == this.version

    override fun applyVersion(version: String): MappingsContainer = supplier()
}

private class MultiMappingsSupplier(val versions: () -> Iterable<String>, val supplier: (String) -> MappingsContainer) : MappingsSupplier {
    override fun isApplicable(version: String): Boolean = version in versions()

    override fun applyVersion(version: String): MappingsContainer = supplier(version)
}