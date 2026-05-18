package com.sa.posprinter.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.sa.posprinter.R
import com.sa.posprinter.model.PreviewItem

class PreviewAdapter(
    private val items: List<PreviewItem>
) : RecyclerView.Adapter<PreviewAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvKey: TextView = view.findViewById(R.id.tvKey)
        val tvValue: TextView = view.findViewById(R.id.tvValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_key_value, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.tvKey.text = item.key
        holder.tvValue.text = item.value
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<PreviewItem> {
        return items
    }

}
