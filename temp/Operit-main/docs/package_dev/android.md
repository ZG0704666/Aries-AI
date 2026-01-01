# API 文档: `android.d.ts`

本文档详细介绍了 `android.d.ts` 文件中定义的与 Android 系统底层交互的 API。这些工具提供了对设备包管理、系统设置、硬件控制以及应用间通信（Intents）的强大能力。

## 概述

该模块通过一个全局的 `Android` 类实例提供所有功能，同时也暴露了多个独立的类（如 `PackageManager`, `DeviceController` 等），以便于直接使用。

-   **`Android`**: 所有 Android 相关功能的统一入口。
-   **`PackageManager`**: 管理应用的安装、卸载和信息查询。
-   **`DeviceController`**: 控制设备硬件，如截屏、音量、Wi-Fi 等。
-   **`SystemManager`**: 管理系统属性和设置。
-   **`Intent`**: 强大的应用间通信机制，用于启动 Activity、发送广播等。
-   **`ContentProvider`**: 用于访问结构化数据集。

---

## 核心类和枚举

### `Android` 类

这是与 Android 系统交互的主要入口点。

**属性:**

-   `packageManager: PackageManager`: 用于应用管理的 `PackageManager` 实例。
-   `systemManager: SystemManager`: 用于系统管理的 `SystemManager` 实例。
-   `deviceController: DeviceController`: 用于设备控制的 `DeviceController` 实例。

**方法:**

-   `createIntent(action?: string): Intent`: 创建一个新的 `Intent` 对象。
-   `createContentProvider(uri: string): ContentProvider`: 创建一个新的 `ContentProvider` 客户端。

---

### `PackageManager` 类

提供了与 Android 包管理器交互的方法，用于管理设备上的应用程序。

**方法:**

-   `install(apkPath: string, replaceExisting?: boolean): Promise<string>`: 安装一个 APK 应用。
-   `uninstall(packageName: string, keepData?: boolean): Promise<string>`: 卸载一个应用。
-   `getInfo(packageName: string): Promise<object>`: 获取指定应用包的详细信息，包括版本、权限、Activity 等。
-   `getList(includeSystem?: boolean): Promise<string[]>`: 获取已安装应用的包名列表。
-   `clearData(packageName: string): Promise<string>`: 清除一个应用的用户数据和缓存。
-   `isInstalled(packageName: string): Promise<boolean>`: 检查一个应用是否已安装。

---

### `DeviceController` 类

提供对设备硬件和状态的控制。

**方法:**

-   `takeScreenshot(outputPath: string): Promise<string>`: 截取当前屏幕并保存到指定路径。
-   `recordScreen(...)`: 录制屏幕。
-   `setBrightness(brightness: number): Promise<string>`: 设置屏幕亮度 (0-255)。
-   `setVolume(stream: 'music' | 'call' | ..., volume: number): Promise<string>`: 设置指定音频流的音量。
-   `setAirplaneMode(enable: boolean): Promise<string>`: 开启或关闭飞行模式。
-   `setWiFi(enable: boolean): Promise<string>`: 开启或关闭 Wi-Fi。
-   `setBluetooth(enable: boolean): Promise<string>`: 开启或关闭蓝牙。
-   `lock(): Promise<string>`: 锁定设备屏幕。
-   `unlock(): Promise<string>`: 解锁设备屏幕（仅在无安全锁时有效）。
-   `reboot(mode?: string): Promise<string>`: 重启设备（可指定进入 `recovery` 或 `bootloader` 模式）。

---

### `SystemManager` 类

用于读取和修改 Android 系统级的属性和设置。

**方法:**

-   `getProperty(prop: string): Promise<string>`: 获取一个系统属性的值。
-   `setProperty(prop: string, value: string): Promise<string>`: 设置一个系统属性的值。
-   `getAllProperties(): Promise<object>`: 获取所有系统属性。
-   `getSetting(namespace: 'system' | 'secure' | 'global', key: string): Promise<string>`: 从指定的命名空间获取一个系统设置。
-   `setSetting(namespace: 'system' | 'secure' | 'global', key: string, value: string): Promise<string>`: 写入一个系统设置。
-   `listSettings(namespace: 'system' | 'secure' | 'global'): Promise<object>`: 列出指定命名空间下的所有设置。
-   `getScreenInfo(): Promise<object>`: 获取屏幕信息，包括分辨率和密度。

---

### `Intent` 类

封装了 Android 的 `Intent` 对象，是执行应用间通信的核心。你可以用它来启动应用的某个页面（Activity）、发送系统广播或启动一个后台服务。

**核心方法:**

-   `setAction(action: string | IntentAction): Intent`: 设置 Intent 的动作，例如 `IntentAction.ACTION_VIEW`。
-   `setPackage(packageName: string): Intent`: 指定接收此 Intent 的应用包名。
-   `setComponent(packageName: string, component: string): Intent`: 精确指定处理此 Intent 的组件（如 `com.android.settings/.Settings`）。
-   `setData(uri: string): Intent`: 设置 Intent 关联的数据 URI，例如一个网址或文件路径。
-   `addCategory(category: string | IntentCategory): Intent`: 添加一个类别，如 `IntentCategory.CATEGORY_LAUNCHER`。
-   `addFlag(flag: IntentFlag | string): Intent`: 添加标志位来改变 Intent 的行为，例如 `IntentFlag.ACTIVITY_NEW_TASK`。
-   `putExtra(key: string, value: any): Intent`: 添加附加数据。
-   `start(): Promise<any>`: 以 Activity 方式启动此 Intent。
-   `sendBroadcast(): Promise<any>`: 发送广播。
-   `startService(): Promise<any>`: 启动一个服务。

**相关枚举:**

-   `IntentAction`: 预定义的标准 Intent 动作字符串常量（如 `ACTION_VIEW`, `ACTION_SETTINGS`）。
-   `IntentCategory`: 预定义的标准 Intent 类别字符串常量（如 `CATEGORY_LAUNCHER`, `CATEGORY_HOME`）。
-   `IntentFlag`: 用于 `addFlag` 的标志位枚举。

---

### `ContentProvider` 类

提供了一个与 Android `ContentProvider` 交互的客户端，允许你查询、插入、更新和删除结构化数据。

**方法:**

-   `constructor(uri: string)`: 使用目标 `ContentProvider` 的 URI 创建一个实例。
-   `query(...)`: 查询数据。
-   `insert(values: object): Promise<string>`: 插入新数据。
-   `update(values: object, ...)`: 更新现有数据。
-   `delete(...)`: 删除数据。

---

### `AdbExecutor` 基类

这是一个内部基类，为所有其他类提供了执行底层 `adb` 和 `adb shell` 命令的能力。通常你不需要直接使用它。

**方法:**

-   `executeAdb(command: string, ...)`: 执行原始的 `adb` 命令。
-   `executeShell(command: string, ...)`: 执行 `adb shell` 命令。 