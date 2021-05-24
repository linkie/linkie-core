package me.shedaniel.linkie.namespace

import me.shedaniel.linkie.MappingsSupplier
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.cachedSupplier
import me.shedaniel.linkie.concat
import me.shedaniel.linkie.multipleSupplier
import me.shedaniel.linkie.namespaces.MappingsVersionBuilder

class MappingsSupplierBuilder(
    var cached: Boolean = false,
    var versions: MutableMap<MappingsVersionBuilder, () -> Iterable<String>> = mutableMapOf(),
) {
    fun cached() {
        cached = true
    }

    inline fun version(version: String, spec: VersionSpec.() -> Unit) {
        versions {
            version(version)
            spec()
        }
    }

    inline fun versions(spec: VersionSpec.() -> Unit) {
        val versionSpec = VersionSpec()
        val mappings = versionSpec.accept(spec)
        versions[mappings] = versionSpec.versions!!
    }

    internal fun toMappingsSupplier(namespace: Namespace): MappingsSupplier {
        val suppliers = mutableListOf<MappingsSupplier>()
        this.versions.forEach { (builder, versions) ->
            var supplier = multipleSupplier(versions) {
                builder.build(it).container.build(it)
            }
            if (cached) {
                supplier = namespace.cachedSupplier({
                    builder.build(it).uuid
                }, supplier)
            }
            suppliers.add(supplier)
        }
        return suppliers.concat()
    }
}
