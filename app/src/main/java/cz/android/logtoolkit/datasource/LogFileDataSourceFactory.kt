package cz.android.logtoolkit.datasource

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.paging.DataSource
import java.io.File

/**
 * The Log file data source factory.
 * This factory is responsible for us to create the instance of datasource.
 * @see LogFileDataSource
 */
class LogFileDataSourceFactory(private val file: File) : DataSource.Factory<Int, Long>() {
    private val mListDataSourceMutableLiveData = MutableLiveData<LogFileDataSource>()

    override fun create(): DataSource<Int, Long> {
        val dataSource = LogFileDataSource(file)
        //Once we recreate the instance we will restore the log level and filter keyword from old datasource.
        val oldDataSource = mListDataSourceMutableLiveData.value
        if (null != oldDataSource) {
            dataSource.logLevel = oldDataSource.logLevel
            dataSource.logFilterKeyword = oldDataSource.logFilterKeyword
        }
        mListDataSourceMutableLiveData.postValue(dataSource)
        return dataSource
    }

    fun getLogDataSourceMutableLiveData(): LiveData<LogFileDataSource> {
        return mListDataSourceMutableLiveData
    }
}