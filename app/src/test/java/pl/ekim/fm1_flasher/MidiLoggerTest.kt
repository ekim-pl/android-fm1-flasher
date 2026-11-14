package pl.ekim.fm1_flasher

import org.junit.Assert.*
import org.junit.Test

class MidiLoggerTest {
    private fun bytes(vararg n: Int) = n.map { it.toByte() }.toByteArray()
    @Test fun fragmentationRunningStatusAndRealtime() {
        val emitted = mutableListOf<ByteArray>()
        val parser = MidiStreamParser { b, _ -> emitted += b }
        bytes(0x90, 60, 0xF8, 100, 61, 0, 0xC1, 5, 6).forEach { parser.accept(byteArrayOf(it)) }
        assertEquals(5, emitted.size)
        assertArrayEquals(bytes(0xF8), emitted[0])
        assertArrayEquals(bytes(0x90, 60, 100), emitted[1])
        assertArrayEquals(bytes(0x90, 61, 0), emitted[2])
        assertArrayEquals(bytes(0xC1, 5), emitted[3])
        assertArrayEquals(bytes(0xC1, 6), emitted[4])
    }
    @Test fun sysexRealtimeAndSystemCommonResetRunningStatus() {
        val messages = mutableListOf<Pair<ByteArray,String>>()
        val parser = MidiStreamParser { b, a -> messages += b to a }
        parser.accept(bytes(0x90, 60, 1, 0xF0, 1, 0xFA))
        parser.accept(bytes(2, 0xF7, 3, 0xF2, 1, 2, 4))
        assertEquals(listOf("", "", "", "orphan data", "", "orphan data"), messages.map { it.second })
        assertArrayEquals(bytes(0xF0, 1, 2, 0xF7), messages[2].first)
    }
    @Test fun independentPortsAndLossReset() {
        val a = mutableListOf<ByteArray>(); val b = mutableListOf<ByteArray>()
        val pa = MidiStreamParser { m, _ -> a += m }; val pb = MidiStreamParser { m, _ -> b += m }
        pa.accept(bytes(0x90, 60)); pb.accept(bytes(0xB1, 7, 100)); pa.reset(); pa.accept(bytes(1))
        assertArrayEquals(bytes(0xB1, 7, 100), b.single())
        assertArrayEquals(bytes(1), a.single())
    }
    @Test fun boundedSysexAndRecovery() {
        val messages = mutableListOf<Pair<ByteArray,String>>()
        val parser = MidiStreamParser { b, a -> messages += b to a }
        parser.accept(bytes(0xF0)); parser.accept(ByteArray(70000) { 1 }); parser.accept(bytes(0xF8,0xF7,0x90,60,127))
        assertEquals("truncated", messages[1].second)
        assertTrue(messages[1].first.size <= 65536)
        assertArrayEquals(bytes(0x90,60,127), messages[2].first)
    }
    @Test fun interruptionAndEveryMessageLength() {
        val messages = mutableListOf<ByteArray>()
        val parser = MidiStreamParser { b, _ -> messages += b }
        parser.accept(bytes(0xF0, 1, 0x90, 60, 90, 0xA0, 60, 1, 0xB0, 1, 2, 0xC0, 3, 0xD0, 4, 0xE0, 0, 64, 0xF1, 1, 0xF3, 2, 0xF6))
        assertEquals(listOf(2,3,3,3,2,2,3,2,2,1), messages.map { it.size })
    }
    @Test fun unfinishedMessageIsVisibleOnClose() {
        val messages = mutableListOf<Pair<ByteArray,String>>()
        val parser = MidiStreamParser { b, a -> messages += b to a }
        parser.accept(bytes(0xF0, 1, 2)); parser.finish()
        assertArrayEquals(bytes(0xF0, 1, 2), messages.single().first)
        assertTrue(messages.single().second.contains("interrupted"))
    }
    @Test fun boundedLogAndExport() {
        val log = MidiLog(maxEntries = 2, maxBytes = 6)
        repeat(3) { log.add("port $it", bytes(0x90,60,it)) }
        log.loss("port 2", 42)
        val snapshot = log.snapshot()
        assertEquals(2, snapshot.entries.size)
        assertEquals(2L, snapshot.evicted)
        assertEquals(42L, snapshot.lostBytes)
        assertTrue(snapshot.export().contains("90 3C 02"))
        assertTrue(snapshot.export().contains("parser reset"))
        assertTrue(snapshot.entries.first().line().contains("note=60"))
        log.clear(); assertTrue(log.snapshot().entries.isEmpty())
    }
}
