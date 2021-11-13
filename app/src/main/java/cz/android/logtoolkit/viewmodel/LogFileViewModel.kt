package cz.android.logtoolkit.viewmodel

import android.os.AsyncTask
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import cz.android.logtoolkit.datasource.LogFileDataSource
import cz.android.logtoolkit.datasource.LogFileDataSourceFactory
import cz.android.logtoolkit.datasource.LoggerCallback
import java.io.File

/**
 * The log file viewModel.
 * @see LogFileDataSourceFactory
 */
class LogFileViewModel(file: File) : ViewModel() {

    companion object {
        private const val INITIAL_LOAD_SIZE = 30
        private const val PAGE_SIZE = 20
        private const val PREFETCH_DISTANCE = 5
    }

    private val mDataSourceFactory = LogFileDataSourceFactory(file)
    private val mDataSourceLiveData = mDataSourceFactory.getLogDataSourceMutableLiveData()
    private val mLoadState: LiveData<Int> =
        Transformations.switchMap(mDataSourceLiveData, LogFileDataSource::getLoadState)
    private var mCallback: LoggerCallback? = null
    private val mDataSourceObserver =
        Observer<LoggerCallback> { callback -> mCallback = callback }

    init {
        mDataSourceLiveData.observeForever(mDataSourceObserver)
    }

    fun getLogLiveData(): LiveData<PagedList<Long>> {
        //There are some parameters that we can config according to our use case
        val config = PagedList.Config.Builder()
            .setEnablePlaceholders(false)
            .setInitialLoadSizeHint(INITIAL_LOAD_SIZE)
            .setPageSize(PAGE_SIZE) //Configures how many data items in one page to be supplied to recycler view
            .setPrefetchDistance(PREFETCH_DISTANCE) // in first call how many pages to load
            .build()
        return LivePagedListBuilder(mDataSourceFactory, config)
            .setFetchExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
            .build()
    }


    fun getLoadState(): LiveData<Int> {
        return mLoadState
    }

    fun filterKeyword(keyword: CharSequence) {
        mCallback?.filterKeyword(keyword)
    }

    fun filterLevel(level: Int) {
        mCallback?.filterLevel(level)
    }

    fun invalidate() {
        mCallback?.invalidate()
    }

    override fun onCleared() {
        mCallback?.closeDataSource()
        mDataSourceLiveData.removeObserver(mDataSourceObserver)
        super.onCleared()
    }
}