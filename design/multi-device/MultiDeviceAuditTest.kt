package pl.ekim.fm1_flasher

import kotlin.test.*

/** Temporary characterization of the current policy, not desired safety behavior. */
class MultiDeviceAuditTest {
    @Test fun fullHubResetMakesEveryNewEndpointEligible() {
        val a = Endpoint(1, "usb/001/002")
        val b = Endpoint(2, "usb/001/003")
        val newA = Endpoint(3, "usb/001/004")
        val newB = Endpoint(4, "usb/001/005")
        assertEquals(listOf(newA, newB), ReconnectGate(a, listOf(a, b)).candidates(listOf(newA, newB)))
    }
    @Test fun soleReappearingSiblingCanBeAnAutomaticCandidate() {
        val target = Endpoint(1, "usb/001/002")
        val sibling = Endpoint(2, "usb/001/003")
        val returnedSibling = Endpoint(3, "usb/001/004")
        assertEquals(returnedSibling, ReconnectGate(target, listOf(target, sibling))
            .automaticCandidate(listOf(returnedSibling), setOf(returnedSibling.usbPath)))
    }
    @Test fun matchingFirmwareIdentityDoesNotDistinguishUnits() {
        assertTrue(FirmwareIdentity("FM-1", "015").sameAs(FirmwareIdentity("FM-1", "015")))
        assertTrue(FirmwareIdentity("ota-FM-1", "015").sameAs(FirmwareIdentity("ota-FM-1", "015")))
    }
}
