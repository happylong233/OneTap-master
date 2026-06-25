# OneTap

OneTap 是一款面向长辈和家属的 Android 桌面应用。它把手机首页简化成更清晰的家人联系、常用应用、天气日期和设置入口，让长辈可以用更少步骤完成日常操作，也让家属更容易维护联系人和常用软件。

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="OneTap app icon">
</p>

## 项目定位

OneTap 不是普通通讯录，也不是单纯的启动器。它的核心目标是提供一个适合长辈长期使用的“简化桌面”：

- 首页信息足够大、足够清楚，减少复杂入口。
- 家人联系人优先展示，支持电话、微信语音和微信视频等常用联系动作。
- 常用应用由家属配置，长辈只看到真正需要的入口。
- 显示、语音、主题等设置集中管理，方便按使用习惯调整。

## 当前功能

### 长辈首页

- 显示当前时间、日期、星期和农历信息。
- 显示当前天气摘要，并支持定时刷新。
- 提供家人联系人快捷入口。
- 提供常用应用快捷入口。
- 支持作为系统桌面使用。

### 家人联系

- 添加、编辑、删除联系人。
- 支持联系人头像。
- 支持联系人排序和紧急联系人标记。
- 支持从系统通讯录导入。
- 支持电话、微信语音、微信视频等联系方式。

### 常用应用

- 扫描本机已安装应用。
- 将常用应用添加到长辈首页。
- 支持搜索、分类展示和排序。
- 针对部分系统的应用列表权限提供引导说明。

### 微信辅助

- 通过无障碍服务辅助发起微信语音或视频通话。
- 通过独立的微信自动化控制逻辑处理操作流程。
- 适合减少长辈在微信内查找联系人和按钮的步骤。

### 显示与语音设置

- 支持字体大小设置。
- 支持普通/高对比度显示。
- 支持蓝色和暖色主题。
- 支持语音提示、语速、音量等辅助设置。
- 支持默认桌面状态检测和退出桌面模式。

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
- 拖拽排序：Compose Reorderable
- 农历：lunar

## 环境要求

- Android Studio 2024.2.1 或更高版本
- JDK 11 或更高版本，推荐 JDK 17/18
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
├── data/                 # 数据层
│   ├── local/            # Room、DAO、迁移和类型转换
│   ├── model/            # 数据模型
│   ├── remote/           # 天气 API 和远程数据映射
│   └── repository/       # 数据仓库
├── di/                   # Hilt 依赖注入
├── service/              # 微信无障碍与自动化服务
├── ui/                   # Activity、Compose 页面和主题
├── utils/                # 工具类
└── viewmodel/            # ViewModel
```

## 主要权限

应用会根据功能需要申请以下权限：

- 电话权限：用于拨打电话。
- 应用列表权限：用于读取本机已安装应用并配置常用应用。
- 网络权限：用于获取天气数据。
- 无障碍服务：用于辅助发起微信语音或视频通话。
- 存储/图片访问相关权限：用于联系人头像选择与本地保存。
- 振动权限：用于交互反馈。

## 使用建议

1. 安装后将 OneTap 设置为默认桌面。
2. 家属先进入设置中心，添加家人联系人和常用应用。
3. 如需微信语音或视频通话辅助，按提示开启无障碍服务。
4. 根据长辈视力和使用习惯调整字体、对比度、主题和语音提示。

## 开源协议

本项目使用 [MIT License](LICENSE)。
