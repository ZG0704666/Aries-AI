package com.ai.phoneagent.updates

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ai.phoneagent.BuildConfig
import com.ai.phoneagent.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INCLUDE_PRERELEASE = "extra_include_prerelease"
    }

    private val repo = ReleaseRepository()

    private lateinit var toolbar: MaterialToolbar
    private lateinit var switchPrerelease: SwitchMaterial
    private lateinit var recycler: RecyclerView
    private lateinit var progress: View
    private lateinit var tvError: TextView
    private lateinit var btnLoadMore: MaterialButton

    private var includePrerelease: Boolean = false
    private var page: Int = 1
    private var loading: Boolean = false

    private enum class LoadMoreMode {
        LoadMore,
        Retry,
    }

    private var loadMoreMode: LoadMoreMode = LoadMoreMode.LoadMore

    private val adapter by lazy {
        ReleaseHistoryAdapter(
            onDetails = { entry -> showDetails(entry) },
            onOpenRelease = { entry -> openReleaseUrlWithFeedback(entry.releaseUrl) },
            onDownload = { entry -> handleDownload(entry) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_update_history)

        setupEdgeToEdge()

        toolbar = findViewById(R.id.topAppBar)
        switchPrerelease = findViewById(R.id.switchPrerelease)
        recycler = findViewById(R.id.recyclerReleases)
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tvError)
        btnLoadMore = findViewById(R.id.btnLoadMore)

        toolbar.setNavigationOnClickListener { finish() }

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        includePrerelease = intent.getBooleanExtra(EXTRA_INCLUDE_PRERELEASE, false)
        switchPrerelease.isChecked = includePrerelease
        switchPrerelease.setOnCheckedChangeListener { _, checked ->
            includePrerelease = checked
            refresh()
        }

        btnLoadMore.setOnClickListener {
            when (loadMoreMode) {
                LoadMoreMode.LoadMore -> loadMore()
                LoadMoreMode.Retry -> loadPage(resetError = false)
            }
        }

        refresh()
    }

    private fun setupEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)?.let { controller ->
            val useLightSystemBarIcons = resources.getBoolean(R.bool.m3t_light_system_bars)
            controller.isAppearanceLightStatusBars = useLightSystemBarIcons
            controller.isAppearanceLightNavigationBars = useLightSystemBarIcons
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appBar)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, sys.top, 0, 0)
            insets
        }
    }

    private fun refresh() {
        page = 1
        adapter.submitList(emptyList())
        loadMoreMode = LoadMoreMode.LoadMore
        btnLoadMore.text = getString(R.string.m3t_updates_load_more)
        btnLoadMore.isEnabled = true
        loadPage(resetError = true)
    }

    private fun loadMore() {
        if (loading) return
        page += 1
        loadPage(resetError = false)
    }

    private fun loadPage(resetError: Boolean) {
        if (loading) return
        loading = true

        if (resetError) {
            tvError.visibility = View.GONE
            tvError.text = ""
        }
        progress.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repo.fetchReleasePage(page = page, perPage = 20)
            }

            progress.visibility = View.GONE
            loading = false

            result
                .onSuccess { list ->
                    val filtered = if (includePrerelease) list else list.filter { !it.isPrerelease }
                    if (page == 1) {
                        adapter.submitList(filtered)
                    } else {
                        adapter.appendList(filtered)
                    }

                    loadMoreMode = LoadMoreMode.LoadMore
                    btnLoadMore.isEnabled = true
                    btnLoadMore.text =
                        if (filtered.isNotEmpty()) {
                            getString(R.string.m3t_updates_load_more)
                        } else {
                            getString(R.string.m3t_updates_no_more)
                        }
                }
                .onFailure { e ->
                    tvError.visibility = View.VISIBLE
                    tvError.text = ReleaseUiUtil.formatError(e)

                    loadMoreMode = LoadMoreMode.Retry
                    btnLoadMore.isEnabled = true
                    btnLoadMore.text = getString(R.string.m3t_updates_retry)
                }
        }
    }

    private fun openReleaseUrlWithFeedback(url: String): Boolean {
        val opened = ReleaseUiUtil.openUrl(this, url)
        if (!opened) {
            Toast.makeText(this, R.string.about_open_url_failed, Toast.LENGTH_SHORT).show()
        }
        return opened
    }

    private fun showDetails(entry: ReleaseEntry) {
        if (isFinishing || isDestroyed) return
        runCatching {
            MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
                .setTitle(entry.versionTag)
                .setMessage(entry.body.ifBlank { getString(R.string.m3t_updates_no_changelog) })
                .setPositiveButton(getString(R.string.m3t_updates_open_release)) { _, _ ->
                    openReleaseUrlWithFeedback(entry.releaseUrl)
                }
                .setNegativeButton(getString(R.string.m3t_updates_close), null)
                .show()
        }.onFailure {
            Toast.makeText(this, R.string.update_open_detail_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleDownload(entry: ReleaseEntry) {
        runCatching {
            if (BuildConfig.GITHUB_TOKEN.isNotBlank()) {
                val submitted = ApkDownloadUtil.enqueueApkDownload(this, entry)
                if (!submitted) {
                    Toast.makeText(this, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
                    openReleaseUrlWithFeedback(entry.releaseUrl)
                }
                return
            }

            if (entry.apkUrl.isNullOrBlank()) {
                Toast.makeText(this, R.string.update_apk_missing_fallback_release, Toast.LENGTH_SHORT).show()
                openReleaseUrlWithFeedback(entry.releaseUrl)
                return
            }

            val options = ReleaseUiUtil.mirroredDownloadOptions(entry.apkUrl)
            if (options.isEmpty()) {
                Toast.makeText(this, R.string.update_apk_missing_fallback_release, Toast.LENGTH_SHORT).show()
                openReleaseUrlWithFeedback(entry.releaseUrl)
                return
            }

            if (options.size == 1) {
                openReleaseUrlWithFeedback(options.first().second)
                return
            }

            val names = options.map { it.first }.toTypedArray()
            MaterialAlertDialogBuilder(this, R.style.BlueGlassAlertDialog)
                .setTitle(getString(R.string.m3t_updates_choose_source))
                .setItems(names) { _, which ->
                    openReleaseUrlWithFeedback(options[which].second)
                }
                .setNegativeButton(getString(R.string.m3t_action_cancel), null)
                .show()
        }.onFailure {
            Toast.makeText(this, R.string.update_download_submit_failed, Toast.LENGTH_SHORT).show()
            openReleaseUrlWithFeedback(entry.releaseUrl)
        }
    }
}
