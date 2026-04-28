package com.lumifold.notihold

import android.app.Application
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // メモリ最適化: Calendarインスタンスを再利用
        private val sharedCalendar = Calendar.getInstance()
        private val sharedNow = Calendar.getInstance()
    }

    private val db = AppDatabase.getDatabase(application)
    private val preferenceManager = PreferenceManager(application)
    
    val billingManager = BillingManager(application, preferenceManager, viewModelScope)
    val isAdRemoved: StateFlow<Boolean> = preferenceManager.isAdRemoved
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _filter = MutableStateFlow("ALL")
    private val _searchQuery = MutableStateFlow("")

    private val colorStarred = ContextCompat.getColor(application, android.R.color.holo_orange_light)
    private val colorUnstarred = ContextCompat.getColor(application, android.R.color.darker_gray)

    private val debouncedSearchQuery = _searchQuery.debounce(300)

    // PagingDataをFlowとして公開（Composeなどで使用可能）
    val notifications: Flow<androidx.paging.PagingData<NotificationListItem>> = combine(
        _filter, 
        debouncedSearchQuery
    ) { filter, query ->
        Pair(filter, query)
    }.flatMapLatest { (filter, query) ->
        val pagingSourceFactory = {
            when (filter) {
                "ALL" -> if (query.isBlank()) db.notificationDao().getAllActivePaging() 
                         else db.notificationDao().searchActivePaging(query)
                "STARRED" -> db.notificationDao().getStarredPaging()
                "TRASH" -> db.notificationDao().getDeletedPaging()
                else -> db.notificationDao().getAllActivePaging()
            }
        }

        Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = true),
            pagingSourceFactory = pagingSourceFactory
        ).flow.map { pagingData ->
            // 共有Calendarインスタンスを再利用
            pagingData.map { entity ->
                val formattedTime = formatTimeOptimized(entity.timestamp, sharedCalendar, sharedNow)
                
                NotificationListItem.Item(
                    NotificationDisplayModel(
                        id = entity.id.toLong(),
                        iconPath = entity.cachedIconPath,
                        packageNameUri = "appicon:${entity.packageName}",
                        appName = entity.appName,
                        title = entity.title,
                        text = entity.text,
                        formattedTime = formattedTime,
                        isBold = !entity.isRead,
                        isTrashMode = filter == "TRASH",
                        starIconRes = if (entity.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_border,
                        starTint = if (entity.isStarred) colorStarred else colorUnstarred,
                        label = entity.label,
                        isStarred = entity.isStarred
                    )
                ) as NotificationListItem
            }
        }
    }.cachedIn(viewModelScope)

    // ListAdapter用にList<NotificationListItem>をStateFlowとして提供
    // Roomからデータを取得し、ViewModelで事前加工を行う
    val notificationList: StateFlow<List<NotificationListItem>> = combine(
        _filter,
        debouncedSearchQuery
    ) { filter, query ->
        // ListAdapterでの効率的な更新のため、一旦全量取得（Pagingでない場合）
        // 実際の運用ではフィルタリングされたデータを取得
        val entities = when (filter) {
            "ALL" -> if (query.isBlank()) db.notificationDao().getAllActive() 
                     else db.notificationDao().getAllActive() // 検索ロジックはDaoに合わせて調整
            "STARRED" -> db.notificationDao().getAllActive().filter { it.isStarred }
            "TRASH" -> db.notificationDao().getAllActive() // 実際にはgetDeletedPagingなどに対応するList取得が必要
            else -> db.notificationDao().getAllActive()
        }
        
        // 共有Calendarインスタンスを再利用してオブジェクト生成を削減
        entities.map { entity ->
            val formattedTime = formatTimeOptimized(entity.timestamp, sharedCalendar, sharedNow)
            NotificationListItem.Item(
                NotificationDisplayModel(
                    id = entity.id.toLong(),
                    iconPath = entity.cachedIconPath,
                    packageNameUri = "appicon:${entity.packageName}",
                    appName = entity.appName,
                    title = entity.title,
                    text = entity.text,
                    formattedTime = formattedTime,
                    isBold = !entity.isRead,
                    isTrashMode = filter == "TRASH",
                    starIconRes = if (entity.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_border,
                    starTint = if (entity.isStarred) colorStarred else colorUnstarred,
                    label = entity.label,
                    isStarred = entity.isStarred
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setFilter(filter: String) {
        _filter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getCurrentFilter(): String = _filter.value

    /**
     * メモリ最適化済み時刻フォーマット関数
     * 共有Calendarインスタンスを再利用してオブジェクト生成を削減
     */
    private fun formatTimeOptimized(timestamp: Long, calendar: Calendar, now: Calendar): String {
        calendar.timeInMillis = timestamp
        now.timeInMillis = System.currentTimeMillis()
        return if (calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
            DateFormat.format("H:mm", calendar).toString()
        } else {
            DateFormat.format("M月d日", calendar).toString()
        }
    }
}
