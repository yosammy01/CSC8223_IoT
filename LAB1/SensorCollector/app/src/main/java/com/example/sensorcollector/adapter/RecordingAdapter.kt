package com.example.sensorcollector.adapter

import android.graphics.Color
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.sensorcollector.databinding.ItemRecordingBinding
import com.example.sensorcollector.model.RecordingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingAdapter(
    private val onSaveClick: (RecordingSession) -> Unit,
    private val onShareClick: (RecordingSession) -> Unit,
    private val onDeleteClick: (RecordingSession) -> Unit
) : ListAdapter<RecordingSession, RecordingAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(val binding: ItemRecordingBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecordingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = getItem(position)
        val context = holder.itemView.context
        val binding = holder.binding

        val label = session.activityLabel
        binding.tvActivityBadge.text = label

        if (label.equals("Standing Still", ignoreCase = true)) {
            binding.tvActivityBadge.setBackgroundColor(Color.parseColor("#E8F5E9"))
            binding.tvActivityBadge.setTextColor(Color.parseColor("#2E7D32"))
        } else if (label.equals("Walking", ignoreCase = true)) {
            binding.tvActivityBadge.setBackgroundColor(Color.parseColor("#FFF3E0"))
            binding.tvActivityBadge.setTextColor(Color.parseColor("#E65100"))
        } else {
            binding.tvActivityBadge.setBackgroundColor(Color.parseColor("#E1F5FE"))
            binding.tvActivityBadge.setTextColor(Color.parseColor("#0288D1"))
        }

        val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
        binding.tvDate.text = dateFormat.format(Date(session.timestampMs))

        binding.tvFileName.text = session.fileName

        val formattedSize = Formatter.formatShortFileSize(context, session.fileSizeBytes)
        val durationSec = session.durationMs / 1000
        binding.tvDetails.text = String.format(
            Locale.getDefault(),
            "%,d samples • %ds • %s",
            session.sampleCount,
            durationSec,
            formattedSize
        )

        binding.btnSave.setOnClickListener { onSaveClick(session) }
        binding.btnShare.setOnClickListener { onShareClick(session) }
        binding.btnDelete.setOnClickListener { onDeleteClick(session) }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<RecordingSession>() {
            override fun areItemsTheSame(
                oldItem: RecordingSession,
                newItem: RecordingSession
            ): Boolean = oldItem.fileName == newItem.fileName

            override fun areContentsTheSame(
                oldItem: RecordingSession,
                newItem: RecordingSession
            ): Boolean = oldItem == newItem
        }
    }
}
