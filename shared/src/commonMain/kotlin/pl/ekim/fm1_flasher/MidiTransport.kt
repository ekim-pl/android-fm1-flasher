package pl.ekim.fm1_flasher

/** Generation changes on native topology events, even if endpoint IDs and labels are reused. */
data class ProbePort(val id: Int, val label: String, val generation: Long)
interface ProbeBackend {
    @Throws(Exception::class) fun devices(): List<ProbePort>
    /** Reject stale selections, and reject subsequent I/O once the opened generation is lost. */
    @Throws(Exception::class) fun open(port: ProbePort): MidiConnection
}
interface PassiveMidiBackend {
    val midiLog: MidiLog
    val monitorRunning: Boolean
    @Throws(Exception::class) fun monitorPorts(): List<ProbePort>
    @Throws(Exception::class) fun startMonitor(ids: List<Int>)
    fun stopMonitor()
}
