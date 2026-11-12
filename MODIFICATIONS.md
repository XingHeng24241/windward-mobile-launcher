# Windward Modification Notice

Windward 手机版启动器是 Fold Craft Launcher（FCL）的非官方修改版。

## Upstream baseline

- Project: Fold Craft Launcher
- Repository: <https://github.com/FCL-Team/FoldCraftLauncher>
- Release: `1.3.4.5`
- Commit: `b08db5e96a57bc74ae536fee337b32aa9c9ea156`
- License: GPL-3.0

## Windward changes

- 应用名称改为“Windward 手机版启动器”。
- Android application ID 改为 `com.windward.launcher`，可与官方 FCL 并存安装。
- 桌面和启动页图标替换为用户提供的 Windward 图标。
- 当前版本名称为 `1.3.4.5-Windward.2`，Android `versionCode` 为 `1346`。
- 首次运行的环境安装页会继续安装固定的 Windward 客户端整合包。
- 删除启动器自身的自动/手动更新、更新说明和 APK 更新下载。
- 删除 FCL 官方公告请求和公告界面。
- 更新内置隐私说明，使联网行为与修改后的程序一致。
- 使用独立 Windward 发布签名，私钥不包含在仓库中。

## Preserved features

Minecraft、Java、Forge、Fabric、NeoForge、Quilt、OptiFine、模组、整合包、资源包、光影、世界、控制布局、账户登录与游戏启动功能保留上游实现。模组检查更新等内容更新功能没有被关闭。

## No affiliation

Windward 不是 FCL-Team 的官方版本。上游项目、原作者声明和第三方许可证归各自权利人所有。
