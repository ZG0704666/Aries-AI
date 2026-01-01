package com.ai.assistance.operit.ui.features.about.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.updates.UpdateManager
import com.ai.assistance.operit.data.updates.UpdateStatus
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.updates.PatchUpdateInstaller
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.ai.assistance.operit.ui.components.CustomScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.widget.Toast

private const val GITHUB_PROJECT_URL = "https://github.com/AAswordman/Operit"

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val textColor = style.color.toArgb()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                this.textSize = style.fontSize.value
                this.setTextColor(textColor)
                this.movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { textView ->
            textView.text =
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
                } else {
                    @Suppress("DEPRECATION") Html.fromHtml(html)
                }
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchDownloadSourceDialog(
    patchUrl: String,
    metaUrl: String,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val patchMirrors = remember(patchUrl) { GithubReleaseUtil.getMirroredUrls(patchUrl) }
    val metaMirrors = remember(metaUrl) { GithubReleaseUtil.getMirroredUrls(metaUrl) }

    val patchProbeUrls = remember(patchUrl, patchMirrors) {
        buildMap {
            putAll(patchMirrors)
            put("GitHub", patchUrl)
        }
    }
    val metaProbeUrls = remember(metaUrl, metaMirrors) {
        buildMap {
            putAll(metaMirrors)
            put("GitHub", metaUrl)
        }
    }

    var probeResults by remember(patchProbeUrls, metaProbeUrls) {
        mutableStateOf<Map<String, GithubReleaseUtil.ProbeResult>>(emptyMap())
    }

    LaunchedEffect(patchProbeUrls, metaProbeUrls) {
        probeResults = emptyMap()

        val keys = LinkedHashSet<String>().apply {
            addAll(patchProbeUrls.keys)
            addAll(metaProbeUrls.keys)
        }

        coroutineScope {
            keys.forEach { key ->
                launch {
                    val p = patchProbeUrls[key]?.let { url ->
                        withContext(Dispatchers.IO) {
                            GithubReleaseUtil.probeMirrorUrls(mapOf(key to url))[key]
                        }
                    }
                    val m = metaProbeUrls[key]?.let { url ->
                        withContext(Dispatchers.IO) {
                            GithubReleaseUtil.probeMirrorUrls(mapOf(key to url))[key]
                        }
                    }

                    val ok = (p?.ok != false) && (m?.ok != false) && (p != null) && (m != null)
                    val latency = when {
                        p?.latencyMs != null && m?.latencyMs != null -> maxOf(p.latencyMs, m.latencyMs)
                        p?.latencyMs != null -> p.latencyMs
                        m?.latencyMs != null -> m.latencyMs
                        else -> null
                    }

                    val error = when {
                        p == null || m == null -> "probe_failed"
                        p.ok && m.ok -> null
                        !p.ok && !m.ok -> "patch:${p.error ?: "fail"}, meta:${m.error ?: "fail"}"
                        !p.ok -> "patch:${p.error ?: "fail"}"
                        else -> "meta:${m.error ?: "fail"}"
                    }

                    val combined = GithubReleaseUtil.ProbeResult(ok = ok, latencyMs = latency, error = error)
                    probeResults = probeResults.toMutableMap().apply { put(key, combined) }
                }
            }
        }
    }

    val keys = remember(patchUrl, metaUrl, patchMirrors, metaMirrors) {
        val set = LinkedHashSet<String>()
        set.addAll(patchMirrors.keys)
        set.addAll(metaMirrors.keys)
        set.add("GitHub")
        set.toList()
    }

    @Composable
    fun MirrorSourceRow(
        title: String,
        desc: String,
        icon: ImageVector,
        probe: GithubReleaseUtil.ProbeResult?,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (probe == null) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                val text = if (probe.ok) {
                    probe.latencyMs?.let { "${it}ms" } ?: "ok"
                } else {
                    probe.error ?: "fail"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (probe.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 64.dp, max = 140.dp)
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_download_source)) },
        text = {
            Column {
                Text(
                    text = stringResource(id = R.string.patch_update_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn {
                    items(keys) { name ->
                        val probe = probeResults[name]
                        val title =
                            if (name == "GitHub") {
                                stringResource(id = R.string.github_source)
                            } else {
                                stringResource(id = R.string.mirror_download, name)
                            }
                        val desc =
                            if (name == "GitHub") {
                                stringResource(id = R.string.github_source_desc)
                            } else {
                                stringResource(id = R.string.china_mirror_desc)
                            }
                        val icon = if (name == "GitHub") Icons.Default.Language else Icons.Default.Storage
                        MirrorSourceRow(
                            title = title,
                            desc = desc,
                            icon = icon,
                            probe = probe,
                            onClick = { onDownload(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun InfoItem(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp, top = 2.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(modifier = Modifier.padding(top = 4.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitleText: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.16f),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (subtitleContent != null) {
                Box(modifier = Modifier.padding(top = 2.dp)) { subtitleContent() }
            } else if (!subtitleText.isNullOrBlank()) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navigateToUpdateHistory: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val preferences = remember { UserPreferencesManager.getInstance(context) }
    val betaEnabled = preferences.betaPlanEnabled.collectAsState(initial = false).value

    // 获取UpdateManager实例
    val updateManager = remember { UpdateManager.getInstance(context) }

    // 监听更新状态
    var updateStatus by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Initial) }

    // 观察UpdateManager的LiveData
    DisposableEffect(updateManager) {
        val observer = androidx.lifecycle.Observer<UpdateStatus> { newStatus -> updateStatus = newStatus }
        updateManager.updateStatus.observeForever(observer)

        onDispose { updateManager.updateStatus.removeObserver(observer) }
    }

    // 显示更新对话框
    var showUpdateDialog by remember { mutableStateOf(false) }
    // 添加下载源选择对话框状态
    var showDownloadSourceMenu by remember { mutableStateOf(false) }

    // 补丁更新下载源选择
    var showPatchSourceMenu by remember { mutableStateOf(false) }

    // 保存 patch info
    var patchUrl by remember { mutableStateOf("") }
    var metaUrl by remember { mutableStateOf("") }

    // 添加开源许可对话框状态
    var showLicenseDialog by remember { mutableStateOf(false) }

    // 获取应用版本信息
    val appVersion = remember {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "未知"
        }
    }

    // 观察更新状态变化
    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is UpdateStatus.Available, is UpdateStatus.PatchAvailable, is UpdateStatus.UpToDate, is UpdateStatus.Error -> {
                showUpdateDialog = true
            }
            else -> {}
        }
    }

    // 检查更新
    fun checkForUpdates() {
        scope.launch { updateManager.checkForUpdates(appVersion) }
    }

    // 处理下载更新 - 显示下载源选择对话框
    fun handleDownload() {
        when (val status = updateStatus) {
            is UpdateStatus.Available -> {
                if (status.downloadUrl.isNotEmpty() && status.downloadUrl.endsWith(".apk")) {
                    showDownloadSourceMenu = true // 显示下载源选择对话框
                } else {
                    // 如果没有APK下载链接，则直接打开更新页面
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(status.updateUrl)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    showUpdateDialog = false
                }
            }
            is UpdateStatus.PatchAvailable -> {
                patchUrl = status.patchUrl
                metaUrl = status.metaUrl
                showPatchSourceMenu = true
            }
            else -> return
        }
    }

    // 从指定源下载
    fun downloadFromUrl(downloadUrl: String) {
        // 打开浏览器下载
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(downloadUrl)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        showDownloadSourceMenu = false
        showUpdateDialog = false
    }

    fun downloadPatchAndInstallFromMirror(mirrorKey: String) {
        showPatchSourceMenu = false
        showUpdateDialog = false
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.prepare_patch_update), Toast.LENGTH_SHORT).show()
                }
                val apkFile = PatchUpdateInstaller.downloadAndPreparePatchUpdateAuto(context, mirrorKey)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.patch_update_success), Toast.LENGTH_LONG).show()
                    PatchUpdateInstaller.installApk(context, apkFile)
                }
            } catch (e: Exception) {
                AppLogger.e("AboutScreen", "patch update failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.patch_update_failed, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // 显示开源许可对话框
    if (showLicenseDialog) {
        LicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    // 更新对话框
    if (showUpdateDialog) {
        UpdateDialog(
            updateStatus = updateStatus,
            appVersion = appVersion,
            onDismiss = { showUpdateDialog = false },
            onConfirm = {
                if (updateStatus is UpdateStatus.Available || updateStatus is UpdateStatus.PatchAvailable) {
                    handleDownload()
                } else if (updateStatus !is UpdateStatus.Checking) {
                    showUpdateDialog = false
                }
            }
        )
    }

    // 下载源选择对话框
    if (showDownloadSourceMenu) {
        DownloadSourceDialog(
            updateStatus = updateStatus,
            onDismiss = { showDownloadSourceMenu = false },
            onDownload = { downloadFromUrl(it) }
        )
    }

    if (showPatchSourceMenu) {
        PatchDownloadSourceDialog(
            patchUrl = patchUrl,
            metaUrl = metaUrl,
            onDismiss = { showPatchSourceMenu = false },
            onDownload = { key -> downloadPatchAndInstallFromMirror(key) }
        )
    }

    CustomScaffold() { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = stringResource(R.string.app_logo_description),
                                modifier = Modifier.size(80.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = stringResource(id = R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(id = R.string.about_version, appVersion),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            item {
                val updateSubtitle = when (val status = updateStatus) {
                    is UpdateStatus.Available -> context.getString(R.string.new_version, appVersion, status.newVersion)
                    is UpdateStatus.PatchAvailable -> context.getString(R.string.new_version, appVersion, status.newVersion)
                    is UpdateStatus.UpToDate -> context.getString(R.string.already_latest_version, appVersion)
                    is UpdateStatus.Error -> status.message
                    is UpdateStatus.Checking -> context.getString(R.string.checking_updates)
                    else -> null
                }

                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Update,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(id = R.string.check_for_updates),
                        subtitleText = updateSubtitle,
                        trailing = {
                            if (updateStatus is UpdateStatus.Checking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        onClick = {
                            when (updateStatus) {
                                is UpdateStatus.Available,
                                is UpdateStatus.PatchAvailable,
                                is UpdateStatus.UpToDate,
                                is UpdateStatus.Error -> showUpdateDialog = true
                                is UpdateStatus.Checking -> Unit
                                else -> checkForUpdates()
                            }
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.NewReleases,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(id = R.string.beta_plan),
                        subtitleText = stringResource(id = R.string.beta_plan_desc),
                        trailing = {
                            Switch(
                                checked = betaEnabled,
                                onCheckedChange = { enabled ->
                                    scope.launch { preferences.saveBetaPlanEnabled(enabled) }
                                }
                            )
                        }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Language,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(id = R.string.project_url),
                        subtitleText = GITHUB_PROJECT_URL,
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROJECT_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.Star,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(id = R.string.star_on_github),
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_PROJECT_URL)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.History,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(R.string.update_log),
                        onClick = { navigateToUpdateHistory() }
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.Source,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = stringResource(id = R.string.open_source_licenses),
                        onClick = { showLicenseDialog = true }
                    )
                }
            }

            item {
                SettingsGroup {
                    SettingsRow(
                        icon = Icons.Default.Email,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(id = R.string.contact),
                        subtitleText = stringResource(id = R.string.about_contact)
                    )

                    HorizontalDivider(modifier = Modifier.padding(start = 66.dp))

                    SettingsRow(
                        icon = Icons.Default.Person,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = stringResource(id = R.string.developer),
                        subtitleContent = {
                            HtmlText(
                                html = stringResource(id = R.string.about_developer),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    )
                }
            }

            item {
                Text(
                    text = stringResource(id = R.string.about_copyright),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(
    updateStatus: UpdateStatus,
    appVersion: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            val icon = when (updateStatus) {
                is UpdateStatus.Available -> Icons.Default.Update
                is UpdateStatus.PatchAvailable -> Icons.Default.Update
                is UpdateStatus.Checking -> Icons.Default.Download
                is UpdateStatus.UpToDate -> Icons.Default.CheckCircle
                is UpdateStatus.Error -> Icons.Default.Error
                else -> Icons.Default.Update
            }
            Icon(icon, contentDescription = null)
        },
        title = {
            val titleText = when (updateStatus) {
                is UpdateStatus.Available -> stringResource(id = R.string.new_version_found)
                is UpdateStatus.PatchAvailable -> stringResource(id = R.string.new_version_found)
                is UpdateStatus.Checking -> stringResource(id = R.string.checking_updates)
                is UpdateStatus.UpToDate -> stringResource(id = R.string.check_complete)
                is UpdateStatus.Error -> stringResource(id = R.string.check_failed)
                else -> stringResource(id = R.string.update_check)
            }
            Text(text = titleText)
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when (val status = updateStatus) {
                    is UpdateStatus.Available -> {
                        Text(
                            text = "$appVersion -> ${status.newVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        if (status.releaseNotes.isNotEmpty() && !status.newVersion.contains("+")) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                stringResource(id = R.string.update_content),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Text(status.releaseNotes, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is UpdateStatus.PatchAvailable -> {
                        Text(
                            text = "$appVersion -> ${status.newVersion}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    is UpdateStatus.UpToDate -> {
                        Text(stringResource(id = R.string.already_latest_version, appVersion))
                    }
                    is UpdateStatus.Error -> {
                        Text(status.message)
                    }
                    else -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = updateStatus !is UpdateStatus.Checking
            ) {
                Text(
                    when (updateStatus) {
                        is UpdateStatus.Available -> stringResource(id = R.string.download)
                        is UpdateStatus.PatchAvailable -> stringResource(id = R.string.patch_update)
                        else -> stringResource(id = R.string.ok)
                    }
                )
            }
        },
        dismissButton = {
            if (updateStatus !is UpdateStatus.Checking) {
                TextButton(onClick = onDismiss) { Text(stringResource(id = R.string.close)) }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSourceDialog(
    updateStatus: UpdateStatus,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val status = updateStatus as? UpdateStatus.Available
    val mirroredUrls = remember(status) {
        status?.let { GithubReleaseUtil.getMirroredUrls(it.downloadUrl) } ?: emptyMap()
    }

    val probeUrls = remember(status, mirroredUrls) {
        status?.let {
            buildMap {
                putAll(mirroredUrls)
                put("GitHub", it.downloadUrl)
            }
        } ?: emptyMap()
    }

    var probeResults by remember(probeUrls) {
        mutableStateOf<Map<String, GithubReleaseUtil.ProbeResult>>(emptyMap())
    }

    LaunchedEffect(probeUrls) {
        if (probeUrls.isEmpty()) {
            probeResults = emptyMap()
            return@LaunchedEffect
        }
        probeResults = emptyMap()
        coroutineScope {
            probeUrls.forEach { (name, url) ->
                launch {
                    val r = withContext(Dispatchers.IO) {
                        GithubReleaseUtil.probeMirrorUrls(mapOf(name to url))[name]
                    }
                    if (r != null) {
                        probeResults = probeResults.toMutableMap().apply { put(name, r) }
                    }
                }
            }
        }
    }

    @Composable
    fun MirrorSourceRow(
        title: String,
        desc: String?,
        icon: ImageVector,
        probe: GithubReleaseUtil.ProbeResult?,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (desc != null) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            if (probe == null && probeUrls.isNotEmpty()) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else if (probe != null) {
                val text = if (probe.ok) {
                    probe.latencyMs?.let { "${it}ms" } ?: "ok"
                } else {
                    probe.error ?: "fail"
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (probe.ok) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(min = 64.dp, max = 140.dp)
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(id = R.string.select_download_source)) },
        text = {
            Column {
                Text(
                    stringResource(id = R.string.select_download_source_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyColumn {
                    items(mirroredUrls.toList()) { (name, url) ->
                        val probe = probeResults[name]
                        MirrorSourceRow(
                            title = stringResource(id = R.string.mirror_download, name),
                            desc = null,
                            icon = Icons.Default.Storage,
                            probe = probe,
                            onClick = { onDownload(url) }
                        )
                    }
                    if (status != null) {
                        item {
                            val probe = probeResults["GitHub"]
                            MirrorSourceRow(
                                title = stringResource(id = R.string.github_source),
                                desc = stringResource(id = R.string.github_source_desc),
                                icon = Icons.Default.Language,
                                probe = probe,
                                onClick = { onDownload(status.downloadUrl) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}
