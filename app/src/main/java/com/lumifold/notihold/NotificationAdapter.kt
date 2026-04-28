package com.lumifold.notihold

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.dispose
import coil.request.CachePolicy
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * 爆速・高画質・全アイコン対応 NotificationAdapter
 */
class NotificationAdapter(
    private val onStarClicked: (NotificationDisplayModel) -> Unit,
    private val onItemLongClicked: (NotificationDisplayModel) -> Unit,
    private val onItemClicked: (NotificationDisplayModel) -> Unit,
    private val onRestoreClicked: (NotificationDisplayModel) -> Unit
) : PagingDataAdapter<NotificationListItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_DATE_HEADER = 1
        const val TYPE_ITEM = 2

        private object DiffCallback : DiffUtil.ItemCallback<NotificationListItem>() {
            override fun areItemsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return when {
                    oldItem is NotificationListItem.Header && newItem is NotificationListItem.Header -> 
                        oldItem.title == newItem.title
                    oldItem is NotificationListItem.DateHeader && newItem is NotificationListItem.DateHeader -> 
                        oldItem.date == newItem.date
                    oldItem is NotificationListItem.Item && newItem is NotificationListItem.Item -> 
                        oldItem.model.id == newItem.model.id
                    else -> false
                }
            }
            override fun areContentsTheSame(oldItem: NotificationListItem, newItem: NotificationListItem): Boolean {
                return oldItem == newItem
            }
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerTitle: TextView = view.findViewById(R.id.headerTitle)
    }

    class DateHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateTitle: TextView = view.findViewById(R.id.headerTitle)
    }

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appNameLabel: TextView = view.findViewById(R.id.appNameLabel)
        val title: TextView = view.findViewById(R.id.notificationTitle)
        val text: TextView = view.findViewById(R.id.notificationText)
        val time: TextView = view.findViewById(R.id.notificationTime)
        val starButton: ImageView = view.findViewById(R.id.starButton)
        val labelBadge: TextView = view.findViewById(R.id.labelBadge)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is NotificationListItem.Item) onItemClicked(item.model)
                }
            }
            view.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is NotificationListItem.Item) onItemLongClicked(item.model)
                }
                true
            }
            starButton.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val item = getItem(position)
                    if (item is NotificationListItem.Item) {
                        if (item.model.isTrashMode) onRestoreClicked(item.model) else onStarClicked(item.model)
                    }
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is NotificationListItem.Header -> TYPE_HEADER
            is NotificationListItem.DateHeader -> TYPE_DATE_HEADER
            else -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.notification_header, parent, false))
            TYPE_DATE_HEADER -> DateHeaderViewHolder(inflater.inflate(R.layout.notification_header, parent, false))
            else -> ItemViewHolder(inflater.inflate(R.layout.notification_item, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position) ?: return
        
        if (holder is HeaderViewHolder && item is NotificationListItem.Header) {
            holder.headerTitle.text = item.title
        } else if (holder is DateHeaderViewHolder && item is NotificationListItem.DateHeader) {
            holder.dateTitle.text = item.date
        } else if (holder is ItemViewHolder && item is NotificationListItem.Item) {
            val model = item.model

            holder.appNameLabel.text = model.appName
            holder.title.text = model.title
            holder.text.text = model.text
            holder.time.text = model.formattedTime

            val targetTypeface = if (model.isBold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            if (holder.title.typeface != targetTypeface) {
                holder.title.typeface = targetTypeface
                holder.appNameLabel.typeface = targetTypeface
            }

            // 【3段階アイコン取得 + 強制リサイズ実装】
            val placeholderIcon = AppIconFetcher.getResizedNotiHoldIcon(holder.itemView.context)
            
            // 優先順位1 & 2: キャッシュされたアイコンファイル (既にリサイズ済み)
            // 優先順位3: パッケージ名からの動的取得 (AppIconFetcherがリサイズ)
            val iconSource = if (model.iconPath != null && File(model.iconPath).exists()) {
                File(model.iconPath)
            } else {
                model.packageNameUri
            }

            holder.appIcon.load(iconSource) {
                dispatcher(Dispatchers.IO)
                
                val size = model.targetSize ?: 144
                size(size, size) // どんな画像もここで強制リサイズ
                
                diskCachePolicy(CachePolicy.ENABLED)
                crossfade(true)
                
                error(placeholderIcon)
                fallback(placeholderIcon)
                placeholder(placeholderIcon)
                
                allowHardware(true)
                transformations(CircleCropTransformation())
            }

            if (model.isTrashMode) {
                holder.starButton.setImageResource(R.drawable.ic_restore)
                holder.starButton.setColorFilter(0xFF388E3C.toInt())
            } else {
                holder.starButton.setImageResource(model.starIconRes)
                holder.starButton.setColorFilter(model.starTint)
            }

            if (model.label != null) {
                holder.labelBadge.visibility = View.VISIBLE
                holder.labelBadge.text = model.label
            } else {
                holder.labelBadge.visibility = View.GONE
            }
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ItemViewHolder) {
            holder.appIcon.dispose()
            holder.appIcon.setImageDrawable(null)
        }
    }
}
