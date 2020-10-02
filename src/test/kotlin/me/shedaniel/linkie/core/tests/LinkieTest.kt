package me.shedaniel.linkie.core.tests

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import me.shedaniel.linkie.Namespaces
import me.shedaniel.linkie.namespaces.MCPNamespace
import me.shedaniel.linkie.namespaces.MojangNamespace
import me.shedaniel.linkie.namespaces.YarnNamespace
import me.shedaniel.linkie.utils.Version
import me.shedaniel.linkie.utils.tryToVersion
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class LinkieTest {
    @Test
    fun versioning() {
        assertEquals(Version(1, 14, 4), "1.14.4".tryToVersion())
        assertNull("20w02a".tryToVersion())
        assertEquals(Version(1, 14, snapshot = "alpha.19.w.02a"), "19w02a".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "alpha.20.w.17a"), "20w17a".tryToVersion())
        assertEquals(Version(1, 16, 2, snapshot = "alpha.20.w.27a"), "20w27a".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "pre3"), "1.16-pre3".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "pre3"), "1.16 Pre-Release 3".tryToVersion())
        assertEquals(Version(1, 16, snapshot = "rc1"), "1.16-rc1".tryToVersion())
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
        Namespaces.init(YarnNamespace)
        runBlocking { delay(2000) }
        runBlocking { while (YarnNamespace.reloading) delay(100) }
        assertEquals("1.16.3", YarnNamespace.getDefaultVersion())
        YarnNamespace.getDefaultProvider().mappingsContainer!!.invoke()
    }

    @Test
    fun mcp() {
        Namespaces.init(MCPNamespace)
        runBlocking { delay(2000) }
        runBlocking { while (MCPNamespace.reloading) delay(100) }
        assertEquals("1.16.3", MCPNamespace.getDefaultVersion())
        MCPNamespace.getDefaultProvider().mappingsContainer!!.invoke()
    }

    @Test
    fun mojmap() {
        Namespaces.init(MojangNamespace)
        runBlocking { delay(2000) }
        runBlocking { while (MojangNamespace.reloading) delay(100) }
        assertEquals("1.16.3", MojangNamespace.getDefaultVersion())
        MojangNamespace.getDefaultProvider().mappingsContainer!!.invoke()
    }
}