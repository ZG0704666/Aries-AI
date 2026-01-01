package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AppListData
import com.ai.assistance.operit.core.tools.AppOperationData
import com.ai.assistance.operit.core.tools.LocationData
import com.ai.assistance.operit.core.tools.NotificationData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.SystemSettingData
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.ComponentName
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import java.io.File
import com.ai.assistance.operit.services.notification.OperitNotificationStore

/** 提供系统级操作的工具类 包括系统设置修改、应用安装和卸载等 这些操作需要用户明确授权 */
open class StandardSystemOperationTools(private val context: Context) {

    companion object {
        private const val TAG = "SystemOperationTools"
    }

    /** 修改系统设置 支持修改各种系统设置，如音量、亮度等 */
    open suspend fun modifySystemSetting(tool: AITool): ToolResult {
        val setting = tool.parameters.find { it.name == "setting" }?.value ?: ""
        val value = tool.parameters.find { it.name == "value" }?.value ?: ""
        val namespace = tool.parameters.find { it.name == "namespace" }?.value ?: "system"

        if (setting.isBlank() || value.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供setting和value参数"
            )
        }

        val validNamespaces = listOf("system", "secure", "global")
        if (!validNamespaces.contains(namespace)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "命名空间必须是以下之一: ${validNamespaces.joinToString(", ")}"
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
            // 自动打开系统设置页面引导用户授权
            try {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "没有修改系统设置的权限。已为您打开设置页面，请授予 WRITE_SETTINGS 权限后重试。"
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开设置页面失败", e)
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "没有修改系统设置的权限，且无法打开设置页面: ${e.message}"
                )
            }
        }

        return try {
            when (namespace) {
                "system" -> Settings.System.putString(context.contentResolver, setting, value)
                "secure" -> Settings.Secure.putString(context.contentResolver, setting, value)
                "global" -> Settings.Global.putString(context.contentResolver, setting, value)
            }
            val resultData = SystemSettingData(namespace = namespace, setting = setting, value = value)
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "修改系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "修改系统设置时出现安全异常: ${e.message}. 这可能需要更高的权限."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "修改系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "修改系统设置时出错: ${e.message}"
            )
        }
    }

    /** 获取系统设置的当前值 */
    open suspend fun getSystemSetting(tool: AITool): ToolResult {
        val setting = tool.parameters.find { it.name == "setting" }?.value ?: ""
        val namespace = tool.parameters.find { it.name == "namespace" }?.value ?: "system"

        if (setting.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供setting参数"
            )
        }

        val validNamespaces = listOf("system", "secure", "global")
        if (!validNamespaces.contains(namespace)) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "命名空间必须是以下之一: ${validNamespaces.joinToString(", ")}"
            )
        }

        return try {
            val value = when (namespace) {
                "system" -> Settings.System.getString(context.contentResolver, setting)
                "secure" -> Settings.Secure.getString(context.contentResolver, setting)
                "global" -> Settings.Global.getString(context.contentResolver, setting)
                else -> null
            }

            if (value != null) {
                val resultData = SystemSettingData(namespace = namespace, setting = setting, value = value)
                ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取设置失败: setting '$setting' 在 namespace '$namespace' 中未找到."
                )
            }
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "获取系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取系统设置时出现安全异常: ${e.message}. 这可能需要更高的权限."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取系统设置时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取系统设置时出错: ${e.message}"
            )
        }
    }

    /** 安装应用程序 需要APK文件的路径 */
    open suspend fun installApp(tool: AITool): ToolResult {
        val apkPath = tool.parameters.find { it.name == "apk_path" }?.value ?: ""

        if (apkPath.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供apk_path参数"
            )
        }

        val file = File(apkPath)
        if (!file.exists()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "APK文件不存在: $apkPath"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_VIEW)
            val uri = Uri.fromFile(file)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)

            val resultData = AppOperationData(
                operationType = "install_request",
                packageName = apkPath,
                success = true,
                details = "已发送安装请求，需要用户确认。"
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求安装应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "请求安装应用时出错: ${e.message}"
            )
        }
    }

    /** 卸载应用程序 需要提供包名 */
    open suspend fun uninstallApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供package_name参数"
            )
        }

        try {
            context.packageManager.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "应用未安装: $packageName"
            )
        }

        return try {
            val intent = Intent(Intent.ACTION_DELETE)
            intent.data = Uri.parse("package:$packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            val resultData = AppOperationData(
                operationType = "uninstall_request",
                packageName = packageName,
                success = true,
                details = "已发送卸载请求，需要用户确认。"
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "请求卸载应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "请求卸载应用时出错: ${e.message}"
            )
        }
    }

    /** 获取已安装的应用列表 */
    suspend fun listInstalledApps(tool: AITool): ToolResult {
        val includeSystemApps =
                tool.parameters.find { it.name == "include_system_apps" }?.value?.toBoolean()
                        ?: false
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appDetails = mutableListOf<String>()

            apps.forEach { appInfo ->
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (includeSystemApps || !isSystemApp) {
                    val packageName = appInfo.packageName
                    val appName =
                            try {
                                appInfo.loadLabel(pm).toString()
                            } catch (e: Exception) {
                                AppLogger.w(
                                        TAG,
                                        "Failed to load application label for $packageName",
                                        e
                                )
                                packageName
                            }
                    appDetails.add("$appName ($packageName)")
                }
            }

            val sortedAppDetails = appDetails.sorted()
            val resultData = AppListData(
                includesSystemApps = includeSystemApps, 
                packages = sortedAppDetails
            )
            
            ToolResult(toolName = tool.name, success = true, result = resultData)
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取已安装应用列表时出错", e)
            ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取应用列表失败: ${e.message}"
            )
        }
    }

    /** 启动应用程序 如果提供了activity参数，将启动指定的活动 否则使用默认启动器启动应用 */
    open suspend fun startApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""
        val activityName = tool.parameters.find { it.name == "activity" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供package_name参数"
            )
        }

        return try {
            val intent: Intent? = if (activityName.isBlank()) {
                context.packageManager.getLaunchIntentForPackage(packageName)
            } else {
                Intent(Intent.ACTION_MAIN).also {
                    it.addCategory(Intent.CATEGORY_LAUNCHER)
                    it.component = ComponentName(packageName, activityName)
                }
            }

            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                val details = if (activityName.isNotBlank()) "活动: $activityName" else ""
                val resultData = AppOperationData(
                    operationType = "start",
                    packageName = packageName,
                    success = true,
                    details = details
                )
                ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "启动应用失败: 无法找到应用的启动Intent. 请检查包名或Activity名称."
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "启动应用时出错: ${e.message}"
            )
        }
    }

    /** 停止应用程序 */
    open suspend fun stopApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value ?: ""

        if (packageName.isBlank()) {
            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "必须提供package_name参数"
            )
        }

        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.killBackgroundProcesses(packageName)
            val resultData = AppOperationData(
                operationType = "stop",
                packageName = packageName,
                success = true,
                details = "已请求停止应用后台进程。"
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "停止应用时出现安全异常", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "停止应用失败: ${e.message}. 需要 KILL_BACKGROUND_PROCESSES 权限."
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "停止应用时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "停止应用时出错: ${e.message}"
            )
        }
    }

    /** 读取设备通知内容 获取当前设备上的通知信息 */
    open suspend fun getNotifications(tool: AITool): ToolResult {
        val limit = tool.parameters.find { it.name == "limit" }?.value?.toIntOrNull() ?: 10
        val includeOngoing =
            tool.parameters.find { it.name == "include_ongoing" }?.value?.toBoolean() ?: false

        val enabledListeners =
            Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?: ""
        val myPackageName = context.packageName

        val hasNotificationAccess = enabledListeners
            .split(":")
            .asSequence()
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it.packageName == myPackageName }

        if (!hasNotificationAccess) {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "打开通知使用权设置页面失败", e)
            }

            return ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "无法读取通知。本应用需要被授权为通知监听服务 (Notification Listener Service)。"
            )
        }

        return try {
            val notifications = OperitNotificationStore.snapshot(
                limit = limit,
                includeOngoing = includeOngoing
            )
            val resultData = NotificationData(
                notifications = notifications,
                timestamp = System.currentTimeMillis()
            )
            ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取通知时出错", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "获取通知时出错: ${e.message}"
            )
        }
    }

    /** 获取设备位置信息 通过系统API获取当前设备位置 */
    suspend fun getDeviceLocation(tool: AITool): ToolResult {
        val timeout = tool.parameters.find { it.name == "timeout" }?.value?.toIntOrNull() ?: 10
        val highAccuracy =
                tool.parameters.find { it.name == "high_accuracy" }?.value?.toBoolean() ?: false
        val includeAddress =
                tool.parameters.find { it.name == "include_address" }?.value?.toBoolean() ?: true

        return try {
            // 检查位置权限
            val hasFineLocationPermission =
                    context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED

            val hasCoarseLocationPermission =
                    context.checkSelfPermission(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            // 如果没有任何位置权限，返回错误
            if (!hasFineLocationPermission && !hasCoarseLocationPermission) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "未授予位置权限，请在应用设置中开启位置权限"
                )
            }

            // 根据精度要求和权限情况决定使用哪种精度
            val actualHighAccuracy = highAccuracy && hasFineLocationPermission

            // 使用Dispatchers.Main确保在主线程上执行位置操作
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            val locationResult =
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        kotlinx.coroutines.suspendCancellableCoroutine<Location?> { continuation ->
                            val locationManager =
                                    context.getSystemService(Context.LOCATION_SERVICE) as
                                            LocationManager

                            // 选择合适的位置提供者
                            val provider =
                                    when {
                                        actualHighAccuracy &&
                                                locationManager.isProviderEnabled(
                                                        LocationManager.GPS_PROVIDER
                                                ) -> LocationManager.GPS_PROVIDER
                                        locationManager.isProviderEnabled(
                                                LocationManager.NETWORK_PROVIDER
                                        ) -> LocationManager.NETWORK_PROVIDER
                                        locationManager.isProviderEnabled(
                                                LocationManager.PASSIVE_PROVIDER
                                        ) -> LocationManager.PASSIVE_PROVIDER
                                        else -> null
                                    }

                            if (provider == null) {
                                continuation.resume(null) { AppLogger.e(TAG, "位置请求取消", it) }
                                return@suspendCancellableCoroutine
                            }

                            // 尝试获取最后已知位置
                            val lastKnownLocation =
                                    try {
                                        if (actualHighAccuracy && hasFineLocationPermission) {
                                            locationManager.getLastKnownLocation(
                                                    LocationManager.GPS_PROVIDER
                                            )
                                                    ?: locationManager.getLastKnownLocation(
                                                            LocationManager.NETWORK_PROVIDER
                                                    )
                                        } else if (hasCoarseLocationPermission) {
                                            locationManager.getLastKnownLocation(
                                                    LocationManager.NETWORK_PROVIDER
                                            )
                                                    ?: locationManager.getLastKnownLocation(
                                                            LocationManager.PASSIVE_PROVIDER
                                                    )
                                        } else {
                                            null
                                        }
                                    } catch (e: SecurityException) {
                                        AppLogger.e(TAG, "获取最后已知位置失败", e)
                                        null
                                    }

                            // 如果有最后已知位置且足够新（10分钟内），直接返回
                            if (lastKnownLocation != null &&
                                            System.currentTimeMillis() - lastKnownLocation.time <
                                                    10 * 60 * 1000
                            ) {
                                continuation.resume(lastKnownLocation) { AppLogger.e(TAG, "位置请求取消", it) }
                                return@suspendCancellableCoroutine
                            }

                            // 否则请求位置更新
                            val locationListener =
                                    object : android.location.LocationListener {
                                        override fun onLocationChanged(location: Location) {
                                            locationManager.removeUpdates(this)
                                            continuation.resume(location) {
                                                AppLogger.e(TAG, "位置请求取消", it)
                                            }
                                        }

                                        override fun onProviderDisabled(provider: String) {
                                            // 如果提供者被禁用，尝试使用最后已知位置
                                            if (!continuation.isCompleted) {
                                                if (lastKnownLocation != null) {
                                                    continuation.resume(lastKnownLocation) {
                                                        AppLogger.e(TAG, "位置请求取消", it)
                                                    }
                                                } else {
                                                    continuation.resume(null) {
                                                        AppLogger.e(TAG, "位置请求取消", it)
                                                    }
                                                }
                                            }
                                        }

                                        override fun onProviderEnabled(provider: String) {
                                            // 不需要处理
                                        }

                                        @Deprecated("Deprecated in Java")
                                        override fun onStatusChanged(
                                                provider: String,
                                                status: Int,
                                                extras: android.os.Bundle
                                        ) {
                                            // 不需要处理
                                        }
                                    }

                            try {
                                // 设置位置请求参数
                                locationManager.requestLocationUpdates(
                                        provider,
                                        0, // 最小时间间隔
                                        0f, // 最小距离变化
                                        locationListener
                                )

                                // 设置超时
                                kotlinx.coroutines.GlobalScope.launch {
                                    delay(timeout * 1000L)
                                    // 在主线程上移除更新和恢复协程
                                    kotlinx.coroutines.withContext(
                                            kotlinx.coroutines.Dispatchers.Main
                                    ) {
                                        if (!continuation.isCompleted) {
                                            locationManager.removeUpdates(locationListener)
                                            // 如果超时，尝试使用最后已知位置
                                            continuation.resume(lastKnownLocation) {
                                                AppLogger.e(TAG, "位置请求取消", it)
                                            }
                                        }
                                    }
                                }

                                // 如果协程被取消，移除位置更新
                                continuation.invokeOnCancellation {
                                    try {
                                        // 确保在主线程上移除位置更新
                                        kotlinx.coroutines.runBlocking(
                                                kotlinx.coroutines.Dispatchers.Main
                                        ) { locationManager.removeUpdates(locationListener) }
                                    } catch (e: Exception) {
                                        AppLogger.e(TAG, "移除位置更新失败", e)
                                    }
                                }
                            } catch (e: SecurityException) {
                                continuation.resume(lastKnownLocation) { AppLogger.e(TAG, "位置请求取消", it) }
                                AppLogger.e(TAG, "请求位置更新失败", e)
                            }
                        }
                    }

            // 处理位置结果
            if (locationResult == null) {
                return ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "无法获取位置信息，请确保已启用位置服务"
                )
            }

            val resultData =
                    if (includeAddress) {
                        // 获取地址信息
                        val addressInfo =
                                getAddressFromLocation(
                                        locationResult.latitude,
                                        locationResult.longitude
                                )

                        LocationData(
                                latitude = locationResult.latitude,
                                longitude = locationResult.longitude,
                                accuracy = locationResult.accuracy,
                                provider = locationResult.provider ?: "unknown",
                                timestamp = locationResult.time,
                                rawData = locationResult.toString(),
                                city = addressInfo.city,
                                address = addressInfo.address,
                                country = addressInfo.country,
                                province = addressInfo.province
                        )
                    } else {
                        LocationData(
                                latitude = locationResult.latitude,
                                longitude = locationResult.longitude,
                                accuracy = locationResult.accuracy,
                                provider = locationResult.provider ?: "unknown",
                                timestamp = locationResult.time,
                                rawData = locationResult.toString()
                        )
                    }

            return ToolResult(toolName = tool.name, success = true, result = resultData, error = "")
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取位置信息时出错", e)
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "获取位置信息时出错: ${e.message}"
            )
        }
    }

    /**
     * 从经纬度获取地址信息
     * @param latitude 纬度
     * @param longitude 经度
     * @return 包含地址信息的数据类
     */
    private fun getAddressFromLocation(latitude: Double, longitude: Double): AddressInfo {
        try {
            val geocoder = Geocoder(context, Locale.getDefault())

            // 尝试获取地址
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (addresses != null && addresses.isNotEmpty()) {
                val address = addresses[0]

                return AddressInfo(
                        address = address.getAddressLine(0) ?: "",
                        city = address.locality ?: address.subAdminArea ?: "",
                        province = address.adminArea ?: "",
                        country = address.countryName ?: "",
                        postalCode = address.postalCode ?: ""
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取地址信息时出错", e)
        }

        // 如果无法获取地址信息，返回空对象
        return AddressInfo("", "", "", "", "")
    }

    /** 地址信息数据类 */
    data class AddressInfo(
            val address: String, // 完整地址
            val city: String, // 城市
            val province: String, // 省/州
            val country: String, // 国家
            val postalCode: String // 邮政编码
    )
}
