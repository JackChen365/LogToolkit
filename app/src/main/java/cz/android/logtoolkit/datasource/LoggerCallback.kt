package cz.android.logtoolkit.datasource

import android.util.Log

/**
 * The log data source callback.
 * This interface is responsible for us to filter the keyword and level and so on.
 * @see LogFileDataSource
 */
interface LoggerCallback {
    /**
     * Filter log by the keyword.
     */
    fun filterKeyword(keyword: CharSequence)

    /**
     * Filter log by the log level.
     * @see Log.ASSERT
     * @see Log.INFO
     * @see Log.WARN
     * @see Log.DEBUG
     * @see Log.ERROR
     */
    fun filterLevel(level: Int)

    /**
     * Close the related resources in data source
     */
    fun closeDataSource()

    /**
     * Make the datasource invalidate.
     */
    fun invalidate()
}