# Windward 手机版启动器实施计划

规格来源：`docs/superpowers/specs/2026-11-12-windward-mobile-launcher-design.md`

目标基线：FCL `1.3.4.5` / `b08db5e96a57bc74ae536fee337b32aa9c9ea156`

## 执行原则

- 每个阶段只修改已批准范围内的文件。
- 先创建可重复运行的静态验收脚本，再做行为修改。
- 启动器更新与内容更新必须按类和调用点区分，禁止关键词批量删除。
- 每个代码阶段通过静态验证和 `git diff --check` 后再提交。
- 构建、签名和运行测试单独提交验证报告，不把“编译成功”当作“手机运行成功”。

## 任务 1：建立定制验收脚本

新增文件：

- `scripts/verify-windward-customization.ps1`

脚本检查：

1. `applicationId`、`versionName`、APK 输出名和调试 Provider 使用 Windward 值。
2. `app_name` 为“Windward 手机版启动器”。
3. 四个 FCL 更新/公告 URL 不存在于可编译源码和资源。
4. `MainActivity` 不再调用 `UpdateChecker.checkAuto`。
5. 启动器设置模型不再包含 `CHECK_UPDATE`、`SHOW_CHANGELOG` 和升级按钮行。
6. `MainUI` 不再发起公告请求。
7. Minecraft、Java、模组和下载核心目录仍存在，关键类未被删除。
8. 图标源文件存在且 SHA-256 与用户提供文件一致。
9. GPL `LICENSE` 与修改声明存在。

先在未修改基线上运行一次，预期因品牌和更新入口而失败；全部改造完成后必须通过。

## 任务 2：修改应用身份、版本和输出名

修改文件：

- `FCL/build.gradle.kts`
- 必要时 `FCL/src/main/AndroidManifest.xml`

操作：

- `applicationId = "com.windward.launcher"`
- namespace 保持 `com.tungsten.fcl`
- `versionName = "1.3.4.5-Windward.1"`
- 调试 Provider 改为 `com.windward.launcher.debug.provider`
- APK 输出名改为 `Windward-Mobile-Launcher-<version>-universal.apk`
- 移除不再使用的 `updateMap` 任务和根目录 `version_map.json`

验证：运行验收脚本的身份检查，并确认 Manifest 中所有 authority 使用 `${applicationId}` 或新调试值。

## 任务 3：替换应用名和图标

修改或新增文件：

- `FCL/src/main/res/values/strings.xml`
- `FCL/src/main/res/values-vi/strings.xml`
- `FCL/src/main/res/drawable-nodpi/windward_launcher_source.jpg`
- `FCL/src/main/res/drawable/windward_launcher_icon.xml`
- `FCL/src/main/res/mipmap/ic_launcher.xml`
- `FCL/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `FCL/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`（若 Manifest 分开引用）
- 旧 FCL launcher icon 资源

操作：

- 复制用户给定的完整 JPG，不裁字、不重绘。
- 用 Android drawable/inset 资源生成安全区，不进行破坏性位图重采样。
- 所有 locale 的桌面应用名统一为“Windward 手机版启动器”。
- Manifest 的普通和圆形图标均指向 Windward 图标。

验证：检查源图 SHA-256；构建后用资源检查工具核对 label 和 icon 引用。

## 任务 4：关闭启动器自身更新

修改文件：

- `FCL/src/main/java/com/tungsten/fcl/activity/MainActivity.kt`
- `FCL/src/main/java/com/tungsten/fcl/ui/setting/LauncherSettingAdapter.kt`
- `FCL/src/main/java/com/tungsten/fcl/ui/setting/LauncherSettingPage.java`
- `FCL/src/main/java/com/tungsten/fcl/upgrade/UpdateChecker.java`
- `FCL/src/main/java/com/tungsten/fcl/upgrade/UpdateDialog.java`
- `FCL/src/main/java/com/tungsten/fcl/upgrade/ChangelogDialog.java`
- `FCL/src/main/java/com/tungsten/fcl/upgrade/RemoteVersion.java`
- 仅供启动器更新使用的布局和字符串资源

操作：

- 删除自动检查调用与设置入口。
- 删除远程版本表、更新弹窗和 APK 更新下载的可达实现。
- 若本地版本号工具仍有引用，将其迁移到无网络工具类。
- 保留模组检查更新、控制布局更新、认证组件更新及全部下载核心。

验证：运行验收脚本；用 `rg` 证明所有被删除引用只属于启动器更新；编译 Java/Kotlin。

## 任务 5：关闭官方公告

修改文件：

- `FCL/src/main/java/com/tungsten/fcl/ui/main/MainUI.java`
- `FCL/src/main/java/com/tungsten/fcl/ui/main/Announcement.java`
- `FCL/src/main/res/layout/ui_main.xml`
- `FCL/src/main/assets/legal/*/privacy.txt`

操作：

- 删除公告请求、模型、点击处理和公告视图。
- 主界面布局移除公告卡片，不留空白区域。
- 所有内置隐私文本删除“自动获取更新和公告”的陈述；无人工翻译的语言使用准确英文说明。
- 不改变皮肤预览、账户和主界面其他生命周期代码。

验证：四个禁用端点在可编译源码、资源和最终 APK 中均为零命中。

## 任务 6：增加开源与修改说明

新增或修改文件：

- `README.md`
- `README_EN.md`
- `MODIFICATIONS.md`
- `.gitignore`

操作：

- 在 README 顶部标注项目名称、上游、固定基线、非官方身份和 GPLv3。
- 在 `MODIFICATIONS.md` 列出品牌、包名、更新/公告关闭及下载功能未改动。
- 保留上游 `LICENSE` 和第三方许可证。
- 忽略本地 release keystore、密码、SDK、NDK、Gradle 缓存和发布临时目录。

验证：验收脚本检查声明与许可证；`git status` 不出现秘密文件。

## 任务 7：建立安全的 Windward 签名配置

修改文件：

- `FCL/build.gradle.kts`
- `.gitignore`

删除上游发布密钥文件：

- `key-store.jks`
- `private_key.pepk`

保留公开调试密钥：

- `debug-key.jks`

操作：

- release signing 从 `WINDWARD_KEYSTORE_PATH` 和 `WINDWARD_KEYSTORE_PASSWORD` 读取。
- 在工作区外层的专用 secrets 目录创建 Windward release keystore。
- 密码使用随机值并以 Windows DPAPI 加密保存，命令输出不打印明文。
- release 构建缺少密钥时给出清晰配置错误；fordebug 构建不依赖私钥。

验证：Git 追踪文件中没有私钥；release APK 通过 v2/v3 签名验证。

## 任务 8：安装隔离 Android 工具链

目标目录：

- `C:\Users\kaoya\Desktop\我的世界社区\android-toolchain`

操作：

- 使用官方 Android command-line tools。
- 安装 platform 35、build-tools、platform-tools、NDK `27.0.12077973` 和所需 CMake。
- 构建命令显式使用 JDK 17，避免系统 `JAVA_HOME` 的 JDK 21 混入。
- `local.properties` 指向隔离 SDK，文件不提交。

验证：记录 `java -version`、`sdkmanager --list_installed` 与 Gradle 环境信息。

## 任务 9：静态测试与完整构建

按顺序执行：

1. `scripts/verify-windward-customization.ps1`
2. `git diff --check`
3. Gradle checkstyle
4. Gradle 单元测试
5. `assembleFordebug -Darch=all`
6. `assembleRelease -Darch=all`

失败时保留完整日志并修复根因，不跳过验证任务。

## 任务 10：APK 审计与运行测试

审计：

- 包名、应用名、版本、minSdk、targetSdk
- 四种 ABI
- v2/v3 签名证书
- 禁用 URL 零命中
- APK SHA-256

运行：

- 隔离安装并与官方 FCL 并存。
- 冷启动、进入主页、创建离线账户。
- 打开版本下载页并实际下载一个 Minecraft 版本。
- 下载一个模组或资源。
- 启动到 Minecraft 主菜单。
- 观察日志和网络，确认没有 FCL 更新或公告请求。

若没有真实 Android 设备，报告中明确区分模拟器验证与待真实手机验证。

## 任务 11：发布准备

新增输出：

- `release/Windward-Mobile-Launcher-1.3.4.5-universal.apk`
- `release/SHA256SUMS.txt`
- `release/verification.md`

操作：

- 关联外部任务创建的 GitHub 公共仓库。
- 推送源码分支和对应标签。
- 在 GitHub Release 上传 APK、校验值和验证报告。
- 若当前环境没有 GitHub 写入凭据，准备好所有本地产物和可复制发布说明，并明确由用户执行的唯一剩余上传步骤。

## 完成定义

实施只有在规格中的全部验收标准通过、签名 APK 与校验值生成、验证边界如实记录后才算完成。GitHub 远程仓库 URL 或真实手机不可用时，仅相应的上传或手机测试标为待完成，其他工作继续推进。
