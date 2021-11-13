package cz.android.logtoolkit.example

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.cz.android.sample.library.component.document.SampleDocument
import cz.android.logtoolkit.adapter.LogfileAdapter
import cz.android.logtoolkit.R
import cz.android.logtoolkit.databinding.ActivityLogcatBinding
import cz.android.logtoolkit.viewmodel.LogFileViewModel
import cz.android.logtoolkit.viewmodel.LogFileViewModelFactory
import java.io.File

/**
 * This activity is for us to test the how we read log from a local file.
 * Besides, We can filter the log by keyword and log level.
 * @see LogFileViewModel.filterKeyword
 * @see LogFileViewModel.filterLevel
 */
@SampleDocument("logcat.md")
class LogcatActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLogcatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        //Create a temp file from the assets file: test.txt
        val inputStream = assets.open("test.txt")
        val tempFile = File.createTempFile("tmp", "txt")
        tempFile.writeBytes(inputStream.readBytes())
        tempFile.deleteOnExit()
        inputStream.close()

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        val listDivider = ContextCompat.getDrawable(this, R.drawable.list_divider_dark)
        if (null != listDivider) {
            val dividerItemDecoration = DividerItemDecoration(this, LinearLayoutManager.VERTICAL)
            dividerItemDecoration.setDrawable(listDivider)
            binding.recyclerView.addItemDecoration(dividerItemDecoration)
        }
        val viewModel = ViewModelProvider(this, LogFileViewModelFactory(tempFile)).get(LogFileViewModel::class.java)
        viewModel.getLogLiveData().observe(this) { pagedList ->
            val adapter = LogfileAdapter(this, tempFile)
            binding.recyclerView.adapter = adapter
            adapter.submitList(pagedList)
        }

        binding.filterLogTag.setText("LiveChannelAdapter")
        //Filter the log by the given keyword.
        binding.applyButton.setOnClickListener {
            viewModel.filterKeyword(binding.filterLogTag.text)
        }
        //Filter the log by the different log level.
        binding.radioLayout.setOnCheckedChangeListener { _, index, isChecked ->
            if (isChecked) {
                viewModel.filterLevel(index + Log.VERBOSE)
            }
        }
    }
}