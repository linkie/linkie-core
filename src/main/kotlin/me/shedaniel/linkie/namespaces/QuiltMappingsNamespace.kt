package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import me.shedaniel.linkie.*
import me.shedaniel.linkie.namespaces.YarnNamespace.loadIntermediaryFromTinyInputStream
import me.shedaniel.linkie.namespaces.YarnNamespace.loadNamedFromTinyInputStream
import me.shedaniel.linkie.utils.*
import org.dom4j.io.SAXReader
import java.net.URL
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

object QuiltMappingsNamespace : Namespace("quilt-mappings") {
	@Serializable
	data class QuiltMappingsBuild(
			val maven: String,
	)

	val quiltMappingsBuilds = mutableMapOf<String, QuiltMappingsBuild>()
	val latestQuiltMappingsVersion: String?
		get() = quiltMappingsBuilds.keys.filter { it.contains('.') && !it.contains("-") }
			.maxByOrNull { it.tryToVersion() ?: Version() }

	init {
		buildSupplier {
			cached()

			buildVersions {
				versions { quiltMappingsBuilds.keys }
				uuid { version ->
					quiltMappingsBuilds[version]!!.maven.let { it.substring(it.lastIndexOf(':') + 1) }
				}
				mappings {
					MappingsContainer(it, name = "QuiltMappings").apply {
						loadIntermediaryFromMaven(version)
						val quiltMappingsMaven = quiltMappingsBuilds[version]!!.maven
						mappingsSource = loadNamedFromMaven(quiltMappingsMaven.substring(quiltMappingsMaven.lastIndexOf(':') + 1), showError = false)
					}
				}
			}
		}
	}

	override fun getDefaultLoadedVersions(): List<String> {
		return emptyList()
	}

	override fun getAllVersions(): Sequence<String> = quiltMappingsBuilds.keys.asSequence()

	override fun supportsMixin(): Boolean = true
	override fun supportsAW(): Boolean = true

	override suspend fun reloadData() {
		val buildMap = LinkedHashMap<String, MutableList<QuiltMappingsBuild>>()

		val pom = URL("https://maven.quiltmc.org/repository/release/org/quiltmc/quilt-mappings/maven-metadata.xml").readText()
		SAXReader().read(pom.reader()).rootElement
				.element("versioning")
				.element("versions")
				.elementIterator("version")
				.asSequence()
				.map { it.text }
				.forEach {
					buildMap.computeIfAbsent(it.substring(0, it.lastIndexOf("+"))) {
						mutableListOf()
					}.add(QuiltMappingsBuild("org.quitlmc:quilt-mappings:$it"))
				}

		buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.maven.substring(it.maven.lastIndexOf(".") + 1, it.maven.length) }?.apply { quiltMappingsBuilds[version] = this } }
	}

	suspend fun MappingsContainer.loadIntermediaryFromMaven(
			mcVersion: String,
			repo: String = "https://maven.quiltmc.org/repository/release",
			group: String = "org.quiltmc.hashed",
	) =
			loadIntermediaryFromTinyJar(URL("$repo/${group.replace('.', '/')}/$mcVersion/hashed-$mcVersion.jar"))

	suspend fun MappingsContainer.loadIntermediaryFromTinyJar(url: URL) {
		url.toAsyncZip().forEachEntry { path, entry ->
			if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
				loadIntermediaryFromTinyInputStream(entry.bytes.inputStream(), "hashed")
			}
		}
	}

	suspend fun MappingsContainer.loadNamedFromMaven(
			quiltMappingsVersion: String,
			repo: String = "https://maven.quiltmc.org/repository/release",
			group: String = "org.quiltmc.quilt-mappings",
			id: String = "quilt-mappings",
			showError: Boolean = true,
	): MappingsSource {
        loadNamedFromTinyJar(URL("$repo/${group.replace('.', '/')}/$quiltMappingsVersion/$id-$quiltMappingsVersion-v2.jar"), showError)
        return MappingsSource.QUILT_MAPPINGS
	}

	suspend fun MappingsContainer.loadNamedFromTinyJar(url: URL, showError: Boolean = true) {
		url.toAsyncZip().forEachEntry { path, entry ->
			if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
				loadNamedFromTinyInputStream(entry.bytes.inputStream(), showError, "hashed")
			}
		}
	}
}
