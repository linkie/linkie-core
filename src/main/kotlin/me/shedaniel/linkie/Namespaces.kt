package me.shedaniel.linkie

import com.soywiz.korio.file.VfsFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.shedaniel.linkie.jar.GameJarProvider
import me.shedaniel.linkie.utils.debug
import me.shedaniel.linkie.utils.getMillis
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

object Namespaces {
    var config by Delegates.notNull<LinkieConfig>()
    val namespaces = LinkedHashMap<String, Namespace>()
    val cachedMappings = CopyOnWriteArrayList<MappingsContainer>()
    var gameJarProvider: GameJarProvider? = null
    val cacheFolder: VfsFile
        get() = config.cacheDirectory

    private fun registerNamespace(namespace: Namespace) = namespace.also {
        namespaces[it.id] = it
    }

    operator fun get(id: String) = namespaces[id]!!

    fun getMaximumCachedVersion(): Int = config.maximumLoadedVersions

    fun limitCachedData() {
        val list = mutableListOf<String>()
        while (cachedMappings.size > getMaximumCachedVersion()) {
            val first = cachedMappings.first()
            cachedMappings.remove(first)
            list.add(first.let { "${it.namespace}-${it.version}" })
        }
        System.gc()
        debug("Removed ${list.size} Mapping(s): " + list.joinToString(", "))
    }

    fun addMappingsContainer(mappingsContainer: MappingsContainer) {
        cachedMappings.add(mappingsContainer)
        limitCachedData()
        debug("Currently Loaded ${cachedMappings.size} Mapping(s): " + cachedMappings.joinToString(", ") {
            "${it.namespace}-${it.version}"
        })
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    fun init(
        config: LinkieConfig,
    ) {
        fun registerNamespace(namespace: Namespace) {
            namespace.getDependencies().forEach { registerNamespace(it) }
            Namespaces.registerNamespace(namespace)
        }
        Namespaces.config = config
        gameJarProvider = config.gameJarProvider?.let { it(config) }
        config.namespaces.forEach { registerNamespace(it) }
        val cycleMs = config.reloadCycleDuration.millisecondsLong

        var nextDelay = getMillis() - cycleMs
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                if (getMillis() > nextDelay + cycleMs) {
                    cachedMappings.clear()
                    namespaces.map { (_, namespace) ->
                        launch {
                            namespace.reset()
                        }
                    }.forEach { it.join() }
                    nextDelay = getMillis()
                }
                delay(1000)
            }
        }
    }
}