package com.translateassist.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.translateassist.R
import com.translateassist.translation.TranslationLinePair

class TranslationPairAdapter(
    private val pairs: List<TranslationLinePair>,
    private val onCopyLine: ((String) -> Unit)? = null
) : RecyclerView.Adapter<TranslationPairAdapter.PairVH>() {

    class PairVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val original: TextView = itemView.findViewById(R.id.original_line)
        val translated: TextView = itemView.findViewById(R.id.translated_line)
        val transliterated: TextView? = itemView.findViewById(R.id.transliterated_line)
        // no badge now
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PairVH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_translation_pair, parent, false)
        return PairVH(v)
    }

    override fun onBindViewHolder(holder: PairVH, position: Int) {
        val item = pairs[position]
        holder.original.text = item.original
        val translationLine = item.translation ?: "—"
        val transliterationLine = item.transliteration ?: "—"
        holder.translated.text = "TR: $translationLine"
        holder.transliterated?.let { tlView ->
            tlView.visibility = View.VISIBLE
            tlView.text = "TL: $transliterationLine"
        }
        holder.itemView.setOnLongClickListener {
            // Copy concatenated translation + transliteration
            val combined = buildString {
                append(item.translation ?: "")
                if (!item.transliteration.isNullOrBlank()) {
                    if (isNotEmpty()) append(" | ")
                    append(item.transliteration)
                }
            }
            onCopyLine?.invoke(combined.ifBlank { item.original })
            true
        }
    }

    override fun getItemCount(): Int = pairs.size
}
