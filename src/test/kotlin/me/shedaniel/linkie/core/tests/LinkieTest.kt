package me.shedaniel.linkie.core.tests

import com.soywiz.klock.measureTime
import com.soywiz.korio.util.toStringDecimal
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.Class
import me.shedaniel.linkie.Field
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.MappingsContainer
import me.shedaniel.linkie.MappingsEntry
import me.shedaniel.linkie.Method
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.namespaces.*
import me.shedaniel.linkie.obfMergedName
import me.shedaniel.linkie.optimumName
import me.shedaniel.linkie.utils.ClassResultList
import me.shedaniel.linkie.utils.FieldResultList
import me.shedaniel.linkie.utils.MappingsQuery
import me.shedaniel.linkie.utils.MatchAccuracy
import me.shedaniel.linkie.utils.MemberEntry
import me.shedaniel.linkie.utils.MethodResultList
import me.shedaniel.linkie.utils.QueryContext
import me.shedaniel.linkie.utils.ResultHolder
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.like
import me.shedaniel.linkie.utils.localiseFieldDesc
import me.shedaniel.linkie.utils.onlyClass
import me.shedaniel.linkie.utils.remapDescriptor
import me.shedaniel.linkie.utils.toVersion
import me.shedaniel.linkie.utils.tryToVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LinkieTest {
    @Test
    fun versioning() {
        assertEquals(Version(1, 14, 4), "1.14.4".tryToVersion())
        assertEquals(Version(1, 14, snapshot = "alpha.19.w.02a"), "19w02a".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "alpha.20.w.17a"), "20w17a".tryToVersion())
        assertEquals(Version(1, 16, 2, snapshot = "alpha.20.w.27a"), "20w27a".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "pre3"), "1.16-pre3".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "pre3"), "1.16 Pre-Release 3".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "rc1"), "1.16-rc1".tryToVersion())
        assertEquals(Version(1, 17, snapshot = "alpha.20.w.45a"), "20w45a".tryToVersion())
        assert(Version(1, 17, snapshot = "alpha.20.w.45a") >= "1.16.4".toVersion())
        assert(Version(1, 14, 4) > Version(1, 14, 3))
        assert(Version(1, 14, 4) >= Version(1, 14, 4))
        assertFalse(Version(1, 13, 2) >= Version(1, 14, 4))
        assertFalse(Version(1, 14, 4) >= Version(1, 15))
        assert(Version(1, 16, snapshot = "pre6") > Version(1, 15))
        assert(Version(1, 16, snapshot = "pre6") < Version(1, 16))
        assert(Version(1, 16, snapshot = "pre5") < Version(1, 16, snapshot = "pre6"))
        assert(Version(1, 16, snapshot = "pre5") < Version(1, 16, snapshot = "rc1"))
        assert(Version(1, 16, snapshot = "rc1") < Version(1, 16))
        assert(Version(1, 16, snapshot = "alpha.20.w.17a") < Version(1, 16))
        assert(Version(1, 16, snapshot = "alpha.20.w.17a") < Version(1, 16, snapshot = "pre6"))
        assert(Version(1, 16, snapshot = "alpha.20.w.17a") < Version(1, 16, snapshot = "rc1"))
        assertFalse(Version(1, 16, snapshot = "alpha.20.w.17a") > Version(1, 16, snapshot = "alpha.20.w.18a"))
    }

    @Test
    fun yarn() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(YarnNamespace)))
            delay(2000)
            while (YarnNamespace.reloading) delay(100)
            YarnNamespace.getDefaultProvider().get()
        }
    }

    @Test
    fun yarnSource() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(YarnNamespace)))
            delay(2000)
            while (YarnNamespace.reloading) delay(100)
            val container = YarnNamespace.getDefaultProvider()
            val source = container.getSources(container.get().allClasses.random().optimumName)
            source
        }
    }

    @Test
    fun quiltMappings() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(QuiltMappingsNamespace)))
            delay(2000)
            while (QuiltMappingsNamespace.reloading) delay(100)
            QuiltMappingsNamespace.getDefaultProvider().get()
        }
    }

    @Test
    fun mcp() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MCPNamespace)))
            delay(2000)
            while (MCPNamespace.reloading) delay(100)
            val container = MCPNamespace.getDefaultProvider().get()
            container
        }
    }

    @Test
    fun mcpOld() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MCPNamespace)))
            delay(2000)
            while (MCPNamespace.reloading) delay(100)
            val container = MCPNamespace.getProvider("1.14.4").get()
            container
        }
    }

    @Test
    fun mojmap() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MojangNamespace)))
            delay(2000)
            while (MojangNamespace.reloading) delay(100)
            val container = MojangNamespace.getDefaultProvider().get()
            container
        }
    }

    @Test
    fun mojmapSource() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MojangNamespace)))
            delay(2000)
            while (MojangNamespace.reloading) delay(100)
            val container = MojangNamespace.getDefaultProvider()
            val source = container.getSources(container.get().allClasses.random().optimumName)
            source
        }
    }

    @Test
    fun mojmapSrg() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MojangSrgNamespace)))
            delay(2000)
            while (MojangSrgNamespace.reloading) delay(100)
            val container = MojangSrgNamespace.getDefaultProvider().get()
            container
        }
    }

    @Test
    fun mojmapSrg1_17() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MojangSrgNamespace)))
            delay(2000)
            while (MojangSrgNamespace.reloading) delay(100)
            val container = MojangSrgNamespace.getProvider("1.17.1").get()
            container
        }
    }

    @Test
    fun mojmapHashed1_17() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MojangNamespace, MojangHashedNamespace)))
            delay(2000)
            while (MojangHashedNamespace.reloading) delay(100)
            val container = MojangHashedNamespace.getProvider("1.17.1").get()
            container
        }
    }

    @Test
    fun descriptionLocalising() {
        assertEquals("int", "I".localiseFieldDesc())
        assertEquals("int[]", "[I".localiseFieldDesc())
        assertEquals("int[][]", "[[I".localiseFieldDesc())
        assertEquals("me.shedaniel.linkie.MappingsKt", "Lme/shedaniel/linkie/MappingsKt;".localiseFieldDesc())
    }

    @Test
    fun remapDescription() {
        val remaps = mapOf(
            "Sad" to "Happy",
            "kotlin/Sad" to "kotlin/Happy",
            "java/util/Comparator" to "kotlin/IceCream",
        )
        val remapper: (String) -> String = { remaps[it] ?: it }

        assertEquals("()V", "()V".remapDescriptor(remapper))
        assertEquals("(Ljava/util/Optional;)V", "(Ljava/util/Optional;)V".remapDescriptor(remapper))
        assertEquals("(IJ)Ljava/util/Optional;", "(IJ)Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals("(JZLjava/util/Optional;IJ)Ljava/util/Optional;", "(JZLjava/util/Optional;IJ)Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals(
            "(JZLkotlin/Happy;Ljava/util/Optional;IJ)Ljava/util/Optional;",
            "(JZLkotlin/Sad;Ljava/util/Optional;IJ)Ljava/util/Optional;".remapDescriptor(remapper)
        )
        assertEquals(
            "(JZLkotlin/Happy;Ljava/util/Optional;IJ)Lkotlin/IceCream;",
            "(JZLkotlin/Sad;Ljava/util/Optional;IJ)Ljava/util/Comparator;".remapDescriptor(remapper)
        )
        assertEquals(
            "(JZ[Lkotlin/Happy;Ljava/util/Optional;IJ)Lkotlin/IceCream;",
            "(JZ[Lkotlin/Sad;Ljava/util/Optional;IJ)Ljava/util/Comparator;".remapDescriptor(remapper)
        )

        assertEquals("V", "V".remapDescriptor(remapper))
        assertEquals("Ljava/util/Optional;", "Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals("Lkotlin/Happy;", "Lkotlin/Sad;".remapDescriptor(remapper))
        assertEquals("Lkotlin/IceCream;", "Ljava/util/Comparator;".remapDescriptor(remapper))
        assertEquals("[Lkotlin/IceCream;", "[Ljava/util/Comparator;".remapDescriptor(remapper))
        assertEquals("[J", "[J".remapDescriptor(remapper))
        assertEquals("[LHappy;", "[LSad;".remapDescriptor(remapper))
    }

    @Test
    fun stringLike() {
        assertEquals("MinecraftClient".like, "net/minecraft/client/MinecraftClient".like.onlyClass())
    }

    @Test
    fun mappingsQuery() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(YarnNamespace)))
            delay(2000)
            while (YarnNamespace.reloading) delay(100)
            val container = YarnNamespace.getProvider("1.16.5").get()
            mappingsTester(container) {
                assertClassIntermediary("MinecraftClient", "class_310")
                assertClassIntermediary("MinecraftCli", "class_310")
                assertFieldIntermediary("minecraftCli", "field_26868")
                assertMethodIntermediary("drawSlot", "method_2385")
                assertClassIntermediary("Screen", "class_437")
                assertFieldIntermediary("DrawableHelper.zOffset", "field_22734")
                assertFieldIntermediary("field_9360", "field_9360")
                assertFieldIntermediary("9360", "field_9360")
                assertFieldIntermediary("field_13176", "field_13176")
                assertMethodIntermediary("saveRecipe", "method_10425")
                assertClassIntermediary("Slot", "class_1735")
                assertMethodIntermediary("MinecraftClient.openScreen", "method_1507")

                assertClassIntermediary("net/minecraft/recipe/ShapelessRecipe", "class_1867")
                assertClassIntermediary("net/minecraft/data/server/recipe/ShapelessRecipeJsonFactory\$ShapelessRecipeJsonProvider", "class_2450\$class_2451")
                assertClassIntermediary("recipe/ShapelessRec", "class_1867")
                assertClassIntermediary("nbt/NbtElement", "class_2520")
                assertFieldIntermediary("recipe/ShapelessR.inp", "field_9047")

                assertClassIntermediary("MinecraffClient", "class_310", true)
                assertFieldIntermediary("ShapelesssRecipe.inputs", "field_9047", true)
                assertMethodIntermediary("GhastMoveContr.willCollide", "method_7051", true)
            }
        }
    }

    private inline fun mappingsTester(container: MappingsContainer, function: MappingsTester.() -> Unit) {
        MappingsTester(container).also(function)
    }

    private class MappingsTester(val container: MappingsContainer) {
        fun assertClassIntermediary(query: String, expectedIntermediary: String, fuzzy: Boolean = false) {
            val time = measureTime {
                assertClass(query, fuzzy, "a class with intermediary named \"$expectedIntermediary\"") {
                    it.intermediaryName.onlyClass() == expectedIntermediary
                }
            }
            println("$query took $time")
        }

        fun assertMethodIntermediary(query: String, expectedIntermediary: String, fuzzy: Boolean = false) {
            val time = measureTime {
                assertMethod(query, fuzzy, "a method with intermediary named \"$expectedIntermediary\"") {
                    it.intermediaryName == expectedIntermediary
                }
            }
            println("$query took $time")
        }

        fun assertFieldIntermediary(query: String, expectedIntermediary: String, fuzzy: Boolean = false) {
            val time = measureTime {
                assertField(query, fuzzy, "a field with intermediary named \"$expectedIntermediary\"") {
                    it.intermediaryName == expectedIntermediary
                }
            }
            println("$query took $time")
        }

        fun List<ResultHolder<*>>.offerList(): String = offerList(this)

        @JvmName("offerList_")
        fun offerList(list: List<ResultHolder<*>>): String = buildString {
            for (holder in list.asSequence().take(15)) {
                appendLine()
                append("- ")
                append(holder.score.toStringDecimal(4).padStart(6))
                append(' ')
                val member: MappingsEntry = if (holder.value is Class) {
                    holder.value as Class
                } else (holder.value as MemberEntry<*>).member
                append(member.javaClass.simpleName)
                append(' ')
                append(member.obfMergedName ?: "OBF_NULL")
                append(" -> ")
                append(member.intermediaryName)
                if (member.mappedName != null) {
                    append(" -> ")
                    append(member.mappedName)
                }
            }
        }

        inline fun assertClass(query: String, fuzzy: Boolean, expected: String, value: (clazz: Class) -> Boolean) {
            val list = query(query, fuzzy)
            require(list.isNotEmpty()) { "Query \"$query\" returned no result! ${list.offerList()}" }
            require(list.first().value is Class) { "Query \"$query\" did not return a class! ${list.offerList()}" }
            require(value(list.first().value as Class)) { "Query \"$query\" did not return $expected! ${list.offerList()}" }
            println("\n$query -> $expected" + list.offerList())
        }

        inline fun assertMethod(query: String, fuzzy: Boolean, expected: String, value: (method: Method) -> Boolean) {
            val list = query(query, fuzzy)
            require(list.isNotEmpty()) { "Query \"$query\" returned no result! ${list.offerList()}" }
            require(
                list.first().value is MemberEntry<*>
                        && (list.first().value as MemberEntry<*>).member is Method
            ) { "Query \"$query\" did not return a method! ${list.offerList()}" }
            require(value((list.first().value as MemberEntry<*>).member as Method)) { "Query \"$query\" did not return $expected! ${list.offerList()}" }
            println("\n$query -> $expected" + list.offerList())
        }

        inline fun assertField(query: String, fuzzy: Boolean, expected: String, value: (field: Field) -> Boolean) {
            val list = query(query, fuzzy)
            require(list.isNotEmpty()) { "Query \"$query\" returned no result! ${list.offerList()}" }
            require(
                list.first().value is MemberEntry<*>
                        && (list.first().value as MemberEntry<*>).member is Field
            ) { "Query \"$query\" did not return a field! ${list.offerList()}" }
            require(value((list.first().value as MemberEntry<*>).member as Field)) { "Query \"$query\" did not return $expected! ${list.offerList()}" }
            println("\n$query -> $expected" + list.offerList())
        }

        fun query(query: String, fuzzy: Boolean): List<ResultHolder<*>> {
            val context = QueryContext(
                provider = { container },
                searchKey = query.replace('.', '/'),
            )
            val result: MutableList<ResultHolder<*>> = mutableListOf()
            var classes: ClassResultList? = null
            var methods: MethodResultList? = null
            var fields: FieldResultList? = null
            runBlocking {
                launch {
                    try {
                        classes = MappingsQuery.queryClasses(context).value
                    } catch (ignore: NullPointerException) {
                    }
                }
                launch {
                    try {
                        methods = MappingsQuery.queryMethods(context).value
                    } catch (ignore: NullPointerException) {
                    }
                }
                launch {
                    try {
                        fields = MappingsQuery.queryFields(context).value
                    } catch (ignore: NullPointerException) {
                    }
                }
            }
            classes?.also(result::addAll)
            methods?.also(result::addAll)
            fields?.also(result::addAll)
            result.sortByDescending { it.score }

            if (result.isEmpty() && fuzzy) {
                runBlocking {
                    launch {
                        try {
                            classes = MappingsQuery.queryClasses(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (ignore: NullPointerException) {
                        }
                    }
                    launch {
                        try {
                            methods = MappingsQuery.queryMethods(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (ignore: NullPointerException) {
                        }
                    }
                    launch {
                        try {
                            fields = MappingsQuery.queryFields(context.copy(accuracy = MatchAccuracy.Fuzzy)).value
                        } catch (ignore: NullPointerException) {
                        }
                    }
                }
                classes?.also(result::addAll)
                methods?.also(result::addAll)
                fields?.also(result::addAll)
                result.sortByDescending { it.score }
            }
            return result
        }
    }
}
