package me.shedaniel.linkie.namespaces

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import me.shedaniel.linkie.*
import me.shedaniel.linkie.utils.*
import org.dom4j.io.SAXReader
import java.io.InputStream
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
		get() = quiltMappingsBuilds.keys.stream().map { it.tryToVersion() }.filter { it.toString().last().isDigit() && !it.toString().contains("-") }.sorted { a, b -> b!!.compareTo(a!!) }.findFirst().map { it.toString() }.get()

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
		return latestQuiltMappingsVersion?.let(::listOf) ?: listOf()
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
				.map { "org.quitlmc:quilt-mappings:${it.text}" }
				.forEach {
					buildMap.computeIfAbsent(it.substring(it.lastIndexOf(":") + 1, it.lastIndexOf("+"))) {
						mutableListOf()
					}.add(QuiltMappingsBuild(it))
				}

		buildMap.forEach { (version, builds) -> builds.maxByOrNull { it.maven.substring(it.maven.lastIndexOf(".") + 1, it.maven.length) }?.apply { quiltMappingsBuilds[version] = this } }
	}

	override fun getDefaultVersion(channel: () -> String): String =
			when (channel()) {
				"patchwork" -> "1.14.4"
				"snapshot" -> quiltMappingsBuilds.keys.first()
				else -> latestQuiltMappingsVersion!!
			}

	override fun getAvailableMappingChannels(): List<String> = listOf(
			"release",
			"snapshot",
			"patchwork",
	)

	suspend fun MappingsContainer.loadIntermediaryFromMaven(
			mcVersion: String,
			repo: String = "https://maven.quiltmc.org/repository/release",
			group: String = "org.quiltmc.hashed",
	) =
			loadIntermediaryFromTinyJar(URL("$repo/${group.replace('.', '/')}/$mcVersion/hashed-$mcVersion.jar"))

	suspend fun MappingsContainer.loadIntermediaryFromTinyJar(url: URL) {
		url.toAsyncZip().forEachEntry { path, entry ->
			if (!entry.isDirectory && path.split("/").lastOrNull() == "mappings.tiny") {
				loadIntermediaryFromTinyInputStream(entry.bytes.inputStream())
			}
		}
	}

	suspend fun MappingsContainer.loadIntermediaryFromTinyInputStream(stream: InputStream) {
		val mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false)
		val isSplit = !mappings.namespaces.contains("official")
		mappings.classEntries.forEach { entry ->
			val hashed = entry["hashed"]
			getOrCreateClass(hashed).apply {
				if (isSplit) {
					obfName.client = entry["client"]
					obfName.server = entry["server"]
				} else obfName.merged = entry["official"]
			}
		}
		mappings.methodEntries.forEach { entry ->
			val hashedTriple = entry["hashed"]
			getOrCreateClass(hashedTriple.owner).apply {
				getOrCreateMethod(hashedTriple.name, hashedTriple.desc).apply {
					if (isSplit) {
						val clientTriple = entry["client"]
						val serverTriple = entry["server"]
						obfName.client = clientTriple?.name
						obfName.server = serverTriple?.name
					} else {
						val officialTriple = entry["official"]
						obfName.merged = officialTriple?.name
					}
				}
			}
		}
		mappings.fieldEntries.forEach { entry ->
			val hashedTriple = entry["hashed"]
			getOrCreateClass(hashedTriple.owner).apply {
				getOrCreateField(hashedTriple.name, hashedTriple.desc).apply {
					if (isSplit) {
						val clientTriple = entry["client"]
						val serverTriple = entry["server"]
						obfName.client = clientTriple?.name
						obfName.server = serverTriple?.name
					} else {
						val officialTriple = entry["official"]
						obfName.merged = officialTriple?.name
					}
				}
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
				loadNamedFromTinyInputStream(entry.bytes.inputStream(), showError)
			}
		}
	}

	fun MappingsContainer.loadNamedFromTinyInputStream(stream: InputStream, showError: Boolean = true) {
		val mappings = net.fabricmc.mappings.MappingsProvider.readTinyMappings(stream, false)
		mappings.classEntries.forEach { entry ->
			val hashedMojmap = entry["hashed"]
			val clazz = getClass(hashedMojmap)
			if (clazz == null) {
				if (showError) warn("Class $hashedMojmap does not have hashedMojmap name! Skipping!")
			} else clazz.apply {
				if (mappedName == null)
					mappedName = entry["named"]
			}
		}
		mappings.methodEntries.forEach { entry ->
			val hashedTriple = entry["hashed"]
			val clazz = getClass(hashedTriple.owner)
			if (clazz == null) {
				if (showError) warn("Class ${hashedTriple.owner} does not have hashed name! Skipping!")
			} else clazz.apply {
				val method = getMethod(hashedTriple.name, hashedTriple.desc)
				if (method == null) {
					if (showError) warn("Method ${hashedTriple.name} in ${hashedTriple.owner} does not have hashed name! Skipping!")
				} else method.apply {
					val namedTriple = entry["named"]
					if (mappedName == null)
						mappedName = namedTriple?.name
				}
			}
		}
		mappings.fieldEntries.forEach { entry ->
			val hashedTriple = entry["hashed"]
			val clazz = getClass(hashedTriple.owner)
			if (clazz == null) {
				if (showError) warn("Class ${hashedTriple.owner} does not have hashed name! Skipping!")
			} else clazz.apply {
				val field = getField(hashedTriple.name)
				if (field == null) {
					if (showError) warn("Field ${hashedTriple.name} in ${hashedTriple.owner} does not have hashed name! Skipping!")
				} else field.apply {
					val namedTriple = entry["named"]
					if (mappedName == null)
						mappedName = namedTriple?.name
				}
			}
		}
	}
}
