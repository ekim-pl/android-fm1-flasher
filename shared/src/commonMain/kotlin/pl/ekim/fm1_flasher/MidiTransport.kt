package pl.ekim.fm1_flasher

data class ProbePort(val id: Int, val label: String)
interface ProbeBackend {
    @Throws(Exception::class) fun devices(): List<ProbePort>
    @Throws(Exception::class) fun open(id: Int): MidiConnection
}
interface PassiveMidiBackend {
    val midiLog: MidiLog
    val monitorRunning: Boolean
    @Throws(Exception::class) fun monitorPorts(): List<ProbePort>
    @Throws(Exception::class) fun startMonitor(ids: List<Int>)
    fun stopMonitor()
}
