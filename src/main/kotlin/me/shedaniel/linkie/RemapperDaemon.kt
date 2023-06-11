package me.shedaniel.linkie

import com.soywiz.klock.seconds
import com.soywiz.korio.async.runBlockingNoJs
import com.soywiz.korio.file.VfsFile
import com.soywiz.korio.file.extension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import me.shedaniel.linkie.utils.*

object RemapperDaemon {
    val remapQueue: Sequence<RemapPair>
        get() = Namespaces.namespaces.values.asSequence()
            .filter { it.supportsSource() }
            .flatMap { ns -> ns.getAllVersions().map { RemapPair(ns, it, getCache(ns, it)) } }
            .sortedWith(compareBy<RemapPair> {
                when {
                    it.version.tryToVersion() == null -> 2
                    it.version.tryToVersion()!!.snapshot != null -> 1
                    else -> 0
                }
            }.then(compareBy {
                when {
                    it.cache != null && it.cache.failed -> 2
                    it.cache == null || it.cache.classes < it.cache.totalClasses * 0.3 -> 0
                    else -> 1
                }
            }).then(compareByDescending { (_, version, _) ->
                version.tryToVersion() ?: "0.0.0".toVersion()
            }).thenBy { (_, _, cache) ->
                cache?.takeIf { it.totalClasses > 0 }?.let { it.classes.toDouble() / it.totalClasses }
                    ?: 0.0
            })
    val nextInQueue: Pair<Namespace, MappingsProvider>?
        get() = remapQueue
            .mapNotNull { (ns, version, _) ->
                runBlockingNoJs { ns.getProvider(version) }.takeUnless(MappingsProvider::isEmpty)
                    ?.let { ns to it }
            }
            .firstOrNull()
    
    var current: Pair<Namespace, MappingsProvider>? = null

    data class RemapPair(
        val ns: Namespace,
        val version: String,
        val cache: Cache?,
    )

    fun init() {
        Namespaces.config.remapSourceDaemonDuration?.let { duration ->
            val cycleMs = duration.millisecondsLong

            var nextDelay = getMillis() - cycleMs + 20.seconds.millisecondsLong
            CoroutineScope(Dispatchers.Default).launch {
                while (true) {
                    if (getMillis() > nextDelay + cycleMs) {
                        try {
                            remapNext()
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                        nextDelay = getMillis()
                    }
                    delay(1000)
                }
            }
        }
    }

    suspend fun remapNext() {
        val (namespace, provider) = nextInQueue?.also { 
            this.current = it
        } ?: return
        info("Selected ${namespace.id} ${provider.version} for remapping as a background task, the coming queue is as follows:")
        remapQueue.take(5).forEach { (ns, version, cache) ->
            info("  - ${ns.id} $version (${cache?.classes ?: "unknown"} classes remapped, ${cache?.totalClasses ?: "unknown"} total classes)")
        }
        try {
            val mappings = provider.get()
            namespace.getAllSource(mappings, provider.version!!)
        } finally {
            this.current = null
        }
    }

    suspend fun updateCacheJson(
        namespace: Namespace,
        version: String,
        remappedJar: VfsFile,
        add: Int?,
        failed: Boolean = false,
    ) {
        val sourcesDir = Namespaces.config.cacheDirectory / "minecraft-jars" / "sources" / "${namespace.id}-$version"
        sourcesDir.mkdirs()
        val cacheJson = sourcesDir / "cache.json"
        val cache: Cache = when {
            cacheJson.exists() && add != null -> namespace.json.decodeFromString(
                Cache.serializer(),
                cacheJson.readString()
            )

            else -> countClasses(remappedJar, sourcesDir).let {
                if (add != null) it.copy(classes = it.classes + add)
                else it
            }
        }
        cacheJson.writeString(namespace.json.encodeToString(Cache.serializer(), cache.copy(failed = failed)))
    }

    private suspend fun countClasses(remappedJar: VfsFile, sourcesDir: VfsFile): Cache {
        var classes = 0
        var totalClasses = 0
        sourcesDir.listRecursiveSimple {
            if (it.extension == "java") {
                classes++
            }
            true
        }
        ZipFile(remappedJar.readBytes()).forEachEntry { path, entry ->
            if (path.endsWith(".class")) {
                totalClasses++
            }
        }
        return Cache(classes, totalClasses, false)
    }

    fun getCache(namespace: Namespace, version: String): Cache? = runBlockingNoJs {
        val sourcesDir = Namespaces.config.cacheDirectory / "minecraft-jars" / "sources" / "${namespace.id}-$version"
        if (!sourcesDir.exists()) return@runBlockingNoJs null
        val cacheJson = sourcesDir / "cache.json"
        if (!cacheJson.exists()) return@runBlockingNoJs null
        return@runBlockingNoJs namespace.json.decodeFromString(Cache.serializer(), cacheJson.readString())
    }

    @Serializable
    data class Cache(
        var classes: Int,
        val totalClasses: Int,
        var failed: Boolean = false,
    )
}