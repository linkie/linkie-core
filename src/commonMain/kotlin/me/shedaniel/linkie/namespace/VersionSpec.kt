package me.shedaniel.linkie.namespace

import com.soywiz.korio.async.runBlockingNoJs
import me.shedaniel.linkie.buildMappings
import me.shedaniel.linkie.namespaces.ConstructingMappingsBuilder
import me.shedaniel.linkie.namespaces.ConstructingVersionedMappingsBuilder
import me.shedaniel.linkie.namespaces.MappingsBuilder
import me.shedaniel.linkie.namespaces.MappingsVersionBuilder
import me.shedaniel.linkie.namespaces.UuidGetter
import me.shedaniel.linkie.namespaces.ofVersion

class VersionSpec(
    var uuidGetter: UuidGetter = UuidGetter { it },
    var mappingsGetter: MappingsBuilder? = null,
    var versions: (() -> Iterable<String>)? = null,
) {
    fun uuid(uuid: String) {
        uuid { uuid }
    }

    fun uuid(uuid: UuidGetter) {
        uuidGetter = uuid
    }

    fun mappings(mappings: MappingsBuilder) {
        mappingsGetter = mappings
    }

    fun mappings(
        version: String,
        name: String,
        builder: ConstructingMappingsBuilder,
    ) {
        mappings { buildMappings(version, name, builder) }
    }

    fun mappings(
        name: String,
        builder: ConstructingVersionedMappingsBuilder,
    ) {
        mappings { buildMappings(it, name) { builder(it) } }
    }

    fun mappings(
        name: suspend (version: String) -> String,
        builder: ConstructingVersionedMappingsBuilder,
    ) {
        mappings { buildMappings(it, name(it)) { builder(it) } }
    }

    fun version(version: String) {
        val list = listOf(version)
        versionsItr { list }
    }

    fun versions(vararg versions: String) {
        val list = versions.toList()
        versionsItr { list }
    }

    fun versions(versions: Iterable<String>) {
        versionsItr { versions }
    }

    fun versionsItr(versions: () -> Iterable<String>) {
        this.versions = versions
    }

    fun versions(versions: () -> Sequence<String>) {
        this.versions = {
            val sequence = versions()
            Iterable { sequence.iterator() }
        }
    }

    inline fun accept(spec: VersionSpec.() -> Unit): MappingsVersionBuilder {
        also(spec)
        mappingsGetter!!

        return MappingsVersionBuilder { version ->
            ofVersion(runBlockingNoJs { uuidGetter.get(version) }, mappingsGetter!!)
        }
    }
}
