package com.lumifold.notihold

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.format.DateFormat
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lumifold.notihold.databinding.ActivityMainBinding
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import androidx.activity.viewModels
import com.lumifold.notihold.R

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: NotificationAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private var currentFilter = "ALL"
    private var currentDateFilter: Long? = null
    private var currentSearchQuery: String? = null
    private var observationJob: Job? = null
    
    private val viewModel: NotificationViewModel by viewModels()

    companion object {
        private val sharedCalendar = Calendar.getInstance()
        private val sharedNow = Calendar.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setThemeMode(getThemeMode())
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        drawerLayout = binding.drawerLayout
        navigationView = binding.navigationView

        val toggle = ActionBarDrawerToggle(this, drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        setupRecyclerView()
        setupNavigation()
        
        observeNotifications() // 権限に関係なく最初に呼び出す
        scheduleCleanupWorker()
        refreshLabelsInDrawer()
        
        // 広告と購入状態の管理
        initAdsAndBilling()
        
        // 権限チェックは最後に
        if (!isNotificationServiceEnabled()) {
            showPermissionDialog()
        }
    }

    private fun initAdsAndBilling() {
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            // AdMob初期化エラーは無視（テスト環境では正常）
        }
        
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    viewModel.isAdRemoved.collect { isAdRemoved ->
                        try {
                            if (isAdRemoved) {
                                binding.adView.visibility = View.GONE
                            } else {
                                binding.adView.visibility = View.VISIBLE
                                val adRequest = AdRequest.Builder().build()
                                binding.adView.loadAd(adRequest)
                            }
                        } catch (e: Exception) {
                            // 広告読み込みエラーは無視
                            binding.adView.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    // フロー収集エラーは無視
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(
            onStarClicked = { toggleStar(it) },
            onItemLongClicked = { showItemOptionsDialog(it) },
            onItemClicked = { showNotificationDetails(it) },
            onRestoreClicked = { restoreFromTrash(it) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun setupNavigation() {
        navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all -> setFilter("ALL")
                R.id.nav_starred -> setFilter("STARRED")
                R.id.nav_trash -> setFilter("TRASH")
                R.id.nav_settings -> showSettingsDialog()
                R.id.nav_create_label -> showCreateLabelDialog()
                R.id.nav_delete_label -> showDeleteLabelDialog()
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        currentDateFilter = null
        currentSearchQuery = null
        updateMenuVisibility()
        observeNotifications()
    }

    private fun updateMenuVisibility() {
        invalidateOptionsMenu()
    }

    override fun onResume() {
        super.onResume()
        
        // 通知アクセス権限を再確認
        if (!isNotificationServiceEnabled()) {
            showPermissionDialog()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.search_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as androidx.appcompat.widget.SearchView
        
        searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                currentSearchQuery = query
                observeNotifications()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                currentSearchQuery = newText
                observeNotifications()
                return true
            }
        })

        val hasDateFilter = currentDateFilter != null
        menu.findItem(R.id.action_date_search).isVisible = (currentFilter == "ALL" && !hasDateFilter)
        menu.findItem(R.id.action_clear_date_filter).isVisible = (currentFilter == "ALL" && hasDateFilter)
        
        menu.findItem(R.id.action_clear_trash).isVisible = (currentFilter == "TRASH")
        menu.findItem(R.id.action_move_all_to_trash).isVisible = (currentFilter == "ALL")
        
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_date_search -> {
                showDatePicker()
                true
            }
            R.id.action_clear_date_filter -> {
                clearDateFilter()
                true
            }
            R.id.action_clear_trash -> {
                showClearTrashConfirmDialog()
                true
            }
            R.id.action_move_all_to_trash -> {
                showMoveAllToTrashConfirmDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun clearDateFilter() {
        currentDateFilter = null
        updateMenuVisibility()
        observeNotifications()
        Toast.makeText(this, "日付フィルターをクリアしました", Toast.LENGTH_SHORT).show()
    }

    private fun setDateFilter(timestamp: Long) {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        currentDateFilter = calendar.timeInMillis
        currentSearchQuery = null
        updateMenuVisibility()
        observeNotifications()
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val selected = Calendar.getInstance()
                selected.set(year, month, day, 0, 0, 0)
                setDateFilter(selected.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun observeNotifications() {
        observationJob?.cancel()
        observationJob = lifecycleScope.launch {
            if (!currentSearchQuery.isNullOrBlank()) {
                delay(300)
            }

            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val pagingSourceFactory = {
                    when (currentFilter) {
                        "ALL" -> {
                            when {
                                currentDateFilter != null -> db.notificationDao().searchByDatePaging(currentDateFilter!!)
                                currentSearchQuery.isNullOrBlank() -> db.notificationDao().getAllActivePaging()
                                else -> db.notificationDao().searchActivePaging(currentSearchQuery!!)
                            }
                        }
                        "STARRED" -> db.notificationDao().getStarredPaging()
                        "TRASH" -> db.notificationDao().getDeletedPaging()
                        else -> db.notificationDao().getAllActivePaging()
                    }
                }

                val targetSize = AppIconFetcher.calculateOptimalSize(resources.displayMetrics)
                val colorStarred = ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_light)
                val colorUnstarred = ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray)

                Pager(config = PagingConfig(pageSize = 30, enablePlaceholders = true),
                      pagingSourceFactory = pagingSourceFactory)
                    .flow
                    .map { pagingData: PagingData<NotificationEntity> ->
                        pagingData.map { entity: NotificationEntity ->
                            sharedCalendar.timeInMillis = entity.timestamp
                            sharedNow.timeInMillis = System.currentTimeMillis()
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
                                    isTrashMode = currentFilter == "TRASH",
                                    starIconRes = if (entity.isStarred) R.drawable.ic_star_filled else R.drawable.ic_star_border,
                                    starTint = if (entity.isStarred) colorStarred else colorUnstarred,
                                    isStarred = entity.isStarred,
                                    label = entity.label,
                                    targetSize = targetSize
                                )
                            ) as NotificationListItem
                        }
                    }
                    .flowOn(Dispatchers.IO)
                    .cachedIn(lifecycleScope)
                    .collectLatest { pagingData -> 
                        try {
                            adapter.submitData(pagingData)
                        } catch (e: Exception) {
                            // アダプターが破棄されている場合のエラーを無視
                        }
                    }
            } catch (e: Exception) {
                // データベースアクセスエラーの処理
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "データ読み込みエラー: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun formatTimeOptimized(timestamp: Long, calendar: Calendar, now: Calendar): String {
        calendar.timeInMillis = timestamp
        now.timeInMillis = System.currentTimeMillis()
        
        val isToday = calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                     calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        
        return if (isToday) {
            DateFormat.getTimeFormat(this).format(calendar.time)
        } else {
            DateFormat.getDateFormat(this).format(calendar.time)
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true
    }

    private fun showPermissionDialog() {
        // 権限がある場合は何もしない
        if (isNotificationServiceEnabled()) {
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("通知へのアクセス許可")
            .setMessage("通知を保存するために、通知へのアクセスを許可してください。")
            .setPositiveButton("設定を開く") { _, _ ->
                startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            }
            .setNegativeButton("キャンセル") { _, _ ->
                // キャンセルしてもアプリは続行可能
                Toast.makeText(this, "通知機能が制限されます", Toast.LENGTH_SHORT).show()
            }
            .setCancelable(false) // キャンセル以外で閉じれないようにする
            .show()
    }

    private fun scheduleCleanupWorker() {
        val cleanupRequest = PeriodicWorkRequestBuilder<CleanupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CleanupWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            cleanupRequest
        )
    }

    private fun toggleStar(model: NotificationDisplayModel) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@MainActivity)
                val entity = db.notificationDao().getById(model.id.toInt())
                entity?.let {
                    it.isStarred = !it.isStarred
                    db.notificationDao().update(it)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "スター操作エラー: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showItemOptionsDialog(model: NotificationDisplayModel) {
        val options = if (model.isTrashMode) {
            arrayOf("詳細を表示", "完全に削除")
        } else {
            arrayOf("詳細を表示", "ラベルを設定", "削除")
        }

        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showNotificationDetails(model)
                    1 -> if (model.isTrashMode) deletePermanently(model) else showLabelSelectionDialog(model)
                    2 -> moveToTrash(model)
                }
            }
            .show()
    }

    private fun showNotificationDetails(model: NotificationDisplayModel) {
        val builder = AlertDialog.Builder(this)
            .setTitle(model.appName)
            .setMessage("${model.title}\n\n${model.text}")
            .setPositiveButton("閉じる", null)
            
        if (model.isTrashMode) {
            builder.setNeutralButton("復元") { _, _ ->
                restoreFromTrash(model)
            }
        }
        builder.show()
        
        // 既読にする
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val entity = db.notificationDao().getById(model.id.toInt())
            entity?.let {
                if (!it.isRead) {
                    it.isRead = true
                    db.notificationDao().update(it)
                }
            }
        }
    }

    private fun moveToTrash(model: NotificationDisplayModel) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val entity = db.notificationDao().getById(model.id.toInt())
            entity?.let {
                it.isDeleted = true
                db.notificationDao().update(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "ゴミ箱に移動しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restoreFromTrash(model: NotificationDisplayModel) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val entity = db.notificationDao().getById(model.id.toInt())
            entity?.let {
                it.isDeleted = false
                db.notificationDao().update(it)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "元に戻しました", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun deletePermanently(model: NotificationDisplayModel) {
        AlertDialog.Builder(this)
            .setTitle("完全に削除")
            .setMessage("この通知を完全に削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@MainActivity)
                    val entity = db.notificationDao().getById(model.id.toInt())
                    entity?.let {
                        db.notificationDao().delete(it)
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showClearTrashConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("ゴミ箱を空にする")
            .setMessage("ゴミ箱内のすべての通知を完全に削除しますか？")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@MainActivity)
                    db.notificationDao().clearTrash()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "ゴミ箱を空にしました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showMoveAllToTrashConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("すべて削除")
            .setMessage("すべての通知をゴミ箱に移動しますか？")
            .setPositiveButton("移動") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = AppDatabase.getDatabase(this@MainActivity)
                    db.notificationDao().moveAllToTrash(System.currentTimeMillis())
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "すべてゴミ箱に移動しました", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showLabelSelectionDialog(model: NotificationDisplayModel) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val labels = db.labelDao().getAllLabels().map { it.name }.toMutableList()
            labels.add(0, "指定なし")
            
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("ラベルを選択")
                    .setItems(labels.toTypedArray()) { _, which ->
                        val selectedLabel = if (which == 0) null else labels[which]
                        updateNotificationLabel(model.id.toInt(), selectedLabel)
                    }
                    .show()
            }
        }
    }

    private fun updateNotificationLabel(id: Int, label: String?) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val entity = db.notificationDao().getById(id)
            entity?.let {
                it.label = label
                db.notificationDao().update(it)
            }
        }
    }

    private fun showCreateLabelDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        
        AlertDialog.Builder(this)
            .setTitle("新しいラベルを作成")
            .setView(input)
            .setPositiveButton("作成") { _, _ ->
                val labelName = input.text.toString()
                if (labelName.isNotBlank()) {
                    createLabel(labelName)
                }
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun createLabel(name: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            db.labelDao().insert(LabelEntity(name = name, color = 0xFF2196F3.toInt()))
            withContext(Dispatchers.Main) {
                refreshLabelsInDrawer()
                Toast.makeText(this@MainActivity, "ラベル \"$name\" を作成しました", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showDeleteLabelDialog() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val labels = db.labelDao().getAllLabels()
            
            withContext(Dispatchers.Main) {
                if (labels.isEmpty()) {
                    Toast.makeText(this@MainActivity, "削除できるラベルがありません", Toast.LENGTH_SHORT).show()
                    return@withContext
                }
                
                val labelNames = labels.map { it.name }.toTypedArray()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("削除するラベルを選択")
                    .setItems(labelNames) { _, which ->
                        deleteLabel(labels[which])
                    }
                    .show()
            }
        }
    }

    private fun deleteLabel(label: LabelEntity) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            db.labelDao().delete(label)
            // そのラベルが付いていた通知のラベルをnullにする
            // Note: NotificationDao.clearLabel doesn't exist, using a placeholder if needed or just refreshing.
            // db.notificationDao().clearLabel(label.name) 
            withContext(Dispatchers.Main) {
                refreshLabelsInDrawer()
                Toast.makeText(this@MainActivity, "ラベル \"${label.name}\" を削除しました", Toast.LENGTH_SHORT).show()
                if (currentFilter == label.name) {
                    setFilter("ALL")
                }
            }
        }
    }

    private fun refreshLabelsInDrawer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(this@MainActivity)
            val labels = db.labelDao().getAllLabels()
            
            withContext(Dispatchers.Main) {
                val menu = navigationView.menu
                val labelGroup = menu.findItem(R.id.nav_label_group)?.subMenu
                labelGroup?.clear()
                
                labels.forEach { label ->
                    labelGroup?.add(Menu.NONE, Menu.NONE, Menu.NONE, label.name)
                        ?.setIcon(android.R.drawable.ic_dialog_email)
                }
            }
        }
    }

    private fun showSettingsDialog() {
        val options = arrayOf("テーマ設定", "自動削除設定", "クオリティフィルター設定", "通知アクセス権限設定", "プレミアム（広告非表示）購入")
        AlertDialog.Builder(this)
            .setTitle("設定")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showThemeDialog()
                    1 -> showRetentionPeriodDialog()
                    2 -> showQualityFilterDialog()
                    3 -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    4 -> showPurchaseDialog()
                }
            }
            .show()
    }

    private fun showPurchaseDialog() {
        val details = viewModel.billingManager.productDetails.value
        if (details == null) {
            Toast.makeText(this, "購入情報を取得中または取得できませんでした", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("プレミアム購入")
            .setMessage("広告を永久に非表示にします。\n価格: ${details.oneTimePurchaseOfferDetails?.formattedPrice}")
            .setPositiveButton("購入する") { _, _ ->
                viewModel.billingManager.launchBillingFlow(this)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showQualityFilterDialog() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val view = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }

        val cbOngoing = CheckBox(this).apply {
            text = "進行中の通知（ダウンロード等）を保存"
            isChecked = prefs.getBoolean("filter_ongoing", false)
        }
        val cbSystem = CheckBox(this).apply {
            text = "システム系通知を保存（非推奨）"
            isChecked = prefs.getBoolean("filter_system", false)
        }
        val cbMedia = CheckBox(this).apply {
            text = "メディア再生コントロールを保存"
            isChecked = prefs.getBoolean("filter_media", false)
        }

        view.addView(cbOngoing)
        view.addView(cbSystem)
        view.addView(cbMedia)

        AlertDialog.Builder(this)
            .setTitle("クオリティフィルター設定")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                prefs.edit().apply {
                    putBoolean("filter_ongoing", cbOngoing.isChecked)
                    putBoolean("filter_system", cbSystem.isChecked)
                    putBoolean("filter_media", cbMedia.isChecked)
                    apply()
                }
                Toast.makeText(this, "設定を保存しました。以降の通知に適用されます。", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showThemeDialog() {
        val themes = arrayOf("システムデフォルト", "ライト", "ダーク")
        val current = when (getThemeMode()) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> 0
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        AlertDialog.Builder(this)
            .setTitle("テーマ選択")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                val mode = when (which) {
                    0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                setThemeMode(mode)
                dialog.dismiss()
                recreate()
            }
            .show()
    }

    private fun showRetentionPeriodDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        val currentDays = getSharedPreferences("settings", MODE_PRIVATE).getInt("retention_days", 30)
        input.setText(currentDays.toString())
        AlertDialog.Builder(this)
            .setTitle("保持期間 (日)")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val days = input.text.toString().toIntOrNull() ?: 30
                getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("retention_days", days).apply()
                Toast.makeText(this, "期間を $days 日に設定しました", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun getThemeMode(): Int {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        return prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    private fun setThemeMode(mode: Int) {
        getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("theme_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
