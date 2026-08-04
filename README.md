# ☁️ WebDavFileSync - 云盘与本地文件比对双向同步 Android 应用

![Android Target SDK](https://img.shields.io/badge/Android%20Target%20SDK-36%20(Android%2016)-brightgreen)
![Architecture](https://img.shields.io/badge/Architecture-64--bit%20(arm64--v8a%20%2F%20x86__64)-blue)
![UI Specification](https://img.shields.io/badge/UI-Material%20Design%203-purple)
![Language](https://img.shields.io/badge/Language-Kotlin%202.0-orange)
![License](https://img.shields.io/badge/License-MIT-green)

一款基于 **WebDAV 协议** 的 64 位原生 Android 文件增量比对与双向同步应用，针对 **Android 16** 及小米 HyperOS 3 系统进行了全屏状态栏沉浸（Edge-to-Edge）优化。通过文件名（包含扩展名）差异识别算法，实现无损增量合并（仅做缺失文件的补充上传与下载，不执行任何文件删除操作）。

---

## 🌟 核心功能特性

### 1. 🔍 智能文件名差异比对与无损同步
- **增量双向合并 (Two-Way Merge)**：精准扫描 WebDAV 云端与本地 SAF 目录，补全两端缺失文件。
- **单向传输支持**：支持配置单向上传（本地 -> 云盘）或单向下载（云盘 -> 本地）。
- **无损数据保护**：绝不删除两端任何已有文件，确保数据绝对安全。

### 2. 📁 SAF 本地目录与 WebDAV 云端在线浏览选择
- **真实 Android 路径解析**：自动将 Android SAF URI（如 `primary%3AMusic`）解析为直观可读的绝对路径（`/storage/emulated/0/Music/...`）。
- **云端目录在线浏览**：新增/修改同步计划时，支持一键调出 WebDAV 云端文件浏览器，直达 WebDAV 根目录（`/`）在线选择文件夹，彻底消除手动拼写路径导致的 404 报错。

### 3. 🎨 现代 Material Design 3 沉浸式 UI
- **全屏沉浸状态栏 (Edge-to-Edge)**：原生支持 Android 16 / HyperOS 3 全屏沉浸，顶部 Header 与状态栏自然融合，动态适应深色/浅色主题。
- **精简三栏底部导航**：包含 **主页**、**同步计划**、**账号** 三个底部 Tab 页面。
- **非全屏悬浮设置面板 (Modal)**：顶部 Header 内置设置按钮 ⚙，弹出悬浮设置窗口，支持主题模式切换（自动、浅色、深色高亮无勾选）、Wi-Fi 专属同步等。
- **通知与历史记录独立页面**：顶部通知 🔔 与首页历史记录按钮独立弹窗展示，避免占据首页视野。

### 4. 📊 实时同步状态面板与任务中断
- **实时传输进度面板**：手动同步时展现进度卡片，实时刷新检查文件数（如 `125/350`）、传输文件数（如 `89/350`）、精准耗时计时器（`00:01:35`）、进度条（`65%`）及当前处理文件名（如 `IMG_1023.NEF 正在上传...`）。
- **随时安全取消**：支持随时点击 `[取消同步]` 一键安全终止当前传输任务。

---

## 🛠️ 技术栈与架构

- **编程语言**：Kotlin 2.0
- **构建系统**：Gradle 8.7 (AGP 8.5.2) + Version Catalog (`libs.versions.toml`) + KSP 2.0.20
- **界面框架**：Jetpack Compose + Material Design 3 (`androidx.compose.material3`)
- **网络与 WebDAV 引擎**：OkHttp 4.12.0（实现 `PROPFIND`, `GET`, `PUT`, `MKCOL`, `HEAD` 动词及 DOM XML 解析）
- **本地持久化**：
  - Room 数据库（存储同步计划 Entity 与历史日志 Entity）
  - Preferences DataStore（存储 WebDAV 账号密文、主题及偏好设置）
- **存储访问控制**：Android Storage Access Framework (SAF `ACTION_OPEN_DOCUMENT_TREE`) 持久化授权

---

## 🚀 快速开始与编译构建

### 环境要求
- JDK 17 或 JDK 21 (推荐 Microsoft OpenJDK 21)
- Android Studio Jellyfish / Ladybug 或更高版本

### 命令行编译 (Gradle CLI)
在 PowerShell / 终端中运行：

```powershell
# 1. 切换至项目目录
cd WebDavFileSync

# 2. 设置 JAVA_HOME 并执行编译
$env:JAVA_HOME="C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot"
.\gradlew.bat assembleDebug
```

编译产物位于：`app/build/outputs/apk/debug/app-debug.apk`

---

## 📖 使用指南

1. **配置账号**：打开 App 点击底栏 `账号` 页面，输入 WebDAV 服务器地址（支持 123云盘、坚果云、Nextcloud、OwnCloud、AList 等）、用户名与密码/App Token，点击 `测试连接` 验证连通性并保存。
2. **创建同步计划**：切换至 `同步计划` 页面，点击右下角 `新增同步计划`：
   - 点击 `点击选择云盘文件夹` 在线浏览并选中云端目录。
   - 点击 `点击选择本地文件夹` 通过系统 SAF 选择手机本地文件夹。
   - 选择同步模式（双向合并 / 单向上传 / 单向下载）后保存。
3. **执行同步**：回到 `主页`，点击 `立即同步`，即可在中部展开的实时进度面板中直观查看同步进度与当前传输文件名。

---

## 📄 开源许可证

本项目采用 [MIT License](LICENSE) 许可证。
