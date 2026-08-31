package com.johnnytv.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class CategoryAdapter(
    private val onSelected: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val items = ArrayList<Category>()
    private var selectedPosition = RecyclerView.NO_POSITION

    fun submit(list: List<Category>) {
        items.clear()
        items.addAll(list)
        selectedPosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }

    fun selectAt(position: Int) {
        if (position < 0 || position >= items.size) return
        val previous = selectedPosition
        selectedPosition = position
        if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
        notifyItemChanged(position)
    }

    fun itemAt(position: Int): Category? = items.getOrNull(position)

    class VH(val label: TextView) : RecyclerView.ViewHolder(label)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false) as TextView
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.label.text = item.name
        holder.label.isActivated = position == selectedPosition
        holder.label.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current == RecyclerView.NO_POSITION) return@setOnClickListener
            selectAt(current)
            onSelected(items[current])
        }
    }

    override fun getItemCount(): Int = items.size
}

class StreamAdapter(
    private val onPlay: (StreamItem) -> Unit
) : RecyclerView.Adapter<StreamAdapter.VH>() {

    private val all = ArrayList<StreamItem>()
    private val shown = ArrayList<StreamItem>()
    private var query = ""

    fun submit(list: List<StreamItem>) {
        all.clear()
        all.addAll(list)
        applyFilter()
    }

    fun filter(text: String) {
        query = text
        applyFilter()
    }

    fun isEmpty(): Boolean = shown.isEmpty()

    private fun applyFilter() {
        shown.clear()
        val needle = query.trim().lowercase(Locale.getDefault())
        if (needle.isEmpty()) {
            shown.addAll(all)
        } else {
            for (item in all) {
                if (item.name.lowercase(Locale.getDefault()).contains(needle)) shown.add(item)
            }
        }
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.streamNumber)
        val title: TextView = view.findViewById(R.id.streamTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_stream, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = shown[position]
        holder.number.text = item.num
        holder.title.text = item.name
        holder.itemView.setOnClickListener {
            val current = holder.bindingAdapterPosition
            if (current == RecyclerView.NO_POSITION) return@setOnClickListener
            onPlay(shown[current])
        }
    }

    override fun getItemCount(): Int = shown.size
}
