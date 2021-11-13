package cz.android.logtoolkit.test

import android.util.Log
import cz.android.logtoolkit.worker.Logger

object TestLog {
    private const val TAG = "TestLog"

    fun d(tag: String, message: String): Int {
        dispatchLog(Log.DEBUG, tag, message)
        return Log.d(tag, message)
    }

    fun i(tag: String, message: String): Int {
        dispatchLog(Log.INFO, tag, message)
        return Log.i(tag, message)
    }

    fun v(tag: String, message: String): Int {
        dispatchLog(Log.VERBOSE, tag, message)
        return Log.v(tag, message)
    }

    fun w(tag: String, message: String): Int {
        dispatchLog(Log.WARN, tag, message)
        return Log.w(tag, message)
    }

    fun e(tag: String, message: String): Int {
        dispatchLog(Log.ERROR, tag, message)
        return Log.e(tag, message)
    }

    fun w(throwable: Throwable): Int {
        dispatchLog(Log.WARN, TAG, Log.getStackTraceString(throwable))
        return Log.w(TAG, "", throwable)
    }

    fun w(tag: String, throwable: Throwable): Int {
        dispatchLog(Log.WARN, tag, Log.getStackTraceString(throwable))
        return Log.w(tag, "", throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable): Int {
        dispatchLog(Log.WARN, tag, message + '\n' + Log.getStackTraceString(throwable))
        return Log.w(tag, message, throwable)
    }

    fun e(throwable: Throwable): Int {
        dispatchLog(Log.WARN, TAG, Log.getStackTraceString(throwable))
        return e(throwable, null)
    }

    fun e(throwable: Throwable, message: String?): Int {
        dispatchLog(Log.WARN, TAG, message + '\n' + Log.getStackTraceString(throwable))
        return Log.e(TAG, message, throwable)
    }

    private fun dispatchLog(level: Int, tag: String, message: String) {
        Logger.post(level, tag, message)
    }
}
