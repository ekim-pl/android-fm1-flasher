package pl.fm1.flasher

import org.junit.Assert.*
import org.junit.Test

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

    @Test fun ambiguousNewPortsRemainSeparateForExplicitUserSelection() {
        val otherNew = Endpoint(5, "/dev/bus/usb/001/006")
        val gate = ReconnectGate(target, listOf(first, target, third))
        assertEquals(listOf(loader, otherNew), gate.candidates(listOf(first, third, loader, otherNew)))
    }
}
