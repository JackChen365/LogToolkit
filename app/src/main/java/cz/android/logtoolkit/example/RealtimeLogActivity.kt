package cz.android.logtoolkit.example

import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.paging.PagedList
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import com.cz.android.sample.library.component.document.SampleDocument
import cz.android.logtoolkit.R
import cz.android.logtoolkit.adapter.LogMessageItemDetailsLookup
import cz.android.logtoolkit.adapter.LogfileAdapter
import cz.android.logtoolkit.databinding.ActivityRealtimeLogBinding
import cz.android.logtoolkit.reader.BufferedChannelReader
import cz.android.logtoolkit.share.ShareTextHelper
import cz.android.logtoolkit.test.TestLog
import cz.android.logtoolkit.viewmodel.LogFileViewModel
import cz.android.logtoolkit.viewmodel.LogFileViewModelFactory
import cz.android.logtoolkit.worker.Logger
import java.io.File


/**
 * This activity is to demonstrate how we could monitor the log in real-time.
 *
 * @see Logger.observer Observe the log message event.
 */
@SampleDocument("realtime.md")
class RealtimeLogActivity : AppCompatActivity() {
    companion object {
        private val TAG = RealtimeLogActivity::class.java.simpleName
    }

    private lateinit var mRecyclerView: RecyclerView
    private var mLogLevel: Int = Log.INFO

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityRealtimeLogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        mRecyclerView = binding.recyclerView
        val outputFile = Logger.getOutputFile()
        binding.recyclerView.layoutManager = object : LinearLayoutManager(this) {
            override fun onAdapterChanged(oldAdapter: RecyclerView.Adapter<*>?, newAdapter: RecyclerView.Adapter<*>?) {
                super.onAdapterChanged(oldAdapter, newAdapter)
                alwaysScrollToBottom(binding.recyclerView, newAdapter)
            }
        }
        val listDivider = ContextCompat.getDrawable(this, R.drawable.list_divider_dark)
        if (null != listDivider) {
            val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
            dividerItemDecoration.setDrawable(listDivider)
            binding.recyclerView.addItemDecoration(dividerItemDecoration)
        }

        val viewModel = ViewModelProvider(this, LogFileViewModelFactory(outputFile)).get(LogFileViewModel::class.java)
        viewModel.getLogLiveData().observe(this) { pagedList ->
            initialAdapterAndSubmitList(outputFile, binding.recyclerView, pagedList)
        }

        Logger.observer(this) { viewModel.invalidate() }
        binding.radioLayout.setOnCheckedChangeListener { _, index, isChecked ->
            if (isChecked) {
                mLogLevel = index + Log.VERBOSE
            }
        }
        var index = 0
        binding.printoutButton.setOnClickListener {
            when (mLogLevel) {
                Log.VERBOSE -> TestLog.v(TAG, "Message:${index++}")
                Log.DEBUG -> TestLog.d(TAG, "Message:${index++}")
                Log.INFO -> TestLog.i(TAG, "Message:${index++}")
                Log.WARN -> TestLog.w(TAG, "Message:${index++}")
                else -> TestLog.e(TAG, "Message:${index++}")
            }
        }
    }

    private fun initialAdapterAndSubmitList(
        outputFile: File,
        recyclerView: RecyclerView,
        pagedList: PagedList<Long>?
    ) {
        val adapter = LogfileAdapter(this, outputFile)
        recyclerView.adapter = adapter
        val itemDetailsLookup = LogMessageItemDetailsLookup(recyclerView)
        val selectionTracker = SelectionTracker.Builder(
            "LogMessageSelection",
            recyclerView,
            StableIdKeyProvider(recyclerView),
            itemDetailsLookup,
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(SelectionPredicates.createSelectAnything()).build()
        adapter.setSelectionTracker(selectionTracker)
        adapter.submitList(pagedList)
    }

    private fun alwaysScrollToBottom(recyclerView: RecyclerView, newAdapter: RecyclerView.Adapter<*>?) {
        val linearSmoothScroller = LinearSmoothScroller(this)
        newAdapter?.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                dispatchAdapterChangeEvent()
            }

            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                super.onItemRangeRemoved(positionStart, itemCount)
                dispatchAdapterChangeEvent()
            }

            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount)
                dispatchAdapterChangeEvent()
            }

            private fun dispatchAdapterChangeEvent() {
                smoothScrollToBottom(recyclerView, linearSmoothScroller)
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_share_item, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_item_share) {
            shareSelectedLogMessage()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shareSelectedLogMessage() {
        val adapter = mRecyclerView.adapter
        if (null != adapter && adapter is LogfileAdapter) {
            val selection = adapter.getSelectionTracker()?.selection
            if (null != selection) {
                val outputFile = Logger.getOutputFile()
                BufferedChannelReader(outputFile.inputStream().channel).use { reader ->
                    val output = StringBuilder()
                    for (index in selection) {
                        val item = adapter.getItem(index.toInt())
                        if (null != item) {
                            val start = item.shr(32)
                            val end = item.and(0xFFFFFFFF)
                            reader.position(start)
                            val text = reader.readString((end - start).toInt())
                            output.append("$text\n")
                        }
                    }
                    ShareTextHelper.createChooserAndShow(this, "Share message to", output.toString())
                }
            }
        }
    }

    private fun smoothScrollToBottom(recyclerView: RecyclerView, linearSmoothScroller: LinearSmoothScroller) {
        val adapter = recyclerView.adapter
        val layoutManager = recyclerView.layoutManager
        if (null != adapter && null != layoutManager && layoutManager is LinearLayoutManager) {
            val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
            if (firstVisibleItemPosition != RecyclerView.NO_POSITION && firstVisibleItemPosition + layoutManager.childCount < adapter.itemCount) {
                linearSmoothScroller.targetPosition = adapter.itemCount - 1
                layoutManager.startSmoothScroll(linearSmoothScroller)
            }
        }
    }

    override fun onBackPressed() {
        if (clearSelection()) return
        super.onBackPressed()
    }

    private fun clearSelection(): Boolean {
        val adapter = mRecyclerView.adapter
        if (null != adapter && adapter is LogfileAdapter) {
            val selectionTracker = adapter.getSelectionTracker()
            if (null != selectionTracker && !selectionTracker.selection.isEmpty) {
                selectionTracker.clearSelection()
                return true
            }
        }
        return false
    }
}