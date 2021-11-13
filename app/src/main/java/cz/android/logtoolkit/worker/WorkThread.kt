package cz.android.logtoolkit.worker

import android.util.Log
import androidx.annotation.AnyThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * The work thread.
 * This is a thread model for multiple producers and single consumer. We consider two functions.
 * 1. The buffer queue's size.
 * 2. The workout time.
 *
 * Once the buffer is filled, We will process all the packages.
 * And if the time is out, No matter how many packages are inside the queue. We will flush the packages as well.
 * This actually depends on how packages you want to process.
 * For example:
 * If you want to write log to the file. You can not keep a file stream for a long time. That's a waste of resources.
 * So have a limit size of the queue or wait for a period of time is reasonable.
 *
 * Change the [MAX_CAPACITY] if you want the change the buffer size.
 */
internal class WorkThread : Thread {
    companion object {
        private const val TAG = "WorkThread"
        private const val TMP_DIR_ENV = "java.io.tmpdir"
        private val DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yy_MM_dd", Locale.ENGLISH)

        /**
         * max size in our queue. It's a limitation like a buffer so we don't have to wait too much time or store too many data
         */
        private const val MAX_CAPACITY = 1

        /**
         * max wait time, if we don't have enough data we just wait a few second and store all the data
         */
        private const val MAXIMUM_WAIT_TIME = 30 * 1000L

        /**
         * the mutex resource object
         */
        private val LOCK = Object()
    }

    private var mOutputFile: File

    /**
     * the data queue
     */
    private val mMessageQueue: Queue<String> = ArrayDeque()

    /**
     * active time
     */
    private var mActiveTimeMillis = 0L

    private var mUpdateLiveData: MutableLiveData<Int> = MutableLiveData()
    private var mCounter = 0

    /**
     * if this thread is running
     */
    @Volatile
    private var isRunning = false

    constructor()
    constructor(name: String) : super(name)

    init {
        // /data/user/0/cz.android.logtoolkit/cache
        val tempDir = System.getProperty(TMP_DIR_ENV)
        val tempFile = File(tempDir)
        if (!tempFile.exists()) {
            throw FileNotFoundException("The temp dir not existed.")
        }
        val logDir = File(tempFile.parentFile, "log")
        if (!logDir.exists()) logDir.mkdir()
        mOutputFile = File(logDir, DATE_TIME_FORMATTER.format(LocalDateTime.now()) + ".log")
        if (!mOutputFile.exists()) {
            mOutputFile.createNewFile()
        }
    }

    fun setOutputFile(outputFile: File) {
        this.mOutputFile = outputFile
    }

    override fun run() {
        super.run()
        try {
            while (!interrupted()) {
                //process message and wait
                flushDataAndWait()
                //If outside was stop. we stop
                if (!isRunning) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            //flush all the left messages
            flushData()
        }
    }

    /**
     * Check and flush all the data user put in the queue
     */
    private fun flushDataAndWait() {
        synchronized(LOCK) {
            if (!mMessageQueue.isEmpty()) {
                FileOutputStream(mOutputFile, true).writer().use { writer ->
                    while (!mMessageQueue.isEmpty()) {
                        val data = mMessageQueue.poll()
                        writer.append(data)
                    }
                }
                mUpdateLiveData.postValue(++mCounter)
                //refresh the active time
                mActiveTimeMillis = System.currentTimeMillis()
                //wait unit next time
            }
            try {
                LOCK.wait(MAXIMUM_WAIT_TIME)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * flush all the data
     */
    private fun flushData() {
        if (!mMessageQueue.isEmpty()) {
            FileOutputStream(mOutputFile, true).writer().use { writer ->
                while (!mMessageQueue.isEmpty()) {
                    val data = mMessageQueue.poll()
                    writer.write(data)
                }
            }
            mUpdateLiveData.postValue(++mCounter)
        }
        Log.i(TAG, "Flush the rest data to log file.")
    }

    /**
     * start this thread
     */
    @AnyThread
    fun startWorker() {
        if (isAlive) return
        mActiveTimeMillis = System.currentTimeMillis()
        isRunning = true
        super.start()
    }

    /**
     * stop this thread
     */
    @AnyThread
    fun stopWorker() {
        if (!isAlive) return
        synchronized(LOCK) {
            isRunning = false
            LOCK.notify()
        }
    }

    /**
     * put data to the queue
     */
    @AnyThread
    fun post(data: String) {
        if (!isAlive) return
        synchronized(LOCK) {
            mMessageQueue.offer(data)
            if (System.currentTimeMillis() - mActiveTimeMillis > MAXIMUM_WAIT_TIME || mMessageQueue.size >= MAX_CAPACITY) {
                LOCK.notify()
            }
        }
    }

    fun observer(lifecycleOwner: LifecycleOwner, observer: Observer<Int>) {
        if (!isAlive) return
        mUpdateLiveData.observe(lifecycleOwner, observer)
    }

    /**
     * if you want to notify the thread somehow, you could call this function
     */
    @AnyThread
    fun notifyWorker() {
        synchronized(LOCK) { LOCK.notify() }
    }

    fun getOutputFile(): File {
        return mOutputFile
    }
}