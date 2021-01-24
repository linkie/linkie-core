package me.shedaniel.linkie.core.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.LinkieConfig
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.localiseFieldDesc
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
            assertEquals("1.16.5", YarnNamespace.getDefaultVersion())
            YarnNamespace.getDefaultProvider().get()
        }
    }

    @Test
    fun mcp() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MCPNamespace)))
            delay(2000)
            while (MCPNamespace.reloading) delay(100)
            assertEquals("1.16.3", MCPNamespace.getDefaultVersion())
            MCPNamespace.getDefaultProvider().get()
        }
    }

    @Test
    fun mojmap() {
        runBlocking {
            Namespaces.init(LinkieConfig.DEFAULT.copy(namespaces = listOf(MojangNamespace)))
            delay(2000)
            while (MojangNamespace.reloading) delay(100)
            assertEquals("1.16.5", MojangNamespace.getDefaultVersion())
            MojangNamespace.getDefaultProvider().get()
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
            "kotlin/Sad" to "kotlin/Happy",
            "java/util/Comparator" to "kotlin/IceCream",
        )
        val remapper: (String) -> String = { remaps[it] ?: it }

        assertEquals("()V", "()V".remapDescriptor(remapper))
        assertEquals("(Ljava/util/Optional;)V", "(Ljava/util/Optional;)V".remapDescriptor(remapper))
        assertEquals("(IJ)Ljava/util/Optional;", "(IJ)Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals("(JZLjava/util/Optional;IJ)Ljava/util/Optional;", "(JZLjava/util/Optional;IJ)Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals("(JZLkotlin/Happy;Ljava/util/Optional;IJ)Ljava/util/Optional;",
            "(JZLkotlin/Sad;Ljava/util/Optional;IJ)Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals("(JZLkotlin/Happy;Ljava/util/Optional;IJ)Lkotlin/IceCream;",
            "(JZLkotlin/Sad;Ljava/util/Optional;IJ)Ljava/util/Comparator;".remapDescriptor(remapper))
        assertEquals("(JZ[Lkotlin/Happy;Ljava/util/Optional;IJ)Lkotlin/IceCream;",
            "(JZ[Lkotlin/Sad;Ljava/util/Optional;IJ)Ljava/util/Comparator;".remapDescriptor(remapper))

        assertEquals("V", "V".remapDescriptor(remapper))
        assertEquals("Ljava/util/Optional;", "Ljava/util/Optional;".remapDescriptor(remapper))
        assertEquals("Lkotlin/Happy;", "Lkotlin/Sad;".remapDescriptor(remapper))
        assertEquals("Lkotlin/IceCream;", "Ljava/util/Comparator;".remapDescriptor(remapper))
        assertEquals("[Lkotlin/IceCream;", "[Ljava/util/Comparator;".remapDescriptor(remapper))
        assertEquals("[J", "[J".remapDescriptor(remapper))
    }
}