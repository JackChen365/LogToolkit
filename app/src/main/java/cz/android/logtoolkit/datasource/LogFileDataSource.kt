package cz.android.logtoolkit.datasource

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.IntDef
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.PositionalDataSource
import androidx.paging.PositionalDataSource.LoadRangeCallback
import androidx.paging.PositionalDataSource.LoadRangeParams
import cz.android.logtoolkit.reader.BufferedChannelReader
import java.io.File

/**
 * The datasource use a specific file to analysis the log message.
 * Usually the log will start with: 2021-11-10 18:56:46.811 12401-13067/com.logtool.dev I/System.out: (HTTPLog)-Static: isSBSettingEnabled false
 * However, Some of the log message go with multiple lines.
 * <p>
 *     2021-11-10 18:56:52.068 12401-12401/com.logtool.dev I/Glide: Root cause (1 of 1)
 *          com.bumptech.glide.load.HttpException: Not Found, status code: 404
 *          at com.bumptech.glide.integration.okhttp3.OkHttpStreamFetcher.onResponse(OkHttpStreamFetcher.java:71)
 *          [...]
 * </p>
 * So here we have to collect the start position and its corresponding end position.
 *
 * This datasource support will return a long variable list for the PagedListAdapter.
 * We use the long variable to represent both start position and end position.
 * Here is some code snippet how we resovel the start position and end position.
 * <p>
 *     val var = getItem(0)
 *     val start = index.shr(32)
 *     val end = index.and(0xFFFFFFFF)
 * </p>
 *
 * For realtime fetching.
 * We will hold the [LoadRangeParams] and [LoadRangeCallback] once we receive the signal which is the [invalidate] event, we help the user to proceed the load work.
 *
 */
class LogFileDataSource(file: File) : PositionalDataSource<Long>(), LoggerCallback {
    companion object {
        private const val READY_TO_FETCH = 0
        private const val INITIAL_FETCHING = 1
        private const val APPEND_FETCHING = 2
        private const val PENDING_INITIAL_FETCHING = 3
        private const val PENDING_APPEND_FETCHING = 4
        private const val DONE_FETCHING = 5
        private const val DATA_IS_INVALID = 6

        private const val UPDATE_THROTTLE = 1000L
        private const val LOG_TAG_LEVEL_INDEX = 2
        private const val LOG_TAG_INDEX = 3
        private val LOG_REGEX = "[^/]+/([\\w\\.]+)\\s(\\w)/([^:]+)".toRegex()
        private val LOG_LEVEL_MAPPER = mutableMapOf(
            "I" to Log.INFO, "D" to Log.DEBUG, "I" to Log.INFO,
            "W" to Log.WARN, "E" to Log.ERROR, "A" to Log.ASSERT
        )
    }

    @IntDef(
        READY_TO_FETCH,
        INITIAL_FETCHING,
        APPEND_FETCHING,
        PENDING_INITIAL_FETCHING,
        PENDING_APPEND_FETCHING,
        DONE_FETCHING,
        DATA_IS_INVALID
    )
    internal annotation class FetchState

    @FetchState
    private var mWorkerState = READY_TO_FETCH
    private var mLoadInitialParams: LoadInitialParams? = null
    private var mPendingInitialCallback: LoadInitialCallback<Long>? = null
    private var mLoadRangeParams: LoadRangeParams? = null
    private var mPendingCallback: LoadRangeCallback<Long>? = null
    private var mUpdateHandler = Handler(Looper.getMainLooper())

    @SuppressLint("RestrictedApi")
    private var mRefreshAction = Runnable {
        if (mWorkerState == PENDING_INITIAL_FETCHING) {
            val pendingLoadParams = mLoadInitialParams
            val pendingCallback = mPendingInitialCallback
            if (null != pendingLoadParams && null != pendingCallback) {
                ArchTaskExecutor.getIOThreadExecutor().execute {
                    loadInitial(pendingLoadParams, pendingCallback)
                }
            }
        }
        if (mWorkerState == PENDING_APPEND_FETCHING) {
            val pendingLoadRangeParams = mLoadRangeParams
            val pendingCallback = mPendingCallback
            if (null != pendingLoadRangeParams && null != pendingCallback) {
                ArchTaskExecutor.getIOThreadExecutor().execute {
                    loadRange(pendingLoadRangeParams, pendingCallback)
                }
            }
        }
    }
    private var mBufferedChannelReader: BufferedChannelReader = BufferedChannelReader(file.inputStream().channel)
    private var mFetchState = MutableLiveData<Int>()
    internal var logFilterKeyword: CharSequence? = null
    internal var logLevel = Log.INFO

    override fun loadInitial(params: LoadInitialParams, callback: LoadInitialCallback<Long>) {
        val reader = mBufferedChannelReader
        if (!reader.isOpen) {
            callback.onResult(emptyList(), 0)
            return
        }
        if (reader.position() == reader.size()) {
            mLoadInitialParams = params
            mPendingInitialCallback = callback
            dispatchFetchState(PENDING_INITIAL_FETCHING)
            return
        }
        //setting load state so that the UI can know that progress of data fetching
        dispatchFetchState(INITIAL_FETCHING)
        val list = mutableListOf<Long>()
        var pageIndex = 1
        var start = reader.position()
        var position = reader.position()
        var line = reader.readLine()
        var shouldRecordLine = false
        while (pageIndex < params.pageSize && null != line) {
            val matcher = LOG_REGEX.find(line)
            if (null != matcher) {
                val levelText = matcher.groups[LOG_TAG_LEVEL_INDEX]?.value
                val tag = matcher.groups[LOG_TAG_INDEX]?.value
                if (null != levelText && null != tag) {
                    val level = LOG_LEVEL_MAPPER.getValue(levelText)
                    if (shouldRecordLine) {
                        shouldRecordLine = false
                        list.add(start.shl(32) + position)
                        pageIndex++
                    }
                    if ((logFilterKeyword.isNullOrBlank() && logLevel <= level) || (logFilterKeyword == tag && logLevel <= level)) {
                        shouldRecordLine = true
                        start = position
                    }
                }
            }
            position = reader.position()
            line = reader.readLine()
        }
        if (shouldRecordLine) {
            list.add(start.shl(32) + position)
        }
        reader.position(position)
        callback.onResult(list, 0)
        dispatchFetchState(DONE_FETCHING)
    }

    private fun dispatchFetchState(@FetchState fetchState: Int) {
        mWorkerState = fetchState
        mFetchState.postValue(fetchState)
    }

    override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<Long>) {
        val reader = mBufferedChannelReader
        if (!reader.isOpen) {
            callback.onResult(emptyList())
            return
        }
        if (reader.position() == reader.size()) {
            mLoadRangeParams = params
            mPendingCallback = callback
            dispatchFetchState(PENDING_APPEND_FETCHING)
            return
        }
        mLoadRangeParams = null
        mPendingCallback = null
        dispatchFetchState(APPEND_FETCHING)
        var pageIndex = 1
        val list = mutableListOf<Long>()
        var start = reader.position()
        var position = reader.position()
        var line = reader.readLine()
        var shouldRecordLine = false
        while (pageIndex < params.loadSize && null != line) {
            val matcher = LOG_REGEX.find(line)
            if (null != matcher) {
                val levelText = matcher.groups[LOG_TAG_LEVEL_INDEX]?.value
                val tag = matcher.groups[LOG_TAG_INDEX]?.value
                if (null != levelText && null != tag) {
                    val level = LOG_LEVEL_MAPPER.getValue(levelText)
                    if (shouldRecordLine) {
                        shouldRecordLine = false
                        list.add(start.shl(32) + position)
                        pageIndex++
                    }
                    if ((logFilterKeyword.isNullOrBlank() && logLevel <= level) || (logFilterKeyword == tag && logLevel <= level)) {
                        shouldRecordLine = true
                        start = position
                    }
                }
            }
            position = reader.position()
            line = reader.readLine()
        }
        if (shouldRecordLine) {
            list.add(start.shl(32) + position)
        }
        reader.position(position)
        callback.onResult(list)
        dispatchFetchState(DONE_FETCHING)
    }

    override fun invalidate() {
        //When the worker state is pending. We need to fetch the data after user invoked invalidate.
        if (mWorkerState == PENDING_INITIAL_FETCHING || mWorkerState == PENDING_APPEND_FETCHING) {
            mUpdateHandler.removeCallbacks(mRefreshAction)
            mUpdateHandler.postDelayed(mRefreshAction, UPDATE_THROTTLE)
        }
        //Only we change the filter keyword, and log level could make the worker state as DATA_IS_INVALID
        if (mWorkerState == DATA_IS_INVALID) {
            super.invalidate()
        }
    }

    fun getLoadState(): LiveData<Int> {
        return mFetchState
    }

    /**
     * Filter the log by using the given keyword.
     * We make the worker state as READY_TO_FETCH so that we will skip the pending fetching.
     */
    override fun filterKeyword(keyword: CharSequence) {
        mWorkerState = DATA_IS_INVALID
        mBufferedChannelReader.position(0)
        logFilterKeyword = keyword.toString()
        super.invalidate()
    }

    /**
     * Filter the log by the given log level.
     * We make the worker state as READY_TO_FETCH so that we will skip the pending fetching.
     */
    override fun filterLevel(level: Int) {
        mWorkerState = DATA_IS_INVALID
        mBufferedChannelReader.position(0)
        logLevel = level
        super.invalidate()
    }

    override fun closeDataSource() {
        mBufferedChannelReader?.close()
    }

}