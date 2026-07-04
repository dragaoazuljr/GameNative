package com.winlator.shaders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.gamenative.R

class ShaderListAdapter(
    private val onShaderClick: (ShaderEntry) -> Unit,
    private val onFavoriteToggle: (String) -> Unit
) : ListAdapter<ShaderEntry, ShaderListAdapter.ViewHolder>(ShaderDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameText: TextView = view.findViewById(R.id.shaderName)
        val paramText: TextView = view.findViewById(R.id.shaderParam)
        val favoriteButton: ImageButton = view.findViewById(R.id.favoriteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shader_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shader = getItem(position)
        holder.nameText.text = shader.name
        holder.paramText.text = shader.meta?.params?.firstOrNull()?.label ?: ""
        holder.favoriteButton.setImageResource(
            if (shader.isFavorite) android.R.drawable.star_big_on
            else android.R.drawable.star_big_off
        )

        holder.itemView.setOnClickListener { onShaderClick(shader) }
        holder.favoriteButton.setOnClickListener {
            onFavoriteToggle(shader.relativePath)
        }
    }

    class ShaderDiffCallback : DiffUtil.ItemCallback<ShaderEntry>() {
        override fun areItemsTheSame(oldItem: ShaderEntry, newItem: ShaderEntry) =
            oldItem.path == newItem.path

        override fun areContentsTheSame(oldItem: ShaderEntry, newItem: ShaderEntry) =
            oldItem == newItem
    }
}
