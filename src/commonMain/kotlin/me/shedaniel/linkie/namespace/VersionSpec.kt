package me.shedaniel.linkie.namespace

import com.soywiz.korio.async.runBlockingNoJs
import me.shedaniel.linkie.Mappings
import me.shedaniel.linkie.MappingsConstructingBuilder
import me.shedaniel.linkie.namespaces.MappingsBuilder
import me.shedaniel.linkie.namespaces.MappingsVersionBuilder
import me.shedaniel.linkie.namespaces.ofVersion
import me.shedaniel.linkie.namespaces.toBuilder

class VersionSpec(
    private var uuidGetter: suspend (String) -> String = { it },
    private var mappingsGetter: MappingsBuilder? = null,
    internal var versions: (() -> Iterable<String>)? = null,
) {
    fun uuid(uuid: String) {
        uuid { uuid }
    }

    fun uuid(uuid: suspend (String) -> String) {
        uuidGetter = uuid
    }

    fun mappings(mappings: Mappings) {
        mappings { mappings }
    }

    fun mappings(mappings: MappingsBuilder) {
        mappingsGetter = mappings
    }

    fun mappings(mappings: suspend (String) -> Mappings) {
        mappingsGetter = toBuilder(mappings)
    }

    fun buildMappings(
        version: String,
        name: String,
        fillFieldDesc: Boolean = true,
        fillMethodDesc: Boolean = true,
        builder: suspend MappingsConstructingBuilder.() -> Unit,
    ) {
        mappings { me.shedaniel.linkie.buildMappings(version, name, fillFieldDesc, fillMethodDesc, builder) }
    }

    fun buildMappings(
        name: String,
        fillFieldDesc: Boolean = true,
        fillMethodDesc: Boolean = true,
        builder: suspend MappingsConstructingBuilder.(version: String) -> Unit,
    ) {
        mappings { me.shedaniel.linkie.buildMappings(it, name, fillFieldDesc, fillMethodDesc) { builder(it) } }
    }

    fun buildMappings(
        name: suspend (version: String) -> String,
        fillFieldDesc: Boolean = true,
        fillMethodDesc: Boolean = true,
        builder: suspend MappingsConstructingBuilder.(version: String) -> Unit,
    ) {
        mappings { me.shedaniel.linkie.buildMappings(it, name(it), fillFieldDesc, fillMethodDesc) { builder(it) } }
    }

    fun version(version: String) {
        val list = listOf(version)
        versions { list }
    }

    fun versions(vararg versions: String) {
        val list = versions.toList()
        versions { list }
    }

    fun versions(versions: Iterable<String>) {
        versions { versions }
    }

    fun versions(versions: () -> Iterable<String>) {
        this.versions = versions
    }

    fun versionsSeq(versions: () -> Sequence<String>) {
        this.versions = {
            val sequence = versions()
            Iterable { sequence.iterator() }
        }
    }

    fun accept(spec: VersionSpec.() -> Unit): MappingsVersionBuilder {
        also(spec)
        mappingsGetter!!

        return MappingsVersionBuilder {
            ofVersion(runBlockingNoJs { uuidGetter(it) }, mappingsGetter!!)
        }
    }
}
