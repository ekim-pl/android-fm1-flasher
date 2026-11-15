package pl.ekim.fm1_flasher

import kotlin.test.*

class FleetContinuityTest {
    private val a = Endpoint(1, "usb/A", 1)
    private val b = Endpoint(2, "usb/B", 2)
    private val returned = Endpoint(3, "usb/C", 3)
    @Test fun hubResetCannotSelectSoleReturningNeighbour() {
        val gate = ReconnectGate(a, listOf(a, b))
        gate.observe(emptyList())
        assertTrue(gate.candidates(listOf(returned)).isEmpty())
        assertNull(gate.automaticCandidate(listOf(returned), setOf(returned.usbPath)))
    }
    @Test fun lostSiblingNeverRegainsTrustEvenIfSameEndpointReturns() {
        val gate = ReconnectGate(a, listOf(a, b))
        gate.removed(b)
        assertTrue(gate.candidates(listOf(b, returned)).isEmpty())
        assertFalse(gate.valid(listOf(a, b)))
    }
    @Test fun usbDetachBeforeMidiCallbackPoisonsTransition() {
        val gate = ReconnectGate(a, listOf(a, b))
        gate.removedPath(b.usbPath)
        assertTrue(gate.candidates(listOf(b, returned)).isEmpty())
    }
    @Test fun hubDetachPoisonsTransitionEvenBeforeChildrenDisappear() {
        val gate = ReconnectGate(a, listOf(a, b), setOf(a.usbPath, b.usbPath, "usb/HUB"))
        gate.removedPath("usb/HUB")
        assertFalse(gate.valid(listOf(a, b)))
    }
    @Test fun lateDetachFromPreviousStageDoesNotPoisonCurrentStage() {
        val gate = ReconnectGate(returned, listOf(returned), setOf(a.usbPath, returned.usbPath), setOf(a.usbPath))
        gate.removedPath(a.usbPath)
        assertTrue(gate.valid(listOf(returned)))
        gate.removedPath(returned.usbPath)
        val final = Endpoint(4, "usb/D", 4)
        assertEquals(final, gate.automaticCandidate(listOf(final), setOf(final.usbPath)))
    }
    @Test fun unchangedNeighboursAllowOnlyExplicitPairing() {
        val gate = ReconnectGate(a, listOf(a, b))
        gate.removedPath(a.usbPath); gate.observe(listOf(b))
        assertEquals(listOf(returned), gate.candidates(listOf(b, returned)))
        assertNull(gate.automaticCandidate(listOf(b, returned), setOf(b.usbPath, returned.usbPath)))
    }
    @Test fun reusedSiblingAddressDoesNotPreserveContinuity() {
        val gate = ReconnectGate(a, listOf(a, b))
        assertFalse(gate.valid(listOf(returned, b.copy(generation = 99))))
    }
    @Test fun multipleNewDevicesCannotBeChosenByGuessing() {
        val gate = ReconnectGate(a, listOf(a, b))
        assertTrue(gate.candidates(listOf(b, returned, Endpoint(4, "usb/D", 4))).isEmpty())
    }
    @Test fun extraUsbArrivalBlocksEvenWithOnlyOneMidiCandidate() {
        val gate = ReconnectGate(a, listOf(a, b), setOf("usb/HUB", a.usbPath, b.usbPath))
        val usb = setOf("usb/HUB", b.usbPath, returned.usbPath, "usb/extra")
        assertTrue(gate.candidates(listOf(b, returned), usb).isEmpty())
    }
}
