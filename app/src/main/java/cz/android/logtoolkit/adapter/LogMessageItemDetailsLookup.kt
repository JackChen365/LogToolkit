package cz.android.logtoolkit.adapter

import android.view.MotionEvent
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView

class LogMessageItemDetailsLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<Long>() {

    override fun getItemDetails(event: MotionEvent): ItemDetails<Long>? {
        val view = recyclerView.findChildViewUnder(event.x, event.y)
        if (null != view) {
            val childViewHolder = recyclerView.getChildViewHolder(view) as? LogfileAdapter.ViewHolder
            return childViewHolder?.getItemDetails()
        }
        return null
    }
}