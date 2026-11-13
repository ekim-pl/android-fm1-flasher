package pl.fm1.flasher

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LocalLibraryTest {
    private fun withLibrary(test: (File, LocalLibrary) -> Unit) {
        val directory = kotlin.io.path.createTempDirectory().toFile()
        try { test(directory, LocalLibrary(directory)) } finally { directory.deleteRecursively() }
    }

    @Test fun savedBytesMetadataFavoritesAndOfflineSelectionSurviveRestart() = withLibrary { directory, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw, 42)
        library.saveSettings(LibrarySettings(setOf(entry.id), true, entry.id))
        val restarted = LocalLibrary(directory)
        assertEquals(listOf(LocalPackage(entry, 42, raw.size.toLong())), restarted.list())
        assertEquals(LibrarySettings(setOf(entry.id), true, entry.id), restarted.settings())
        assertEquals(entry.sha256, restarted.load(entry.id, null).second.sha256)
    }

    @Test fun expiredCatalogDoesNotPreventVerifiedOfflineFlash() = withLibrary { _, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        val expired = Catalog(listOf(entry), "old", 1, false)
        assertEquals(entry, library.load(entry.id, expired).first.entry)
    }

    @Test fun failedUpdateLeavesPreviousPackageAndSelectionIntact() = withLibrary { _, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        library.saveSettings(LibrarySettings(selectedId = entry.id))
        val next = updatedPackage(); val nextEntry = localEntry(next).copy(version = "2", product = "FM-1_901")
        val corrupt = next.clone().apply { this[1000] = (this[1000].toInt() xor 1).toByte() }
        assertThrows(IllegalArgumentException::class.java) { library.save(nextEntry, corrupt) }
        assertEquals(entry, library.load(entry.id, null).first.entry)
        assertEquals(entry.id, library.settings().selectedId)
    }

    @Test fun successfulUpdateAtomicallyReplacesVersionAndPrunesOldBytes() = withLibrary { directory, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw, 1)
        val next = updatedPackage(); val nextEntry = localEntry(next).copy(version = "2", product = "FM-1_901")
        library.save(nextEntry, next, 2)
        assertEquals(nextEntry, LocalLibrary(directory).load(entry.id, null).first.entry)
        assertEquals(1, File(directory, "packages").listFiles()!!.count { it.extension == "fwsc" })
    }

    @Test fun corruptionOnDiskIsDetectedBeforeFlash() = withLibrary { directory, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        File(directory, "packages/${entry.sha256}.fwsc").writeBytes(raw.clone().apply { this[1000] = 42 })
        assertThrows(IllegalArgumentException::class.java) { library.load(entry.id, null) }
    }

    @Test fun newerVersionChipSupportIsNeverGrantedToOlderLocalPackage() = withLibrary { _, library ->
        val raw = testPackage(); val old = localEntry(raw).copy(chip = "old")
        library.save(old, raw)
        val latest = old.copy(sha256 = "a".repeat(64), chip = "new", version = "2")
        val saved = library.load(old.id, Catalog(listOf(latest), "new", 10, true)).first.entry
        assertEquals("old", saved.chip)
        assertFalse(FlashChip.NEW.allows(saved))
    }

    @Test fun correctedCompatibilityForSameBytesTakesEffectEvenOffline() = withLibrary { _, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        val corrected = entry.copy(chip = "old")
        assertEquals("old", library.load(entry.id, Catalog(listOf(corrected), "known", 10, false)).first.entry.chip)
    }

    @Test fun knownRemovedFirmwareIsBlockedWithoutDeletingSavedCopy() = withLibrary { _, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        assertThrows(IllegalStateException::class.java) { library.load(entry.id, Catalog(emptyList(), "new", 10, false)) }
        assertEquals(1, library.list().size)
    }

    @Test fun deletingOneOfTwoEntriesWithSharedBytesKeepsOtherAndFavorites() = withLibrary { _, library ->
        val raw = testPackage(); val first = localEntry(raw); val second = first.copy(id = "second", name = "Second")
        library.save(first, raw); library.save(second, raw)
        library.saveSettings(LibrarySettings(setOf(first.id), selectedId = first.id))
        library.remove(first.id)
        assertEquals(listOf(second), library.list().map { it.entry })
        assertEquals(second.sha256, library.load(second.id, null).second.sha256)
        assertEquals(setOf(first.id), library.settings().favorites)
        assertNull(library.settings().selectedId)
    }

    @Test fun incompleteTemporaryDownloadIsIgnoredAndValidPackageSurvives() = withLibrary { directory, library ->
        val raw = testPackage(); val entry = localEntry(raw)
        library.save(entry, raw)
        File(directory, "records/interrupted.json.tmp").writeText("{partial")
        File(directory, "packages/interrupted.fwsc.tmp").writeBytes(byteArrayOf(1))
        assertEquals(listOf(entry), LocalLibrary(directory).list().map { it.entry })
        assertEquals(entry.sha256, library.load(entry.id, null).second.sha256)
    }
}

internal fun localEntry(raw: ByteArray = testPackage()) = CatalogEntry(
    "demo", "Demo", "Author", "A synthetic test synthesizer. No manufacturer firmware.", "1", "example/demo", "v1", "123",
    FirmwarePackage.sha256(raw), "FM-1_900", "new", false, kinds = setOf("synth"), date = "2026-11-01")

internal fun updatedPackage() = testPackage().apply { this[7 * 48 + 47] = ('1'.code + 8).toByte() }
