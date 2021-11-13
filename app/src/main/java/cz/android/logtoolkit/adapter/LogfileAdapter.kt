package cz.android.logtoolkit.adapter

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.paging.PagedListAdapter
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.RecyclerView
import cz.android.logtoolkit.R
import cz.android.logtoolkit.databinding.ListLogItemBinding
import cz.android.logtoolkit.reader.BufferedChannelReader
import java.io.File
import java.nio.charset.Charset


/**
 * The log file adapter.
 * @see BufferedChannelReader we use this file channel as a cursor to help us read text from the logfile.
 * @see setSelectionTracker For item selection
 */
class LogfileAdapter(context: Context, private val file: File) :
    PagedListAdapter<Long, LogfileAdapter.ViewHolder>(DIFF_ITEM_CALLBACK) {
    private var mFileBufferedReader: BufferedChannelReader? = null
    private val mNormalTextSpan = ForegroundColorSpan(ContextCompat.getColor(context, R.color.log_text_normal_color))
    private val mErrorTextSpan = ForegroundColorSpan(ContextCompat.getColor(context, R.color.log_text_error_color))
    private var mSelectionTracker: SelectionTracker<Long>? = null

    private companion object {
        private const val MASK = 0xFFFFFFFF
        private const val LOG_LEVEL_INDEX = 2
        private const val LOG_TAG_INDEX = 3
        private val LOG_REGEX = "[^/]+/([\\w\\.]+)\\s(\\w)/([^:]+)".toRegex()
        private val DIFF_ITEM_CALLBACK = object : ItemCallback<Long>() {
            override fun areItemsTheSame(oldItem: Long, newItem: Long): Boolean {
                return oldItem === newItem
            }

            override fun areContentsTheSame(oldItem: Long, newItem: Long): Boolean {
                return oldItem == newItem
            }
        }
    }

    init {
        setHasStableIds(true)
    }

    fun setSelectionTracker(selectionTracker: SelectionTracker<Long>) {
        this.mSelectionTracker = selectionTracker
    }

    fun getSelectionTracker(): SelectionTracker<Long>? {
        return mSelectionTracker
    }

    public override fun getItem(position: Int): Long? {
        return super.getItem(position)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        mFileBufferedReader = BufferedChannelReader(file.inputStream().channel)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        //Release the file channel
        mFileBufferedReader?.close()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ListLogItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val reader = mFileBufferedReader
        //The item is a long variable.
        //The high 32 bits means the start position and low 32 bits means the end position.
        val index = getItem(position)
        if (null != reader && null != index) {
            val isItemSelected = true == mSelectionTracker?.isSelected(position.toLong())
            holder.bind(reader, index, mNormalTextSpan, mErrorTextSpan, isItemSelected)
        }
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class ViewHolder(private val binding: ListLogItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            reader: BufferedChannelReader,
            index: Long,
            normalTextSpan: ForegroundColorSpan,
            errorTextSpan: ForegroundColorSpan,
            isItemSelected: Boolean
        ) {
            val start = index.shr(32)
            val end = index.and(MASK)
            reader.position(start)
            val length = (end - start).toInt()
            var line = reader.readString(length, Charset.defaultCharset()) ?: return
            val builder = SpannableStringBuilder()
            builder.append("${adapterPosition + 1} ")
            val matcher = LOG_REGEX.find(line)
            if (null != matcher) {
                val level = matcher.groups[LOG_LEVEL_INDEX]
                val tag = matcher.groups[LOG_TAG_INDEX]
                if (null != level && null != tag) {
                    builder.append("[" + tag.value + "]")
                    builder.append(" ${level.value}")
                    builder.setSpan(
                        StyleSpan(Typeface.BOLD_ITALIC),
                        0,
                        builder.length,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    builder.append(line.substring(tag.range.last + 1).trimEnd())

                    builder.setSpan(
                        if ("E" == level.value) errorTextSpan else normalTextSpan,
                        0,
                        builder.length,
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    binding.textLog.text = builder
                }
            }
            itemView.isSelected = isItemSelected
        }

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = adapterPosition
                override fun getSelectionKey(): Long? = itemId
                override fun inSelectionHotspot(e: MotionEvent): Boolean {
                    return true
                }
            }
    }


}