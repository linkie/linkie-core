package me.shedaniel.linkie

import com.soywiz.korio.file.VfsFile
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.shedaniel.linkie.Namespaces.config
import me.shedaniel.linkie.jar.GameJarProvider
import me.shedaniel.linkie.namespaces.*
import me.shedaniel.linkie.source.QfResultSaver
import me.shedaniel.linkie.utils.*
import net.fabricmc.stitch.util.StitchUtil
import net.fabricmc.tinyremapper.IMappingProvider
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper
import org.jetbrains.java.decompiler.main.Fernflower
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import org.objectweb.asm.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import javax.lang.model.SourceVersion
import kotlin.collections.HashMap
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.Set
import kotlin.collections.any
import kotlin.collections.asReversed
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.toList
import kotlin.collections.toTypedArray


abstract class Namespace(val id: String) {
    val jarProvider: GameJarProvider? by lazy { Namespaces.gameJarProvider }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false
        return id == (other as Namespace).id
    }

    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = id

    open fun getDependencies(): Set<Namespace> = setOf()

    private val mappingsSuppliers = mutableListOf<MappingsSupplier>()
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    val reloading: Boolean
        get() = selfReloading || getDependencies().any { it.reloading }
    private var selfReloading = false

    suspend fun reset() {
        selfReloading = true
        try {
            reloadData()
            val jobs = getDefaultLoadedVersions().map {
                GlobalScope.launch {
                    val provider = getProvider(it)
                    if (provider.isEmpty().not() && provider.cached != true)
                        provider.get().also {
                            Namespaces.cachedMappings.remove(it)
                        }
                }
            }
            jobs.forEach { it.join() }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        selfReloading = false
    }

    abstract fun getDefaultLoadedVersions(): List<String>
    abstract fun getAllVersions(): Sequence<String>
    abstract suspend fun reloadData()
    open val defaultVersion: String? get() =
        getAllVersions().mapNotNull { it.tryToVersion()?.takeIf { version -> version.snapshot == null }?.let { version -> it to version } }.maxByOrNull { it.first }?.first
            ?: getAllVersions().maxWithOrNull(nullsFirst(compareBy(String::tryToVersion)))!!

    fun getAllSortedVersions(): List<String> =
        getAllVersions().sortedWith(nullsFirst(compareBy { it.tryToVersion() })).toList().asReversed()

    protected fun registerSupplier(mappingsSupplier: MappingsSupplier) {
        mappingsSuppliers.add(namespacedSupplier(loggingSupplier(mappingsSupplier)))
    }

    protected inline fun buildSupplier(builder: MappingsSupplierBuilder.() -> Unit) {
        registerSupplier(MappingsSupplierBuilder().also(builder).toMappingsSupplier())
    }

    protected inner class MappingsSupplierBuilder(
        var cached: Boolean = false,
        var versions: MutableMap<MappingsVersionBuilder, () -> Iterable<String>> = mutableMapOf(),
    ) {
        fun cached() {
            cached = true
        }

        fun version(version: String, uuid: String, mappings: MappingsContainer) {
            version(version, uuid) { mappings }
        }

        fun version(version: String, uuid: (String) -> String, mappings: MappingsContainer) {
            version(version, uuid) { mappings }
        }

        fun version(version: String, uuid: String, mappings: MappingsContainerBuilder) {
            val list = listOf(version)
            versions(list, uuid, mappings)
        }

        fun version(version: String, uuid: (String) -> String, mappings: MappingsContainerBuilder) {
            val list = listOf(version)
            versions(list, uuid, mappings)
        }

        fun version(version: String, uuid: String, mappings: suspend (String) -> MappingsContainer) {
            version(version, uuid, toBuilder(mappings))
        }

        fun version(version: String, uuid: (String) -> String, mappings: suspend (String) -> MappingsContainer) {
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

        fun versions(vararg versions: String, uuid: String, mappings: MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: (String) -> String, mappings: MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: String, mappings: MappingsContainerBuilder) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: (String) -> String, mappings: MappingsContainerBuilder) {
            val list = versions.toList()
            versions(list, uuid, mappings)
        }

        fun versions(vararg versions: String, uuid: String, mappings: suspend (String) -> MappingsContainer) {
            val list = versions.toList()
            versions(list, uuid, toBuilder(mappings))
        }

        fun versions(vararg versions: String, uuid: (String) -> String, mappings: suspend (String) -> MappingsContainer) {
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

        fun versions(versions: Iterable<String>, uuid: String, mappings: MappingsContainer) {
            versions(versions, uuid) { mappings }
        }

        fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: MappingsContainer) {
            versions(versions, uuid) { mappings }
        }

        fun versions(versions: Iterable<String>, uuid: String, mappings: suspend (String) -> MappingsContainer) {
            versions(versions, uuid, toBuilder(mappings))
        }

        fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: suspend (String) -> MappingsContainer) {
            versions(versions, uuid, toBuilder(mappings))
        }

        fun versions(versions: Iterable<String>, uuid: String, mappings: MappingsContainerBuilder) {
            versions(versions) { ofVersion(uuid, mappings) }
        }

        fun versions(versions: Iterable<String>, uuid: (String) -> String, mappings: MappingsContainerBuilder) {
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

    inner class VersionSpec(
        private var uuidGetter: suspend (String) -> String = { it },
        private var mappingsGetter: MappingsContainerBuilder? = null,
        internal var versions: (() -> Iterable<String>)? = null,
    ) {
        fun uuid(uuid: String) {
            uuid { uuid }
        }

        fun uuid(uuid: suspend (String) -> String) {
            uuidGetter = uuid
        }

        fun mappings(mappings: MappingsContainer) {
            mappings { mappings }
        }

        fun mappings(mappings: MappingsContainerBuilder) {
            mappingsGetter = mappings
        }

        fun mappings(mappings: suspend (String) -> MappingsContainer) {
            mappingsGetter = toBuilder(mappings)
        }

        fun buildMappings(
            version: String,
            name: String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            builder: suspend MappingsBuilder.() -> Unit,
        ) {
            mappings { me.shedaniel.linkie.buildMappings(version, name, fillFieldDesc, fillMethodDesc, builder) }
        }

        fun buildMappings(
            name: String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            builder: suspend MappingsBuilder.(version: String) -> Unit,
        ) {
            mappings { me.shedaniel.linkie.buildMappings(it, name, fillFieldDesc, fillMethodDesc) { builder(it) } }
        }

        fun buildMappings(
            name: suspend (version: String) -> String,
            fillFieldDesc: Boolean = true,
            fillMethodDesc: Boolean = true,
            builder: suspend MappingsBuilder.(version: String) -> Unit,
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
                ofVersion(runBlocking { uuidGetter(it) }, mappingsGetter!!)
            }
        }
    }
    
    fun hasProvider(version: String): Boolean {
        return mappingsSuppliers.any { it.isApplicable(version) }
    }

    suspend fun getProvider(version: String): MappingsProvider {
        val entry = mappingsSuppliers.firstOrNull { it.isApplicable(version) } ?: return MappingsProvider.empty(this)
        return MappingsProvider.supply(this, version, entry.isCached(version)) { entry.applyVersion(version) }
    }

    suspend fun getDefaultProvider(): MappingsProvider {
        return getProvider(defaultVersion!!)
    }

    open fun supportsMixin(): Boolean = false
    open fun supportsAT(): Boolean = false
    open fun supportsAW(): Boolean = false
    open fun supportsFieldDescription(): Boolean = true
    open fun supportsSource(): Boolean = false
    
    data class PreSource(
        val result: GameJarProvider.Result,
        val remappedJar: VfsFile,
        val sourcesDir: VfsFile,
        val ff: () -> Fernflower
    )

    suspend fun preGetSource(mappings: MappingsContainer, version: String, threads: Int): PreSource {
        val result = jarProvider!!.provide(version)
        val remappedJarRaw = (config.cacheDirectory / "minecraft-jars" / "remapped").also { it.mkdirs() } / "$id-$version.jar"
        val remappedJarResult = getRemappedJar(version, remappedJarRaw, result, mappings)
        if (remappedJarResult.isFailure) {
            remappedJarRaw.delete()
            throw remappedJarResult.exceptionOrNull()!!
        }
        val remappedJar = remappedJarResult.getOrThrow()
        val sourcesDir = (config.cacheDirectory / "minecraft-jars" / "sources" / "$id-$version").also { it.mkdirs() }
        return PreSource(result, remappedJar, sourcesDir) {
            createFF(result, remappedJar, sourcesDir, threads)
        }
    }
    
    fun createFFOptions(threads: Int): Map<String, String> {
        val options = mutableMapOf<String, String>()
        options[IFernflowerPreferences.INDENT_STRING] = "\t"
        options[IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES] = "1"
        options[IFernflowerPreferences.BYTECODE_SOURCE_MAPPING] = "1"
        options[IFernflowerPreferences.LOG_LEVEL] = "warn"
        options[IFernflowerPreferences.REMOVE_BRIDGE] = "0"
        options[IFernflowerPreferences.REMOVE_SYNTHETIC] = "0"
        options[IFernflowerPreferences.THREADS] = threads.toString()
        return options
    }
    
    fun createFF(result: GameJarProvider.Result, remappedJar: VfsFile, sourcesDir: VfsFile, threads: Int): Fernflower {
        val options = createFFOptions(threads)

        val ff = Fernflower({ outer, inner -> getBytes(outer, inner) },
            QfResultSaver(File(sourcesDir.absolutePath)), options as Map<String, Any>, PrintStreamLogger(System.out)
        )

        for (library in result.libraries) {
            ff.addLibrary(File(library.absolutePath))
        }

        ff.addSource(File(remappedJar.absolutePath))
        return ff
    }

    suspend fun getSource(mappings: MappingsContainer, version: String, className: String): VfsFile {
        val (_, remappedJar, sourcesDir, ffProvider) = preGetSource(mappings, version, 4)
        val sourcesFile = sourcesDir / "$className.java"
        val alternativeSourcesFile = sourcesDir / "${className.substringBeforeLast("/") + "$" + className.substringAfterLast("/")}.java"
        if (sourcesFile.exists()) return sourcesFile
        if (alternativeSourcesFile.exists()) return alternativeSourcesFile

        val ff = ffProvider()
        ff.addWhitelist(className)
        ff.addWhitelist(className.substringBeforeLast("/") + "$" + className.substringAfterLast("/"))
        ff.decompileContext()

        val output = alternativeSourcesFile.takeIfExists() ?: sourcesFile

        RemapperDaemon.updateCacheJson(this, version, remappedJar, 1)

        return output
    }

    suspend fun getAllSource(mappings: MappingsContainer, version: String) {
        val (_, remappedJar, _, ffProvider) = preGetSource(mappings, version, 1)

        try {
            val ff = ffProvider()
            ff.decompileContext()
        } catch (e: Throwable) {
            RemapperDaemon.updateCacheJson(this, version, remappedJar, null, true)
            throw e
        }

        RemapperDaemon.updateCacheJson(this, version, remappedJar, null)
    }

    private suspend fun Namespace.getRemappedJar(
        version: String,
        remappedJar: VfsFile,
        gameJars: GameJarProvider.Result,
        mappings: MappingsContainer
    ) = runCatching {
        val filteredJar = config.cacheDirectory / "minecraft-jars" / "$version-client-filtered.jar"
        if (remappedJar.exists()) return@runCatching remappedJar
        if (!filteredJar.exists()) {
            JarOutputStream(Files.newOutputStream(Paths.get(filteredJar.absolutePath))).use {
                ZipFile(gameJars.minecraftFile.readBytes()).forEachEntry { path, entry ->
                    if (path.endsWith(".class")) {
                        val reader = ClassReader(entry.bytes)
                        val writer = ClassWriter(0)
                        reader.accept(object : ClassVisitor(Opcodes.ASM9, writer) {
                            override fun visitMethod(
                                access: Int,
                                name: String?,
                                descriptor: String?,
                                signature: String?,
                                exceptions: Array<out String>?
                            ): MethodVisitor {
                                return object : MethodVisitor(
                                    api,
                                    super.visitMethod(access, name, descriptor, signature, exceptions)
                                ) {
                                    override fun visitLocalVariable(
                                        name: String?,
                                        descriptor: String?,
                                        signature: String?,
                                        start: Label?,
                                        end: Label?,
                                        index: Int
                                    ) {
                                    }

                                    override fun visitParameter(name: String?, access: Int) {
                                    }
                                }
                            }
                        }, 0)
                        it.putNextEntry(ZipEntry(path))
                        it.write(writer.toByteArray())
                        it.closeEntry()
                    }
                }
            }
        }
        val remapper = TinyRemapper.newRemapper()
            .withMappings { accepter ->
                mappings.allClasses.forEach { clazz ->
                    if (clazz.obfMergedName == null) return@forEach
                    accepter.acceptClass(
                        clazz.obfMergedName,
                        clazz.optimumName.takeIf(String::isNotBlank) ?: clazz.obfMergedName
                    )

                    clazz.methods.forEach { method ->
                        if (method.obfMergedName == null) return@forEach
                        val member = IMappingProvider.Member(
                            clazz.obfMergedName,
                            method.obfMergedName,
                            method.getObfMergedDesc(mappings)
                        )
                        accepter.acceptMethod(
                            member,
                            method.optimumName.takeIf(String::isNotBlank) ?: method.obfMergedName
                        )

                        val nameCounts: MutableMap<String, Int> = HashMap()

                        method.args?.forEach { arg ->
                            nameCounts[arg.name] = nameCounts.getOrPut(arg.name) { 0 } + 1
                        }

                        val methodParameterDesc = member.desc.substringAfter('(').substringBefore(')')
                        var index = 0

                        methodParameterDesc.forEachDescriptor {
                            index++
                        }

                        val size = index
                        val args = arrayOfNulls<String>(size + 1)

                        method.args?.forEach { arg ->
                            accepter.acceptMethodArg(member, arg.index, arg.name)
                        }
    
    //                            args.forEachIndexed { index, arg ->
    //                                if (arg != null) {
    //                                    accepter.acceptMethodArg(member, index, arg)
    //                                }
    //                            }
                    }

                    clazz.fields.forEach { field ->
                        if (field.obfMergedName == null) return@forEach
                        accepter.acceptField(
                            IMappingProvider.Member(
                                clazz.obfMergedName,
                                field.obfMergedName,
                                field.getObfMergedDesc(mappings)
                            ), field.optimumName.takeIf(String::isNotBlank) ?: field.obfMergedName
                        )
                    }
                }
            }
            .threads(4)
            .build()
        remapper.readClassPath(*gameJars.libraries.map { Paths.get(it.absolutePath) }.toTypedArray())
        remapper.readInputs(Paths.get(filteredJar.absolutePath))
        OutputConsumerPath.Builder(Paths.get(remappedJar.absolutePath)).build().use { path ->
            remapper.apply(path)
        }
        remapper.finish()
        return@runCatching remappedJar
    }

    @Throws(IOException::class)
    open fun getBytes(outerPath: String, innerPath: String?): ByteArray? {
        if (innerPath == null) {
            return Files.readAllBytes(Paths.get(outerPath))
        }
        StitchUtil.getJarFileSystem(File(outerPath), false).use { fs -> return Files.readAllBytes(fs.get().getPath(innerPath)) }
    }

    private fun getNameFromType(nameCounts: MutableMap<String, Int>, type: String, isArg: Boolean): String? {
        var type = type
        var plural = false
        if (type[0] == '[') {
            plural = true
            type = type.substring(type.lastIndexOf('[') + 1)
        }
        var incrementLetter = true
        var varName: String?
        when (type[0]) {
            'B' -> varName = "b"
            'C' -> varName = "c"
            'D' -> varName = "d"
            'F' -> varName = "f"
            'I' -> varName = "i"
            'J' -> varName = "l"
            'S' -> varName = "s"
            'Z' -> {
                varName = "bl"
                incrementLetter = false
            }

            'L' -> {
                // strip preceding packages and outer classes
                var start = type.lastIndexOf('/') + 1
                val startDollar = type.lastIndexOf('$') + 1
                if (startDollar > start && startDollar < type.length - 1) {
                    start = startDollar
                } else if (start == 0) {
                    start = 1
                }

                // assemble, lowercase first char, apply plural s
                val first = type[start]
                val firstLc = first.lowercaseChar()
                varName = if (first == firstLc) { // type is already lower case, the var name would shade the type
                    null
                } else {
                    firstLc.toString() + type.substring(start + 1, type.length - 1)
                }

                // Only check for invalid identifiers, keyword check is performed below
                if (varName == null || !varName.isValidJavaIdentifier()) {
                    varName = if (isArg) "arg" else "lv" // lv instead of var to avoid confusion with Java 10's var keyword
                }
                incrementLetter = false
            }

            else -> throw IllegalStateException()
        }
        var hasPluralS = false
        if (plural) {
            val pluralVarName = varName + 's'

            // Appending 's' could make name invalid, e.g. "clas" -> "class" (keyword)
            if (!isJavaKeyword(pluralVarName)) {
                varName = pluralVarName
                hasPluralS = true
            }
        }
        return if (incrementLetter) {
            var index = -1
            while (nameCounts.putIfAbsent(varName!!, 1) != null || isJavaKeyword(varName)) {
                if (index < 0) index = getNameIndex(varName, hasPluralS)
                varName = getIndexName(++index, plural)
            }
            varName
        } else {
            val baseVarName = varName
            var count: Int = nameCounts.compute(baseVarName) { _, v -> if (v == null) 1 else v + 1 }!!
            if (count == 1) {
                varName += if (isJavaKeyword(baseVarName)) {
                    '_'
                } else {
                    return varName // name does not exist yet, so can return fast here
                }
            } else {
                varName = baseVarName + Integer.toString(count)
            }

            /*
                     * Check if name is not taken yet, count only indicates where to continue
                     * numbering for baseVarName, but does not guarantee that there is no
                     * other variable which already has that name, e.g.:
                     * (MyClass ?, MyClass2 ?, MyClass ?) -> (MyClass myClass, MyClass2 myClass2, !myClass2 is already taken!)
                     */while (nameCounts.putIfAbsent(varName!!, 1) != null) {
                varName = baseVarName + Integer.toString(count++)
            }
            nameCounts.put(baseVarName, count) // update name count
            varName
        }
    }

    private fun getNameIndex(name: String, plural: Boolean): Int {
        var ret = 0
        var i = 0
        val max = name.length - if (plural) 1 else 0
        while (i < max) {
            ret = ret * 26 + name[i].code - 'a'.code + 1
            i++
        }
        return ret - 1
    }

    private val singleCharStrings = arrayOf(
        "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
        "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z"
    )

    private fun getIndexName(index: Int, plural: Boolean): String {
        var index = index
        return if (index < 26 && !plural) {
            singleCharStrings[index]
        } else {
            val ret = StringBuilder(2)
            do {
                val next = index / 26
                val cur = index - next * 26
                ret.append(('a'.code + cur).toChar())
                index = next - 1
            } while (index >= 0)
            ret.reverse()
            if (plural) ret.append('s')
            ret.toString()
        }
    }

    private fun isJavaKeyword(s: String): Boolean {
        // TODO: Use SourceVersion.isKeyword(CharSequence, SourceVersion) in Java 9
        //       to make it independent from JDK version
        return SourceVersion.isKeyword(s)
    }
}