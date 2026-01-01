# API 文档: `system.d.ts`

本文档详细介绍了 `system.d.ts` 文件中定义的 API，这些 API 封装在 `Tools.System` 命名空间下，提供了与设备系统层面交互的各种功能。

## 概述

`Tools.System` 命名空间是执行底层系统操作的核心，涵盖了从简单的线程休眠到复杂的应用管理、设置读写以及设备信息查询等功能。

---

## `Tools.System` 命名空间详解

### `sleep(milliseconds: string | number): Promise<SleepResultData>`

暂停脚本执行指定的毫秒数。这是控制脚本节奏、等待异步操作（如UI更新）完成的关键函数。

-   **`milliseconds`**: 要暂停的时间，单位为毫秒。

### `getSetting(setting: string, namespace?: string): Promise<SystemSettingData>`

读取一个 Android 系统设置的值。

-   **`setting`**: 要查询的设置项的键名。
-   **`namespace`**: **可选**。设置项所属的命名空间，可以是 `'system'`, `'secure'`, 或 `'global'`。

### `setSetting(setting: string, value: string, namespace?: string): Promise<SystemSettingData>`

修改一个 Android 系统设置的值。

-   **`setting`**: 要修改的设置项的键名。
-   **`value`**: 要设置的新值。
-   **`namespace`**: **可选**。设置项所属的命名空间。

### `getDeviceInfo(): Promise<DeviceInfoResultData>`

获取关于设备的详细信息，包括型号、Android版本、屏幕分辨率、内存、存储空间、电池状态等。

### `startApp(packageName: string, activity?: string): Promise<AppOperationData>`

启动一个应用程序。

-   **`packageName`**: 要启动的应用的包名 (e.g., `'com.bilibili.app.in'`)。
-   **`activity`**: **可选**。要启动的特定 Activity。如果省略，则启动应用的主 Activity。

### `stopApp(packageName: string): Promise<AppOperationData>`

强制停止一个正在运行的应用程序。

-   **`packageName`**: 要停止的应用的包名。

### `listApps(includeSystem?: boolean): Promise<AppListData>`

列出设备上安装的所有应用程序。

-   **`includeSystem`**: **可选** (默认为 `false`)。是否在列表中包含系统应用。

### `getNotifications(limit?: number, includeOngoing?: boolean): Promise<NotificationData>`

获取设备状态栏中的通知。

-   **`limit`**: **可选** (默认为 10)。返回的通知数量上限。
-   **`includeOngoing`**: **可选** (默认为 `false`)。是否包含正在进行的（不可清除的）通知。

### `getLocation(highAccuracy?: boolean, timeout?: number): Promise<LocationData>`

获取设备当前的地理位置信息。

-   **`highAccuracy`**: **可选** (默认为 `false`)。是否请求高精度的位置数据（可能会消耗更多电量）。
-   **`timeout`**: **可选** (默认为 10)。获取位置的超时时间，单位为秒。

### `shell(command: string): Promise<ADBResultData>`

执行一个原生的 shell 命令 (通常需要 root 权限才能执行有意义的操作)。

-   **`command`**: 要执行的 shell 命令字符串。

### `intent(options: object): Promise<IntentResultData>`

发送一个 Android Intent。这是一个非常强大的功能，可以用于启动 Activity、发送广播、启动服务等。

-   **`options`**: 一个配置对象，可以包含 `action`, `uri`, `package`, `component`, `flags`, `extras`, `type` 等字段。

### `terminal(command: string, sessionId?: string, timeoutMs?: number): Promise<TerminalCommandResultData>`

在一个终端会话中执行命令并返回其输出。这对于需要交互式会话或长时间运行的命令非常有用。

-   **`command`**: 要执行的命令。
-   **`sessionId`**: **可选**。用于在同一个会话中执行连续的命令。
-   **`timeoutMs`**: **可选**。命令执行的超时时间。

**示例：启动B站并等待5秒**
```typescript
async function launchBilibili() {
    try {
        console.log("正在尝试启动B站...");
        const startResult = await Tools.System.startApp("com.bilibili.app.in");

        if (startResult.success) {
            console.log("B站启动成功，等待5秒...");
            await Tools.System.sleep(5000);
            console.log("等待结束。");
            complete({ success: true, message: "B站已成功启动并等待了5秒。" });
        } else {
            complete({ success: false, message: `启动B站失败: ${startResult.details}` });
        }
    } catch (error) {
        complete({ success: false, message: `执行时发生错误: ${error.message}` });
    }
}
``` 