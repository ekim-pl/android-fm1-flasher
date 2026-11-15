package pl.ekim.fm1_flasher

import android.content.Context
import java.util.concurrent.Executors

/** One file owner for service and WorkManager, so concurrent instances cannot overwrite events. */
internal class AndroidOperationLogger private constructor(context: Context) {
    private val worker = Executors.newSingleThreadExecutor()
    private val log = OperationLog(AndroidStorage(context.filesDir), System::currentTimeMillis)
    fun execute(action: (OperationLog) -> Unit) { worker.execute { action(log) } }
    fun record(event: String, message: String = "", level: String = "INFO") = execute {
        runCatching { it.append(level, event, message) }.onFailure { failure ->
            android.util.Log.e("FM1Diagnostics", "Could not persist operation log", failure)
        }
    }
    companion object {
        @Volatile private var instance: AndroidOperationLogger? = null
        fun get(context: Context): AndroidOperationLogger = instance ?: synchronized(this) {
            instance ?: AndroidOperationLogger(context.applicationContext).also { instance = it }
        }
    }
}
