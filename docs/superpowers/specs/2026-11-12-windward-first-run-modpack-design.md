# Windward 首次启动整合包安装设计

状态：已由用户于 2026-11-12 确认

## 1. 目标

在 Windward 手机版启动器首次运行的现有“安装或更新运行环境”阶段中，安装固定的 Windward Modrinth 整合包。玩家只需点击一次现有安装按钮；启动器完成运行环境后，继续下载并安装游戏版本、Fabric、模组、光影、配置和服务器列表，全部成功后才进入主界面。

同时调整 Windward 图标的安全留白，并让桌面主图标和 Android 系统启动动画统一使用同一套 Windward 资源。

## 2. 固定输入

- 本功能发布版本：`1.3.4.5-Windward.2`（Android `versionCode` 1346）
- 完成判定修复版本：`1.3.4.5-Windward.3`（Android `versionCode` 1347）
- 源文件：`D:\下载\modpack.mrpack`
- 文件格式：标准 Modrinth `.mrpack`
- SHA-256：`D84FAA9172EB2561C323EB14E9AFC2850303DD3EDBCA42D0BACEE0BCC327379F`
- 整合包名称：`Windward 客户端`
- 整合包版本：`1.0.4`
- Minecraft：`1.21.11`
- Fabric Loader：`0.19.5`

整合包内声明的全部模组、光影、配置和覆盖文件原样保留，不做删除、替换或设备差异化过滤。

## 3. 分发方式

采用 APK 内置 MRPACK 方案：将 `modpack.mrpack` 作为只读 asset 随 APK 分发。MRPACK 中的索引、覆盖文件和少量本地文件直接来自 APK；Minecraft、Fabric、模组和光影继续使用索引中已有的 HTTPS 下载地址与哈希值下载。

不采用远程 MRPACK 清单，避免新增 Windward 后端依赖；不把所有外部依赖完整塞入 APK，避免 APK 体积无必要地增加。

## 4. 首次启动流程

1. 用户完成协议确认与存储权限授权。
2. `SplashActivity` 检查应用运行环境和 Windward 整合包完成状态。
3. 任一项目未完成时，显示现有 `RuntimeFragment`。
4. 用户点击一次“安装 / 更新”。
5. 现有 LWJGL、Caciocavallo、JRE、JNA 项目按原逻辑安装。
6. 运行环境全部成功后，初始化整合包安装所需的 FCL 核心服务。
7. 页面中的“Windward 游戏整合包”项目启动标准 Modrinth 安装任务。
8. 任务安装 Minecraft、Fabric、索引文件、覆盖文件及所有远程依赖，并选择安装完成的版本。
9. 只有整合包任务成功且完成标记写入后，才进入 `MainActivity`。

若运行环境已经存在而整合包尚未完成，仍进入同一前置页面，只执行整合包安装。若两者都已完成，则保持当前快速进入主界面的行为。

## 5. 界面与进度

在 `fragment_runtime.xml` 的现有项目列表末尾新增一项：

- 标题：`Windward 游戏整合包`
- 状态图标：沿用现有待安装/已完成图标
- 运行中状态：沿用现有 `FCLProgressBar`
- 详情：显示当前下载或安装阶段；可获得进度时显示已完成比例或文件信息

不增加第二个确认弹窗和第二个安装按钮。安装中禁用重复触发；失败时恢复按钮状态并展示现有错误对话框，玩家可重试。

## 6. 状态与恢复

完成状态不能只依赖单一布尔值。启动时至少验证：

- 目标游戏版本存在；
- 版本目录中的 `modrinth.index.json` 可读；
- 索引的 `name`、`versionId`、Minecraft 和 Fabric 版本与内置 MRPACK 一致；
- 首次安装任务已正常结束。

安装成功后记录固定包身份和 SHA-256。下载中断、进程被杀或安装失败时不得写入完成状态；下次启动重新回到前置页面并允许重试。已完成后不自动更新、修复或覆盖玩家对游戏目录的后续手工修改。

## 7. 安装实现边界

- 复用 FCL 已有 `ModrinthInstallTask`、`TaskExecutor`、下载器、镜像、重试和哈希校验。
- 不另写一套 Minecraft 或模组下载器。
- 把“核心服务初始化”和“打开主界面”拆开，使整合包任务能在 `RuntimeFragment` 完成后、`MainActivity` 打开前安全运行。
- 任务生成的临时 MRPACK 文件放在应用缓存目录，安装结束后可清理；APK asset 保持只读。
- 固定安装版本名使用 `Windward 客户端`。若存在同名但不匹配的用户版本，不静默覆盖，需明确报错。

## 8. 图标设计

- 保留现有 `windward_launcher_source.jpg` 的全部图案和文字，不重绘、不裁切。
- 通过 Android drawable 安全区缩小前景内容，让 `Windward` 两端在 legacy 与 adaptive launcher mask 下可见。
- `AndroidManifest.xml` 继续使用 `@mipmap/windward_launcher`。
- 普通主题和 Android 12 以上的系统 Splash 都改为同一 Windward 前景资源，清除 Android 12 仍引用旧 `ic_launcher` 的分支。

## 9. 错误处理

- 任一运行环境任务失败：保持现有行为，整合包任务不启动。
- MRPACK 解析失败：显示“整合包无效”并允许重试。
- 下载或哈希校验失败：显示原始下载错误，不进入主页。
- 空间不足、网络中断或进程结束：不标记成功，下次启动回到安装页面。
- 同名版本冲突：不删除用户文件，给出明确错误。

## 10. 验证

- 单元测试覆盖 MRPACK 元数据读取、完成状态判断和损坏/不匹配状态。
- 静态脚本验证 asset 哈希、运行环境页面项目、Splash 图标引用与旧图标引用。
- 构建通用 debug 与 release APK。
- 审计 APK，确认内置 MRPACK、Windward 图标、四种 ABI、包名和签名。
- 在可用 Android 环境做首次冷启动、中断重试、完成后再次启动和图标显示测试。
- 若没有真实 Android 设备，必须把触屏、桌面蒙版和 Minecraft 实际启动标为待真实手机验证。

## 11. 验收标准

- 一次点击即可完成运行环境与固定整合包安装。
- 整合包完成前不能进入主页；失败后可重试。
- 安装内容与用户提供的 MRPACK 完全一致。
- 完成后的正常启动不会重复安装或更新整合包。
- 桌面图标文字不再贴近蒙版边缘，系统启动动画使用同一 Windward 图标。
- 普通游戏下载、账户、启动器更新关闭和公告关闭等既有功能不受影响。
