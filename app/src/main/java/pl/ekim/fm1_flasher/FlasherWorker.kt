package pl.ekim.fm1_flasher

import java.util.concurrent.Executors
import kotlin.concurrent.withLock

/** One ordered owner for library and fleet I/O, including work submitted by main-thread callbacks. */
internal class FlasherWorker {
    private val executor = Executors.newSingleThreadExecutor()
    fun execute(action: () -> Unit) = executor.execute { CatalogOperations.lock.withLock(action) }
    fun shutdownNow() { executor.shutdownNow() }
}
