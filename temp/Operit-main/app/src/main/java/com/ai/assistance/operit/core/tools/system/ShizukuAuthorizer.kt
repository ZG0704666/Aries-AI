package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.util.AppLogger
import rikka.shizuku.Shizuku

/** Shizuku授权工具类 提供Shizuku权限检查和管理功能 */
class ShizukuAuthorizer {
    companion object {
        private const val TAG = "ShizukuAuthorizer"
        private val mainHandler = Handler(Looper.getMainLooper())

        // 注册Shizuku权限请求监听器
        private var binderReceivedListenerRegistered = false
        private var permissionRequestListenerRegistered = false

        // 服务状态
        private var isServiceAvailable = false
        
        // 错误消息缓存
        private var lastServiceErrorMessage = ""
        private var lastPermissionErrorMessage = ""

        // 状态变更回调
        private val stateChangeListeners = mutableListOf<() -> Unit>()

        /**
         * 添加状态变更监听器
         * @param listener 监听器回调
         */
        fun addStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) {
                if (!stateChangeListeners.contains(listener)) {
                    stateChangeListeners.add(listener)
                }
            }
        }

        /**
         * 移除状态变更监听器
         * @param listener 要移除的监听器
         */
        fun removeStateChangeListener(listener: () -> Unit) {
            synchronized(stateChangeListeners) { stateChangeListeners.remove(listener) }
        }

        /** 触发状态变更通知 */
        private fun notifyStateChanged() {
            // 确保在主线程中执行UI相关回调
            mainHandler.post {
                synchronized(stateChangeListeners) {
                    AppLogger.d(
                            TAG,
                            "Notifying ${stateChangeListeners.size} listeners about state change"
                    )
                    stateChangeListeners.forEach { it.invoke() }
                }
            }
        }

        /**
         * 检查Shizuku是否已安装
         * @param context Android上下文
         * @return 是否已安装Shizuku
         */
        fun isShizukuInstalled(context: Context): Boolean {
            return try {
                val packageInfo = context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
                val versionName = packageInfo.versionName
                AppLogger.i(TAG, "检测到已安装Shizuku，版本: $versionName")
                true
            } catch (e: PackageManager.NameNotFoundException) {
                AppLogger.i(TAG, "未检测到已安装的Shizuku")
                false
            } catch (e: Exception) {
                AppLogger.e(TAG, "检查Shizuku是否安装时出错", e)
                false
            }
        }

        /**
         * 获取最后一次服务检查的错误信息
         * @return 错误信息
         */
        fun getServiceErrorMessage(): String {
            return lastServiceErrorMessage
        }
        
        /**
         * 获取最后一次权限检查的错误信息
         * @return 错误信息
         */
        fun getPermissionErrorMessage(): String {
            return lastPermissionErrorMessage
        }

        /**
         * 检查Shizuku服务是否正在运行
         * @return 服务是否运行
         */
        fun isShizukuServiceRunning(): Boolean {
            try {
                // 首先检查本地缓存的状态 - 如果已经知道服务可用，直接返回
                if (isServiceAvailable) {
                    lastServiceErrorMessage = ""
                    return true
                }

                // 方法1: 使用pingBinder - 这是最可靠的检测方法
                try {
                    if (Shizuku.pingBinder()) {
                        AppLogger.d(TAG, "Shizuku pingBinder succeeded")
                        isServiceAvailable = true
                        lastServiceErrorMessage = ""
                        return true
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Shizuku pingBinder check failed", e)
                    lastServiceErrorMessage = "Shizuku ping failed: ${e.message}"
                    return false
                }

                // 方法2: 直接获取并检查binder存活状态
                try {
                    val binder = Shizuku.getBinder()
                    if (binder != null && binder.isBinderAlive) {
                        AppLogger.d(TAG, "Shizuku binder is alive")
                        isServiceAvailable = true
                        lastServiceErrorMessage = ""
                        return true
                    } else if (binder == null) {
                        lastServiceErrorMessage = "Shizuku binder is null"
                        return false
                    } else {
                        lastServiceErrorMessage = "Shizuku binder is not alive"
                        return false
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Binder check failed", e)
                    lastServiceErrorMessage = "Failed to check binder: ${e.message}"
                    return false
                }

                // 方法3: 尝试获取Shizuku UID (如果可以获取有效UID，说明服务在运行)
                try {
                    val uid = Shizuku.getUid()
                    if (uid > 0) {
                        isServiceAvailable = true
                        lastServiceErrorMessage = ""
                        return true
                    }
                    lastServiceErrorMessage = "Invalid Shizuku UID: $uid"
                    return false
                } catch (e: Exception) {
                    AppLogger.e(TAG, "UID check failed", e)
                    lastServiceErrorMessage = "Failed to get UID: ${e.message}"
                    return false
                }
            } catch (e: Throwable) {
                AppLogger.e(TAG, "Critical error checking Shizuku service", e)
                isServiceAvailable = false
                lastServiceErrorMessage = "Critical error: ${e.message}"
                return false
            }
        }

        /**
         * 检查应用是否有Shizuku权限
         * @return 是否有权限
         */
        fun hasShizukuPermission(): Boolean {
            try {
                val serviceRunning = isShizukuServiceRunning()
                if (!serviceRunning) {
                    lastPermissionErrorMessage = "Shizuku service not running: $lastServiceErrorMessage"
                    return false
                }

                // 适用于Shizuku 13.x版本的权限检查
                val result = Shizuku.checkSelfPermission()
                val granted = result == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    lastPermissionErrorMessage = ""
                    return true
                } else {
                    lastPermissionErrorMessage = "Shizuku permission not granted (code: $result)"
                    return false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error checking Shizuku permission", e)
                lastPermissionErrorMessage = "Error checking permission: ${e.message}"
                return false
            }
        }

        /**
         * 请求Shizuku权限
         * @param onResult 权限请求结果回调，仅返回是否授予权限
         */
        fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
            val serviceRunning = isShizukuServiceRunning()
            if (!serviceRunning) {
                AppLogger.e(TAG, "Cannot request permission: $lastServiceErrorMessage")
                onResult(false)
                return
            }

            val hasPermission = hasShizukuPermission()
            if (hasPermission) {
                AppLogger.d(TAG, "Permission already granted")
                onResult(true)
                notifyStateChanged()
                return
            }

            AppLogger.d(TAG, "Requesting Shizuku permission")

            // 移除之前的监听器避免重复
            try {
                if (permissionRequestListenerRegistered) {
                    Shizuku.removeRequestPermissionResultListener { _, _ -> }
                    permissionRequestListenerRegistered = false
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error removing existing permission listener", e)
            }

            try {
                val requestCode = 100

                AppLogger.d(TAG, "Setting up permission result listener")

                Shizuku.addRequestPermissionResultListener { code, grantResult ->
                    AppLogger.d(TAG, "Permission result received: code=$code, result=$grantResult")
                    if (code == requestCode) {
                        val granted = grantResult == PackageManager.PERMISSION_GRANTED
                        AppLogger.d(TAG, "Shizuku permission request result: $granted")
                        onResult(granted)
                        if (granted) {
                            // 权限授予时触发状态变更通知
                            notifyStateChanged()
                        }

                        // 权限请求完成后移除监听器
                        try {
                            Shizuku.removeRequestPermissionResultListener { _, _ -> }
                            permissionRequestListenerRegistered = false
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error removing permission listener", e)
                        }
                    }
                }
                permissionRequestListenerRegistered = true

                // 请求权限
                AppLogger.d(TAG, "Calling Shizuku.requestPermission($requestCode)")
                Shizuku.requestPermission(requestCode)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error requesting Shizuku permission", e)
                onResult(false)
            }
        }

        /** 初始化Shizuku绑定 */
        fun initialize() {
            AppLogger.d(TAG, "Initializing Shizuku")

            // 重置服务状态
            isServiceAvailable = false
            lastServiceErrorMessage = ""
            lastPermissionErrorMessage = ""

            // 移除之前的监听器避免重复
            if (binderReceivedListenerRegistered) {
                try {
                    Shizuku.removeBinderReceivedListener {}
                    Shizuku.removeBinderDeadListener {}
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Error removing binder listeners", e)
                }
                binderReceivedListenerRegistered = false
            }

            try {
                // 设置绑定接收监听器
                Shizuku.addBinderReceivedListener {
                    AppLogger.d(TAG, "Shizuku binder received")
                    isServiceAvailable = true
                    notifyStateChanged()

                    // 当收到binder时主动检查权限状态
                    mainHandler.post {
                        try {
                            val hasPermission = hasShizukuPermission()
                            AppLogger.d(TAG, "Checking permission after binder received: $hasPermission")
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error checking permission after binder received", e)
                        }
                    }
                }

                // 设置绑定断开监听器
                Shizuku.addBinderDeadListener {
                    AppLogger.d(TAG, "Shizuku binder dead")
                    isServiceAvailable = false
                    notifyStateChanged()
                }

                binderReceivedListenerRegistered = true

                // 立即检查服务是否已经在运行
                val isRunning = isShizukuServiceRunning()
                AppLogger.d(TAG, "Initial Shizuku service status check: $isRunning")
                if (isRunning) {
                    // 如果服务正在运行，检查权限
                    mainHandler.post {
                        try {
                            val hasPermission = hasShizukuPermission()
                            AppLogger.d(TAG, "Initial permission check: $hasPermission")
                            notifyStateChanged()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Error during initial permission check", e)
                        }
                    }
                } else {
                    // 如果服务未运行，500毫秒后再次检查以防初始化延迟
                    mainHandler.postDelayed(
                            {
                                val retryCheck = isShizukuServiceRunning()
                                AppLogger.d(TAG, "Delayed service status check: $retryCheck")
                                if (retryCheck) {
                                    notifyStateChanged()
                                }
                            },
                            500
                    )
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error initializing Shizuku", e)
            }
        }

        /**
         * 获取Shizuku启动说明
         * @return Shizuku启动指南
         */
        fun getShizukuStartupInstructions(): String {
            return "要启动Shizuku服务：\n" +
                    "1. 确保已安装Shizuku应用\n" +
                    "2. 使用ADB命令启动Shizuku:\n" +
                    "   adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh\n" +
                    "或者\n\n" +
                    "2. 打开Shizuku应用并按照屏幕上的说明操作"
        }
    }
}
