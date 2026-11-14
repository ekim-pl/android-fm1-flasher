package pl.ekim.fm1_flasher

import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MidiSessionGuardTest {
    @Test fun anotherWorkerCannotProbeDuringFlashButCanAfterRelease() {
        val worker = Executors.newSingleThreadExecutor()
        try {
            MidiSessionGuard.acquire()
            try {
                // Closing a nested connection must retain the flash session's reservation.
                MidiSessionGuard.acquire()
                MidiSessionGuard.release()
                worker.submit {
                    assertThrows(IllegalStateException::class.java) { MidiSessionGuard.acquire() }
                }.get(2, TimeUnit.SECONDS)
            } finally { MidiSessionGuard.release() }
            worker.submit {
                MidiSessionGuard.acquire()
                MidiSessionGuard.release()
            }.get(2, TimeUnit.SECONDS)
        } finally { worker.shutdownNow() }
    }
}
