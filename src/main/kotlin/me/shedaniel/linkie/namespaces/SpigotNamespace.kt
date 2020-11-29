package me.shedaniel.linkie.namespaces

import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsContainerBuilder
import me.shedaniel.linkie.Namespace
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URL

object SpigotNamespace : Namespace("spigot") {
    init {
        buildSupplier {
            cached()

            buildVersion("1.8.9") {
                buildMappings(it, "Spigot", fillFieldDesc = false) {
                    loadClassFromSpigot(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.15.2-cl.csrg?at=refs%2Fheads%2Fmaster").openStream())
                    loadMembersFromSpigot(URL("https://hub.spigotmc.org/stash/projects/SPIGOT/repos/builddata/raw/mappings/bukkit-1.15.2-members.csrg?at=refs%2Fheads%2Fmaster").openStream())
                    source(MappingsContainer.MappingSource.SPIGOT)
                }
            }
        }
    }

    override fun getDefaultLoadedVersions(): List<String> = listOf()
    override fun getAllVersions(): List<String> = listOf("1.8.9")
    override fun reloadData() {}
    override fun getDefaultVersion(channel: () -> String): String = "1.8.9"

    private fun MappingsContainerBuilder.loadClassFromSpigot(stream: InputStream) {
        InputStreamReader(stream).forEachLine {
            val split = it.split(' ')
            clazz(split[1], split[0])
        }
    }

    private fun MappingsContainerBuilder.loadMembersFromSpigot(stream: InputStream) {
        InputStreamReader(stream).forEachLine {
            val split = it.split(' ')
            if (split.size == 3) {
                clazz(split[0]) {
                    field(split[2]) {
                        obfField(split[1])
                    }
                }
            } else if (split.size == 4) {
                clazz(split[0]) {
                    method(split[3], split[2]) {
                        obfMethod(split[1])
                    }
                }
            }
        }
    }
}