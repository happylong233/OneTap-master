# OneTap

OneTap 是一款面向长辈使用、由家属配置的 Android 简化桌面应用。当前实现把主界面收敛为两个老人端页面：`找家人` 和 `看视频/常用软件`，让长辈通过大按钮、大照片和语音提示完成最常见的联系家人、打开软件、返回桌面的操作。

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="OneTap app icon">
</p>

## 当前定位

OneTap 不是完整通讯录，也不是通用桌面启动器。它更像一个“家属先配置、长辈少选择”的极简桌面：

- 老人端首页优先展示家人照片，点击照片后选择微信视频、微信语音或电话。
- 常用软件页只展示家属选好的少量应用，避免长辈在系统应用列表里寻找。
- 家属设置集中管理联系人、常用软件、微信辅助、电话权限、显示效果和报时设置。
- 应用可作为 Android 默认桌面，按系统主页键会回到 OneTap。

## 已实现功能

### 老人端桌面

- 主入口为 `ElderHomeShell`，包含 `找家人` 和 `常用软件` 两页。
- `找家人` 页按设置显示 2、4、6 或 8 个家人照片卡片。
- `常用软件` 页最多显示 6 个由家属启用的应用。
- 支持大按钮、大图标、长按也当作点击，降低误触和操作门槛。
- 支持作为系统 HOME 桌面，进入或回到主界面时默认切回 `找家人` 页。

### 家人联系

- 支持新增、编辑、删除联系人。
- 联系人字段包括显示名称、关系称呼、手机号、微信备注名、微信号、头像、默认联系方式、是否显示在首页、是否紧急联系人。
- 老人端点击家人照片后弹出联系方法，可选择微信视频、微信语音或直接拨打电话。
- 联系人管理页支持上移、下移排序。
- 仓库层保留了读取系统通讯录的方法，但当前 Compose 界面没有接入“从系统通讯录导入”的完整入口。

### 常用软件

- 扫描本机可启动应用，缓存应用名称、包名、图标和安装/更新时间。
- 家属可搜索应用、启用/禁用应用，并调整已启用应用的显示顺序。
- 老人端只展示已启用应用的前 6 个，点击应用卡片直接启动对应软件。
- 对小米系统预留了应用列表权限设置入口。

### 微信辅助

- 通过无障碍服务辅助打开微信并发起语音或视频通话。
- 联系人需要配置微信备注名或微信号，启动任务前会将搜索关键词写入剪贴板。
- 如果微信未安装、无障碍未开启、任务正在执行或剪贴板写入失败，会用语音提示让家属协助检查。
- 无障碍服务只在点击家人并选择微信通话后执行一次任务。

### 家属设置

- 联系人管理、常用软件管理、微信无障碍设置入口。
- 电话设置说明：老人端点击电话会直接拨出，首次使用需要授权电话权限。
- 长按也当作点击开关。
- 默认最大音量开关：回到老人桌面、打开常用软件、通话和报时时可自动拉高常用音量。
- 整点语音报时：可选择 0:00 到 23:00 中的具体整点，开机后会重新调度。
- 默认桌面状态检测、打开系统默认桌面设置、退出老人桌面模式。

### 显示设置

- 主题颜色：蓝色、暖色。
- 字体大小：小、中、大。
- 对比度：标准、高对比度。
- `找家人` 每页数量：2、4、6、8 个。

## 当前未接入或需注意

- 天气、日期、农历相关工具类和 Open-Meteo 服务仍在源码中，但当前老人端首页没有展示天气、日期或农历信息。
- `oneTapPhoneEnabled`、`floatingBallEnabled` 等设置字段存在，但当前主要 UI 没有完整功能入口。
- README 中描述的功能以当前源码入口和 Compose 页面为准，不代表历史设计稿中的所有内容都已经接入。

## 技术栈

- 语言：Kotlin
- UI：Jetpack Compose、Material 3
- 架构：MVVM、Repository
- 依赖注入：Hilt
- 本地数据库：Room
- 设置存储：DataStore Preferences
- 网络：Retrofit、OkHttp
- 图片加载：Coil
- 异步：Kotlin Coroutines
- 权限处理：XXPermissions
- 农历库：lunar（当前未接入首页展示）

## 环境要求

- Android Studio 2024.2.1 或更高版本
- JDK 17/18 推荐；项目当前 Gradle 配置使用 Java 8 字节码目标
- Android SDK API 35
- Gradle Wrapper 已随项目提交

项目配置：

- `applicationId`: `tech.huangsh.onetap`
- `minSdk`: 24
- `targetSdk`: 35
- `compileSdk`: 35
- `versionName`: 1.0
- `versionCode`: 1

## 构建与运行

克隆项目：

```bash
git clone https://github.com/happylong233/OneTap-master.git
cd OneTap-master
```

检查构建：

```bash
./gradlew build
```

安装调试版本：

```bash
./gradlew installDebug
```

生成 Debug APK：

```bash
./gradlew assembleDebug
```

生成 Release APK：

```bash
./gradlew assembleRelease
```

在 Windows PowerShell 中可以使用：

```powershell
.\gradlew.bat build
.\gradlew.bat installDebug
```

## 目录结构

```text
app/src/main/java/tech/huangsh/onetap/
├── data/                 # Room 数据库、数据模型、仓库和天气相关远程服务
├── di/                   # Hilt 依赖注入
├── service/              # 微信无障碍自动化、整点报时、开机广播
├── ui/                   # Activity、Compose 页面和主题
├── utils/                # 语音、音量、启动器、图片、日期等工具
└── viewmodel/            # ViewModel
```

## 主要权限

应用会根据功能使用以下权限或系统能力：

- `CALL_PHONE`：用于老人端直接拨打电话。
- `QUERY_ALL_PACKAGES` / `GET_INSTALLED_APPS`：用于读取可启动应用列表并配置常用软件。
- `INTERNET` / `ACCESS_NETWORK_STATE`：天气服务代码使用网络能力，但当前首页未展示天气。
- `BIND_ACCESSIBILITY_SERVICE`：用于微信语音/视频通话辅助。
- `READ_EXTERNAL_STORAGE` 和 `FileProvider`：用于联系人头像选择与本地文件访问。
- `VIBRATE`、`MODIFY_AUDIO_SETTINGS`：用于交互反馈和自动调整音量。
- `SCHEDULE_EXACT_ALARM`、`FOREGROUND_SERVICE`、`RECEIVE_BOOT_COMPLETED`：用于整点语音报时和开机后重新调度。

## 使用建议

1. 安装后将 OneTap 设置为默认桌面。
2. 家属进入设置中心，先添加家人联系人和常用软件。
3. 如需微信语音或视频通话辅助，在系统无障碍设置中开启 OneTap 微信辅助。
4. 首次使用电话直拨时授权电话权限。
5. 根据长辈习惯调整字体大小、对比度、主题、家人每页数量、长按点击、最大音量和整点报时。

## 开源协议

本项目使用 [MIT License](LICENSE)。
