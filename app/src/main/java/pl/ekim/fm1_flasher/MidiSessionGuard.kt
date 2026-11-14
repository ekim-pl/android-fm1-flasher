package pl.ekim.fm1_flasher

import java.util.concurrent.locks.ReentrantLock

/** A diagnostic query must never interrupt a flash, including its reconnect gaps. */
internal object MidiSessionGuard {
    private val lock = ReentrantLock()
    fun acquire() { check(lock.tryLock()) { failed(msg(R.string.midi_busy)) } }
    fun release() = lock.unlock()
}
