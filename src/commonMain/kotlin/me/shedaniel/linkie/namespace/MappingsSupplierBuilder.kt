package me.shedaniel.linkie.namespace

import me.shedaniel.linkie.ConcatMappingsSupplier
import me.shedaniel.linkie.EmptyMappingsSupplier
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsSupplier
import me.shedaniel.linkie.cachedSupplier
import me.shedaniel.linkie.namespaces.MappingsBuilder
import me.shedaniel.linkie.namespaces.MappingsVersion
import me.shedaniel.linkie.namespaces.MappingsVersionBuilder
import me.shedaniel.linkie.namespaces.ofVersion
import me.shedaniel.linkie.namespaces.toBuilder

class MappingsSupplierBuilder(
    var cached: Boolean = false,
    var versions: MutableMap<MappingsVersionBuilder, () -> Iterable<String>> = mutableMapOf(),
) {
    fun cached() {
        cached = true
    }

    fun version(version: String, uuid: String, mappings: Mappings) {
        version(version, uuid) { mappings }
    }

    fun version(version: String, uuid: (String) -> String, mappings: Mappings) {
        version(version, uuid) { mappings }
    }

    fun version(version: String, uuid: String, mappings: MappingsBuilder) {
        val list = listOf(version)
        versions(list, uuid, mappings)
    }

    fun version(version: String, uuid: (String) -> String, mappings: MappingsBuilder) {
        val list = listOf(version)
        versions(list, uuid, mappings)
    }

    fun version(version: String, uuid: String, mappings: suspend (String) -> Mappings) {
        version(version, uuid, toBuilder(mappings))
    }

    fun version(version: String, uuid: (String) -> String, mappings: suspend (String) -> Mappings) {
        version(version, uuid, toBuilder(mappings))
    }

    fun version(version: String, mappings: MappingsVersionBuilder) {
        val list = listOf(version)
        versions(list, mappings)
    }

    fun buildVersion(version: String, spec: VersionSpec.(String) -> Unit) {
        buildVersions {
            version(version)
            spec(version)
        }
    }

    fun buildVersion(spec: VersionSpec.() -> Unit) {
        buildVersions(spec)
    }

    fun versions(vararg versions: String, uuid: String, mappings: Mappings) {
        val list = versions.toList()
        versions(list, uuid, mappings)
    }

    fun versions(vararg versions: String, uuid: (String) -> String, mappings: Mappings) {
        val list = versions.toList()
        versions(list, uuid, mappings)
    }

    fun versions(vararg versions: String, uuid: String, mappings: MappingsBuilder) {
        val list = versions.toList()
        versions(list, uuid, mappings)
    }

    fun versions(vararg versions: String, uuid: (String) -> String, mappings: MappingsBuilder) {
        val list = versions.toList()
        versions(list, uuid, mappings)
    }

    fun versions(vararg versions: String, uuid: String, mappings: suspend (String) -> Mappings) {
        val list = versions.toList()
        versions(list, uuid, toBuilder(mappings))
    }

    fun versions(vararg versions: String, uuid: (String) -> String, mappings: suspend (String) -> Mappings) {
        val list = versions.toList()
        versions(list, uuid, toBuilder(mappings))
    }

    fun buildVersions(vararg versions: String, spec: VersionSpec.() -> Unit) {
        buildVersions {
            versions(*versions)
            spec()
        }
    }

    fun versions(vararg versions: String, mappings: MappingsVersionBuilder) {
        val list = versions.toList()
        versions(list, mappings)
    }

    fun versions(versions: Iterable<String>, uuid: String, mappings: Mappings) {
        versions(versions, uuid) { mappings }
    }

    fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: Mappings) {
        versions(versions, uuid) { mappings }
    }

    fun versions(versions: Iterable<String>, uuid: String, mappings: suspend (String) -> Mappings) {
        versions(versions, uuid, toBuilder(mappings))
    }

    fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: suspend (String) -> Mappings) {
        versions(versions, uuid, toBuilder(mappings))
    }

    fun versions(versions: Iterable<String>, uuid: String, mappings: MappingsBuilder) {
        versions(versions) { ofVersion(uuid, mappings) }
    }

    fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: MappingsBuilder) {
        versions(versions) { ofVersion(uuid(it), mappings) }
    }

    fun versions(versions: Iterable<String>, mappings: MappingsVersion) {
        versions(versions) { mappings }
    }

    fun versions(versions: Iterable<String>, mappings: MappingsVersionBuilder) {
        versions({ versions }, mappings)
    }

    fun buildVersions(versions: Iterable<String>, spec: VersionSpec.() -> Unit) {
        buildVersions {
            versions(versions)
            spec()
        }
    }

    fun versions(versions: () -> Iterable<String>, mappings: MappingsVersionBuilder) {
        this.versions[mappings] = versions
    }

    fun buildVersions(versions: () -> Iterable<String>, spec: VersionSpec.() -> Unit) {
        buildVersions {
            versions(versions)
            spec()
        }
    }

    fun buildVersions(spec: VersionSpec.() -> Unit) {
        val versionSpec = VersionSpec()
        val mappings = versionSpec.accept(spec)
        versions(versionSpec.versions!!, mappings)
    }

    fun toMappingsSupplier(): MappingsSupplier {
        val suppliers = mutableListOf<MappingsSupplier>()
        this.versions.forEach { (builder, versions) ->
            var supplier = multipleSupplier(versions) {
                builder.build(it).container.build(it)
            }
            if (cached) {
                supplier = cachedSupplier({
                    builder.build(it).uuid
                }, supplier)
            }
            suppliers.add(supplier)
        }
        return when (suppliers.size) {
            0 -> EmptyMappingsSupplier
            1 -> suppliers.first()
            else -> ConcatMappingsSupplier(suppliers)
        }
    }
}
