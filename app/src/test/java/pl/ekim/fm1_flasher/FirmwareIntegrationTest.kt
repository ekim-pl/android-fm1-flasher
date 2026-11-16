package pl.ekim.fm1_flasher

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Explicit Gradle task only; missing or changed official packages fail instead of skipping. */
internal class FirmwareIntegrationTest : RecoveryTest() {
    private fun packageFor(version: String): FirmwarePackage {
        val directory = File(checkNotNull(System.getProperty("fm1.fixtureDir")) { "Run :app:firmwareIntegrationTest" })
        val manifest = JSONObject(File(checkNotNull(System.getProperty("fm1.fixtureManifest"))).readText())
        val item = manifest.getJSONObject(version)
        val raw = File(directory, "$version.fwsc").readBytes()
        return FirmwarePackage.parse(raw, item.getString("sha256"), item.getString("product"))
    }

    override fun v15() = packageFor("v15")
    override fun operations(image: FirmwarePackage) = Recovery.operations

    @Test fun officialPackagesHavePinnedIdentitiesAndV16CannotBeRecovered() {
        val v15 = packageFor("v15")
        val v16 = packageFor("v16")
        assertEquals(FirmwareIdentity("FM-1", "015"), v15.identity)
        assertEquals(FirmwareIdentity("FM-1", "016"), v16.identity)
        assertEquals(Recovery.V15_PACKAGE, v15.sha256)
        assertEquals(Recovery.V15_REGION, FirmwarePackage.sha256(Recovery.region(v15)))
        assertTrue(v16.flashImage.size >= Recovery.END)
        assertThrows(IllegalArgumentException::class.java) { Recovery.region(v16) }
        for (image in listOf(v15, v16)) {
            val raw = File(System.getProperty("fm1.fixtureDir"), if (image === v15) "v15.fwsc" else "v16.fwsc").readBytes()
            raw[0x500] = (raw[0x500].toInt() xor 1).toByte()
            assertThrows(IllegalArgumentException::class.java) { FirmwarePackage.parse(raw, image.sha256, image.identity.toString()) }
        }
    }
}
