package pl.ekim.fm1_flasher

import kotlin.test.*


class TargetSelectionTest {
    private val first = Endpoint(1, "/dev/bus/usb/001/002")
    private val target = Endpoint(2, "/dev/bus/usb/001/003")
    private val third = Endpoint(3, "/dev/bus/usb/001/004")
    private val loader = Endpoint(4, "/dev/bus/usb/001/005")

    @Test fun noCandidateBeforeSelectedDeviceDisappears() {
        val gate = ReconnectGate(target, listOf(first, target, third))
        assertTrue(gate.candidates(listOf(first, target, third, loader)).isEmpty())
    }

    @Test fun siblingsAreExcludedEvenWhenNamesAndFirmwareWouldMatch() {
        val gate = ReconnectGate(target, listOf(first, target, third))
        assertEquals(listOf(loader), gate.candidates(listOf(first, third, loader)))
        assertTrue(gate.candidates(listOf(first, third)).isEmpty())
    }

    @Test fun newMidiIdOnAnExistingSiblingUsbPortIsStillExcluded() {
        val gate = ReconnectGate(target, listOf(first, target, third))
        assertTrue(gate.candidates(listOf(Endpoint(99, first.usbPath), third)).isEmpty())
    }

    @Test fun ambiguousNewPortsCannotBeManuallyGuessed() {
        val otherNew = Endpoint(5, "/dev/bus/usb/001/006")
        val gate = ReconnectGate(target, listOf(first, target, third))
        assertTrue(gate.candidates(listOf(first, third, loader, otherNew)).isEmpty())
    }

    @Test fun onlyReenumeratedUsbDeviceCanReconnectAutomatically() {
        val gate = ReconnectGate(target, listOf(target))
        assertEquals(loader, gate.automaticCandidate(listOf(loader), setOf(loader.usbPath)))
        val final = Endpoint(5, "/dev/bus/usb/001/006")
        assertEquals(final, ReconnectGate(loader, listOf(loader)).automaticCandidate(listOf(final), setOf(final.usbPath)))
    }

    @Test fun automaticReconnectWaitsForPreviousIdToDisappear() {
        val gate = ReconnectGate(target, listOf(target))
        assertNull(gate.automaticCandidate(listOf(target), setOf(target.usbPath)))
        assertNull(gate.automaticCandidate(listOf(target, loader), setOf(target.usbPath, loader.usbPath)))
        assertNull(gate.automaticCandidate(emptyList(), emptySet()))
    }

    @Test fun otherUsbDevicesRequireManualSelectionEvenWithOneEligiblePort() {
        val gate = ReconnectGate(target, listOf(first, target))
        assertNull(gate.automaticCandidate(listOf(first, loader), setOf(first.usbPath, loader.usbPath)))
        assertNull(gate.automaticCandidate(listOf(loader), setOf(loader.usbPath, "usb/non-midi-keyboard")))
    }

    @Test fun severalMidiEndpointsOnOneUsbDeviceAreNotAutoSelected() {
        val gate = ReconnectGate(target, listOf(target))
        val otherPort = Endpoint(5, loader.usbPath)
        assertNull(gate.automaticCandidate(listOf(loader, otherPort), setOf(loader.usbPath)))
    }

    @Test fun soleRemainingSiblingOrStaleUsbPathCannotBeAutoSelected() {
        val gate = ReconnectGate(target, listOf(first, target))
        assertNull(gate.automaticCandidate(listOf(first), setOf(first.usbPath)))
        assertNull(gate.automaticCandidate(listOf(Endpoint(99, first.usbPath)), setOf(first.usbPath)))
        assertNull(gate.automaticCandidate(listOf(loader), setOf(target.usbPath)))
        assertNull(gate.automaticCandidate(listOf(loader), emptySet()))
    }
}
