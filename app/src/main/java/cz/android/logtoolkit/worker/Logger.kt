package cz.android.logtoolkit.worker

import android.annotation.SuppressLint
import android.util.Log
import androidx.core.util.Preconditions
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import java.io.File
import java.time.LocalDateTime

/**
 * The simple logger object.
 */
object Logger {
    // /data/user/0/cz.android.logtoolkit/cache
    private val packageName = File(System.getProperty("java.io.tmpdir")).parentFile.name
    private lateinit var workThread: WorkThread
    private val levelMapper = mutableMapOf(
        Log.INFO to "I", Log.DEBUG to "D", Log.INFO to "I",
        Log.WARN to "W", Log.ERROR to "E", Log.ASSERT to "A"
    )

    fun injectLoggerService(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_CREATE) {
                    startService()
                } else if (event == Lifecycle.Event.ON_DESTROY) {
                    lifecycleOwner.lifecycle.removeObserver(this)
                    stopService()
                }
            }
        })
    }

    fun getOutputFile(): File {
        return workThread.getOutputFile()
    }

    fun setOutputFile(tempFile: File) {
        workThread.setOutputFile(tempFile)
    }

    /**
     * Enqueue a message to the work thread.
     * @param message
     */
    @SuppressLint("RestrictedApi")
    fun post(level: Int, tag: String, message: String) {
        Preconditions.checkState(this::workThread.isInitialized, "The work thread should be initialized before start use it.")
        workThread.post(formatMessage(level, tag, message))
    }

    @SuppressLint("RestrictedApi")
    fun observer(lifecycleOwner: LifecycleOwner, observer: Observer<Int>) {
        Preconditions.checkState(this::workThread.isInitialized, "The work thread should be initialized before start use it.")
        workThread.observer(lifecycleOwner, observer)
    }

    /**
     * Start the log service.
     */
    private fun startService() {
        workThread = WorkThread()
        workThread.startWorker()
    }

    /**
     * Stop the log service.
     */
    private fun stopService() {
        workThread.stopWorker()
    }

    private fun formatMessage(level: Int, tag: String, message: String): String {
        val levelText = levelMapper.getValue(level)
        //2021-11-10 18:56:46.796 12401-13044/cz.android.logtoolkit I/Appboy v8.1.0 .bo.app.q1:
        return LocalDateTime.now().toString() + "/" + packageName + " " + levelText + "/" + tag + ": " + message + "\n"
    }
}