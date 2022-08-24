package me.shedaniel.linkie.namespaces

import com.soywiz.korio.async.runBlockingNoJs
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsMember
import me.shedaniel.linkie.MappingsSource
import me.shedaniel.linkie.Namespace
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.jar.GameJarProvider
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.obfMergedOrOptimumName
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.utils.remapDescriptor
import me.shedaniel.linkie.utils.singleSequenceOf
import org.quiltmc.mappings_hasher.MappingsHasher
import org.quiltmc.mappings_hasher.org.cadixdev.lorenz.MappingSet
import java.io.Closeable
import java.util.jar.JarFile

object MojangHashedNamespace : Namespace("mojang_hashed") {
    override fun getDependencies(): Set<Namespace> = setOf(MojangNamespace)
    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): Sequence<String> = MojangNamespace.getAllVersions().filter {
        runBlockingNoJs { jarProvider?.canProvideVersion(it) == true }
    }

    override suspend fun reloadData() = Unit
    override fun supportsMixin(): Boolean = true
    override fun supportsAW(): Boolean = true
    override fun supportsSource(): Boolean = true

    init {
        buildSupplier {
            cached()

            buildVersions {
                versionsSeq(MojangNamespace::getAllVersions)
                mappings { version ->
                    MojangNamespace.getProvider(version).get()
                        .clone()
                        .copy(version = version, name = "Mojang (Hashed)", mappingsSource = MappingsSource.MOJANG_HASHED)
                        .also { it.hash(version) }
                }
            }
        }
    }

    private suspend fun MappingsContainer.hash(version: String) {
        val result = jarProvider!!.provide(version)
        val original = MappingSet.create().also { set ->
            allClasses.forEach { clazz ->
                val mapping = set.getOrCreateClassMapping(clazz.obfMergedName ?: clazz.optimumName)
                    .setDeobfuscatedName(clazz.optimumName)
                clazz.methods.forEach { method ->
                    mapping.getOrCreateMethodMapping(method.obfMergedName ?: method.optimumName, method.getObfOrOptimalMergedDesc(this))
                        .deobfuscatedName = method.optimumName
                }
                clazz.fields.forEach { field ->
                    mapping.getOrCreateFieldMapping(field.obfMergedName ?: field.optimumName, field.getObfOrOptimalMergedDesc(this))
                        .deobfuscatedName = field.optimumName
                }
            }
        }
        val hasher = MappingsHasher(original, "net/minecraft/unmapped")
        val toClose = mutableListOf<Closeable>()
        result.libraries.forEach {
            hasher.addLibrary(JarFile(it.absolutePath).also(toClose::add))
        }
        val set = hasher.generate(JarFile(result.minecraftFile.absolutePath).also(toClose::add)) { original.getClassMapping(it.name()).isPresent }
        toClose.forEach(Closeable::close)
        classes.clear()
        set.topLevelClassMappings.asSequence()
            .flatMap { singleSequenceOf(it) + it.innerClassMappings }
            .forEach { mapping ->
                val clazz = getOrCreateClass(mapping.fullDeobfuscatedName)
                if (mapping.hasDeobfuscatedName()) {
                    clazz.obfMergedName = mapping.fullObfuscatedName
                }

                val originalClass = original.getClassMapping(mapping.fullObfuscatedName).orElse(null)
                val mappedName = originalClass?.fullDeobfuscatedName
                if (mappedName != clazz.intermediaryName) {
                    clazz.mappedName = mappedName
                }
                mapping.methodMappings.forEach { methodMapping ->
                    val method = clazz.getOrCreateMethod(methodMapping.deobfuscatedName, methodMapping.deobfuscatedDescriptor)
                    if (methodMapping.hasDeobfuscatedName()) {
                        method.obfMergedName = methodMapping.obfuscatedName
                    }
                    val mappedMethodName = originalClass?.getMethodMapping(methodMapping.signature)?.orElse(null)
                        ?.deobfuscatedName
                    if (mappedMethodName != method.intermediaryName) {
                        method.mappedName = mappedMethodName
                    }
                }
                mapping.fieldMappings.forEach { fieldMapping ->
                    val field = clazz.getOrCreateField(fieldMapping.deobfuscatedName, fieldMapping.deobfuscatedSignature.type.get().toString())
                    if (fieldMapping.hasDeobfuscatedName()) {
                        field.obfMergedName = fieldMapping.obfuscatedName
                    }
                    val mappedFieldName = originalClass?.getFieldMapping(fieldMapping.signature)?.orElse(null)
                        ?.deobfuscatedName
                    if (mappedFieldName != field.intermediaryName) {
                        field.mappedName = mappedFieldName
                    }
                }
            }
    }

    private fun MappingsMember.getObfOrOptimalMergedDesc(container: MappingsContainer): String =
        intermediaryDesc.remapDescriptor { container.getClass(it)?.obfMergedOrOptimumName ?: it }
}
