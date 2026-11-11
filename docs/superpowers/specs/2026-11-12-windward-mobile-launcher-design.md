# Windward 手机版启动器设计规格

状态：已由用户于 2026-11-12 确认

## 1. 项目目标

以 Fold Craft Launcher（FCL）官方 `1.3.4.5` 发布版为唯一功能基线，制作可公开分发的 Android 通用 APK。产品名称为“Windward 手机版启动器”。

本项目采用最小定制：保留原版 FCL 的界面结构、账户、游戏下载和游戏启动能力，仅替换品牌与应用身份，并彻底关闭启动器自身更新和 FCL 官方公告。

## 2. 上游基线与许可证

- 上游仓库：`FCL-Team/FoldCraftLauncher`
- 固定标签：`1.3.4.5`
- 固定提交：`b08db5e96a57bc74ae536fee337b32aa9c9ea156`
- 许可证：GPL-3.0
- 修改版继续保留上游 `LICENSE`、原作者声明和第三方许可证。
- 公开仓库需增加修改说明，明确标注这是 Windward 维护的非官方修改版，不代表 FCL-Team。
- 发布 APK 时必须同时提供与该 APK 对应的完整源码。

公开仓库名称为 `windward-mobile-launcher`。远程 URL 由独立的仓库创建任务提供；URL 不影响本地实现与验收标准。

## 3. 范围

### 3.1 必须完成

- 应用显示名称改为“Windward 手机版启动器”。
- Android `applicationId` 改为 `com.windward.launcher`。
- 保留现有 Java/Kotlin namespace 与类包 `com.tungsten.fcl`，不做无收益的大规模包重命名。
- 应用图标使用用户提供的完整图片，不裁掉 `WindWard` 文字。
- 版本显示为 `1.3.4.5-Windward.1`。
- 输出一份包含 ARM32、ARM64、x86 和 x86_64 的通用 APK。
- 彻底关闭启动器自身的自动检查更新、手动检查更新、更新说明和 APK 更新下载。
- 彻底关闭 FCL 官方公告请求、公告卡片和公告错误日志。
- 保留全部游戏内容下载能力和原版错误处理。
- 创建独立发布签名并验证 APK。
- 公开源码、修改说明、APK、SHA-256 校验值和验证报告。

### 3.2 明确不做

- 不重做 FCL 页面、导航、主题或交互布局。
- 不增加 Windward 自有更新通道；该版本永久停留在固定基线。
- 不修改 Minecraft、Java、模组、整合包、资源包和控制布局的下载源或任务实现。
- 不移除模组检查更新、控制布局更新、认证组件更新等游戏功能。
- 不声称兼容 Android 7 或更早版本。
- 不把签名密钥、密码或私有配置提交到 GitHub。

## 4. 应用身份与品牌资源

### 4.1 包身份

`FCL/build.gradle.kts` 中的 `applicationId` 改为 `com.windward.launcher`，namespace 保持不变。调试构建继续使用 `.debug` 后缀。

所有 Provider authority 必须基于新 application ID：

- `${applicationId}.provider`
- `${applicationId}.document.provider`
- `${applicationId}.crashreporterinitprovider`
- 调试资源 `file_browser_provider` 使用 `com.windward.launcher.debug.provider`

这样 Windward 与官方 FCL 可以同时安装，文件共享和崩溃报告 Provider 不发生冲突。

### 4.2 名称与版本

- 所有语言环境的桌面应用名统一显示“Windward 手机版启动器”，避免 locale 切换后恢复为 FCL。
- `versionCode` 保持上游 `1345`；独立 application ID 不会与官方包发生版本冲突。
- `versionName` 改为 `1.3.4.5-Windward.1`。
- APK 文件名固定为 `Windward-Mobile-Launcher-1.3.4.5-universal.apk`。

### 4.3 图标

品牌源文件：`C:\Users\kaoya\Desktop\服务器图片\服务器白底图标.jpg`

- 尺寸：1080 × 1051
- SHA-256：`0B53F1CE193530612686586C0624067FE5D03E15BF7D16800724E5DD51A4DED9`
- 使用完整画面，包括上方像素图形和下方 `WindWard` 文字。
- 从同一源图生成 Android 所需的 legacy mipmap 与 adaptive icon 资源。
- 生成过程允许按画布补白以满足方形和安全区要求，但不得裁掉文字或改变图案内容。

## 5. 启动器更新关闭设计

FCL 中“启动器更新”与“模组更新”等功能同名，修改必须只作用于以下启动器级入口。

### 5.1 自动检查

在 `FCL/src/main/java/com/tungsten/fcl/activity/MainActivity.kt` 中移除 `UpdateChecker.getInstance().checkAuto(...)` 的启动调用与无用 import。

### 5.2 设置入口

在 `LauncherSettingAdapter.kt` 中移除启动器升级整行，包括：

- `CHECK_UPDATE`
- `SHOW_CHANGELOG`
- `settings_launcher_upgrade` 对应的按钮行

在 `LauncherSettingPage.java` 中移除对应分发分支和 `UpdateChecker` 依赖。设置页不得留下不可点击项或空白占位。

### 5.3 网络实现与更新 APK

`com.tungsten.fcl.upgrade` 下不再保留可触发远程版本表或下载更新 APK 的可达路径。实现应移除以下 URL 在最终 APK 中的引用：

- `raw.githubusercontent.com/FCL-Team/FoldCraftLauncher/main/version_map.json`
- `gitee.com/fcl-team/FCL-Repo/raw/main/res/version_map.json`

若 `UpdateChecker.getCurrentVersionCode` 仍被公告模型引用，则把本地版本读取迁移为无网络的小型工具，不能为了该方法保留远程更新实现。

最终 APK 不显示检查更新、更新说明、发现新版本、忽略更新或下载启动器更新等 UI。

## 6. 官方公告关闭设计

在 `FCL/src/main/java/com/tungsten/fcl/ui/main/MainUI.java` 中：

- 不调用 `checkAnnouncement()`。
- 移除公告 URL、公告网络任务和相关错误日志。
- 公告容器保持 `GONE`，不显示空卡片。
- 删除仅用于远程公告交互的点击与隐藏逻辑时，不得影响皮肤预览等主界面功能。

最终 APK 不包含或访问以下公告端点：

- `raw.githubusercontent.com/FCL-Team/FCL-Repo/refs/heads/main/res/announcement_v2.txt`
- `gitee.com/fcl-team/FCL-Repo/raw/main/res/announcement_v2.txt`

所有内置语言的隐私说明必须同步修改，不能继续宣称应用会自动获取 FCL 更新和公告；未提供人工翻译的语言使用准确的英文修改说明。上游版权、GPL 和第三方声明保持不变。

## 7. 必须保留的下载与启动链路

下列功能按上游代码原样保留：

- Minecraft 正式版、快照版和历史版本下载。
- Java 运行时安装与选择。
- Forge、Fabric、NeoForge、Quilt、OptiFine 等版本组件。
- Modrinth、CurseForge 等模组和整合包能力。
- 模组检查更新与文件下载。
- 资源包、光影、世界、控制布局和渲染器相关资源。
- 微软账户、外置登录、离线账户与皮肤加载。
- 游戏文件校验、断点续传、重试、镜像选择、哈希校验和下载通知。
- JVM 启动、游戏运行服务、崩溃报告和日志导出。

禁止用“删除所有 update 代码”一类宽泛操作实现需求。每个被删除的入口都要证明属于启动器自身更新或官方公告。

## 8. 构建环境与产物

### 8.1 工具链

- JDK 17
- Gradle Wrapper 8.13
- Android compile SDK 35
- Android target SDK 34
- Android min SDK 26（Android 8.0）
- Android NDK `27.0.12077973`

新增 SDK、NDK、Gradle 缓存和临时文件尽量保存在当前工作区的专用工具链目录，不修改现有桌面启动器工程。

### 8.2 架构与构建任务

使用上游 `all` 架构模式生成通用包，保留：

- `armeabi-v7a`
- `arm64-v8a`
- `x86`
- `x86_64`

正式构建以 `assembleRelease -Darch=all` 为基准。调试和测试构建使用 `assembleFordebug -Darch=all`。

### 8.3 签名

- 创建 Windward 专用 release keystore。
- keystore、密码文件和本地签名属性位于公开仓库之外。
- Git 忽略规则必须覆盖所有本地签名文件。
- Release 使用 APK Signature Scheme v2 与 v3，与上游 minSdk 设计一致。
- 发布前使用 `apksigner verify --verbose --print-certs` 验证签名。

## 9. 验证方案

### 9.1 静态与构建验证

- `git diff --check`
- 项目 checkstyle 与单元测试
- `assembleFordebug -Darch=all`
- `assembleRelease -Darch=all`
- `apksigner verify --verbose --print-certs`
- 使用 Android 构建工具核对 package、label、version、minSdk、targetSdk 和四种 ABI。
- 扫描最终源码与 APK，确认不包含四个被禁用的更新/公告 URL。
- 扫描设置模型和主界面，确认没有启动器升级按钮与公告空容器。

### 9.2 运行验证

在隔离的 Android 环境或连接的真实手机上：

1. 与官方 FCL 并存安装。
2. 冷启动并进入主页，无更新或公告请求错误。
3. 创建离线账户并打开版本下载页。
4. 下载一个 Minecraft 版本，确认进度、重试、校验和完成状态。
5. 下载一个模组或资源，确认内容下载功能未被误伤。
6. 启动已下载版本，至少到 Minecraft 主菜单。
7. 记录启动期间网络请求，确认没有 FCL 更新和公告端点。

若没有可连接的真实 Android 设备，电脑端完成 APK 安装和页面级验证后，Minecraft 进入世界验证必须明确标记为“待真实手机验证”，不能将成功编译等同于完整运行成功。

## 10. 错误处理

- 被关闭的更新与公告逻辑必须静默不存在，不进行后台重试，不产生弹窗或警告日志。
- 游戏资源下载继续使用 FCL 原有重试、哈希校验和错误报告。
- 品牌资源生成失败、Provider 冲突、签名失败或 ABI 缺失都视为发布阻断问题。
- 任何会改变通用下载器行为的修改都超出本规格范围，必须停止并重新确认设计。

## 11. 公开发布内容

GitHub 公共仓库包含：

- 完整修改源码与构建脚本
- GPL-3.0 `LICENSE`
- 原作者和修改版说明
- 不含秘密的构建说明
- 设计规格与验证报告

GitHub Release 包含：

- `Windward-Mobile-Launcher-1.3.4.5-universal.apk`
- `SHA256SUMS.txt`
- 修改摘要、兼容范围和安装说明
- 对应源码标签或源码归档

APK 本身不检查 GitHub Release，也不提供任何后续自动更新机制。

## 12. 验收标准

只有同时满足以下条件才可声明完成：

- APK 名称、图标、包名和版本均符合本规格。
- APK 可与官方 FCL 同时安装。
- 四种 ABI 均存在，minSdk 为 26。
- 启动器更新与官方公告的入口、端点和后台请求全部消失。
- Minecraft、Java、模组、整合包和资源下载功能仍可用。
- APK 通过签名验证并提供 SHA-256。
- 公共仓库保留 GPLv3 与对应源码。
- 运行验证结果如实区分“已验证”“待真实手机验证”。
