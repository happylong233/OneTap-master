# OneTap

给长辈用的 Android 简化桌面。家属先配置家人和常用软件，长辈主要只看到两个入口：`找家人`、`常用软件`。

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="OneTap app icon">
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/platform-Android-3DDC84?logo=android&logoColor=white">
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white">
  <img alt="Jetpack Compose" src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-24-blue">
  <img alt="targetSdk" src="https://img.shields.io/badge/targetSdk-35-blue">
  <img alt="License" src="https://img.shields.io/badge/license-MIT-green">
</p>

## 这是什么

OneTap 目前实现的是一个老人端桌面：

- `找家人`：显示家人照片，点击后选择微信视频、微信语音或直接打电话。
- `常用软件`：显示家属挑好的应用，最多 6 个，点击直接打开。
- 可设为系统默认桌面，按主页键会回到 OneTap。
- 提供长按当点击、默认最大音量、整点语音报时等辅助设置。

它不是完整通讯录，也不是通用 Launcher。当前目标很窄：减少长辈在手机里找人、找软件、找按钮的步骤。

## 快速开始

Windows PowerShell：

```powershell
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

macOS / Linux：

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

也可以直接用 Android Studio 打开项目，选择 `app` 运行配置后安装到设备。

## 支持平台

| 项目 | 当前值 |
| --- | --- |
| 平台 | Android |
| `applicationId` | `tech.huangsh.onetap` |
| `minSdk` | 24 |
| `targetSdk` | 35 |
| `compileSdk` | 35 |
| 语言 | Kotlin |
| UI | Jetpack Compose / Material 3 |
| 架构 | MVVM + Repository |
| 本地存储 | Room + DataStore Preferences |

## 常用命令

| 目的 | Windows | macOS / Linux |
| --- | --- | --- |
| 编译 Kotlin | `.\gradlew.bat :app:compileDebugKotlin` | `./gradlew :app:compileDebugKotlin` |
| 生成 Debug APK | `.\gradlew.bat :app:assembleDebug` | `./gradlew :app:assembleDebug` |
| 安装 Debug 版本 | `.\gradlew.bat :app:installDebug` | `./gradlew :app:installDebug` |
| 完整构建 | `.\gradlew.bat build` | `./gradlew build` |

## 配置和权限

| 功能 | 依赖/权限 | 说明 |
| --- | --- | --- |
| 直接拨号 | `CALL_PHONE` | 老人端点击电话会直接拨出，首次使用需要授权。 |
| 微信语音/视频辅助 | 无障碍服务 | 依赖微信界面，不是微信官方 API。微信版本变化可能影响成功率。 |
| 常用软件列表 | `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS` | 用来扫描本机可启动应用。部分系统可能需要额外手动授权。 |
| 整点语音报时 | `SCHEDULE_EXACT_ALARM`、`FOREGROUND_SERVICE` | 用 `AlarmManager` 调度，到点后启动前台服务并用 TTS 播报。 |
| 开机后恢复报时 | `RECEIVE_BOOT_COMPLETED` | 设备重启后重新调度整点报时。 |
| 默认最大音量 | `MODIFY_AUDIO_SETTINGS` | 会调整通话、媒体、铃声、闹钟、通知、系统音量。 |
| 联系人头像 | `READ_EXTERNAL_STORAGE`、`FileProvider` | 用于选择和保存联系人头像。 |

## 使用边界

这些地方需要提前知道：

- 仓库层有读取系统通讯录的方法，但当前界面没有完整接入“从系统通讯录导入”。
- 微信辅助通过无障碍自动化完成，不能保证适配所有微信版本和所有系统。
- 默认最大音量功能会修改多个音量通道，适合老人机使用，但普通手机上可能显得激进。
- 整点报时受系统闹钟、电池优化、前台服务策略影响，真机行为需要按目标设备验证。
- `oneTapPhoneEnabled`、`floatingBallEnabled` 等设置字段还没有完整 UI 功能。

## 工作原理

- `MainActivity` 作为桌面入口，加载 `ElderHomeShell`。
- `ElderHomeShell` 在 `找家人` 和 `常用软件` 两页之间切换。
- 联系人和常用软件保存在 Room 数据库里。
- 字体、主题、报时、音量等设置保存在 DataStore Preferences。
- 微信通话辅助由 `WeChatAccessibilityService` 和 `WeChatAutomationController` 处理。
- 整点报时由 `TimeAnnouncementScheduler` 注册闹钟，`TimeAnnouncementReceiver` 接收触发，`TimeAnnouncementService` 用 TTS 播报。

## FAQ

### 为什么微信辅助要开无障碍？

因为这里没有接微信官方通话 API。OneTap 只能通过无障碍服务辅助打开微信、搜索联系人并点击语音/视频通话入口。

### 为什么电话要授权？

老人端的电话按钮走的是直接拨号，不是只打开拨号盘，所以需要 `CALL_PHONE` 权限。

### 常用软件为什么最多 6 个？

老人端常用软件页刻意限制数量，避免重新变成一个复杂应用列表。管理页可以选择和排序，首页只取前 6 个。

### 可以不设为默认桌面吗？

可以作为普通 App 打开。但如果不设为默认桌面，按系统主页键会回到原系统桌面，不会回到 OneTap。

## 目录结构

```text
app/src/main/java/tech/huangsh/onetap/
├── data/                 # Room、DataStore、模型和仓库
├── di/                   # Hilt 依赖注入
├── service/              # 微信无障碍自动化、整点报时、开机广播
├── ui/                   # Activity、Compose 页面和主题
├── utils/                # 语音、音量、启动器、图片等工具
└── viewmodel/            # ViewModel
```

## 技术栈

- Kotlin
- Jetpack Compose / Material 3
- Hilt
- Room
- DataStore Preferences
- Coil
- Kotlin Coroutines
- XXPermissions

## License

[MIT License](LICENSE)
