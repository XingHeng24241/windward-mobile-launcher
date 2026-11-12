# Windward 首次启动整合包安装实施计划

规格来源：`docs/superpowers/specs/2026-11-12-windward-first-run-modpack-design.md`

## 任务 1：建立固定输入与验收脚本

- 将应用版本提升为 `1.3.4.5-Windward.2` / `1346`，保证已安装 `.1` 的设备可以覆盖安装。
- 将已核验的 `modpack.mrpack` 加入 `FCL/src/main/assets/windward/`。
- 新增静态验证脚本，检查 asset SHA-256、关键元数据、页面资源和 Splash 图标引用。
- 先让新增检查在代码接入前失败，完成后通过。

## 任务 2：封装 Windward 整合包元数据与完成状态

- 新增独立的 Windward 首次安装组件，负责从 asset 复制 MRPACK、解析 Modrinth 索引和验证目标版本。
- 完成判断同时核对版本目录、`modrinth.index.json` 和固定包身份。
- 不把“存在同名目录”等同于成功，不覆盖不匹配版本。
- 为纯状态判断编写单元测试。

## 任务 3：拆分启动核心初始化

- 将 `SplashActivity.enterLauncher()` 中的核心初始化与打开 `MainActivity` 分离。
- 运行库完成后先初始化安装任务依赖；整合包完成后再打开主页。
- 保证初始化幂等，避免失败重试或 Activity 重建导致重复初始化。

## 任务 4：接入标准 Modrinth 安装任务

- 通过当前 Profile 和 `ModpackHelper` 创建标准 Modrinth 安装任务。
- 使用 `TaskExecutor` 接收阶段、进度、成功与失败事件。
- 安装成功后选中 `Windward 客户端`，写入完成记录并清理临时文件。
- 失败时保留可重试状态，禁止进入主页。

## 任务 5：扩展首次运行页面

- 在 `fragment_runtime.xml` 新增 Windward 整合包项目。
- 在 `RuntimeFragment` 中增加状态图标、进度、详情和串行启动逻辑。
- 所有运行库成功后才开始整合包；整个流程只使用现有按钮。
- `SplashActivity` 的快速路径同时检查整合包状态。

## 任务 6：统一并缩小图标安全区

- 调整 legacy 与 adaptive Windward drawable 的前景留白，不修改源 JPG 内容。
- 修复 `values-v31/themes.xml` 对旧 `ic_launcher` 的引用。
- 确认桌面主图标与系统 Splash 引用同一 Windward 前景。

## 任务 7：静态、单元和构建验证

1. 运行新增的 Windward 首次安装验证脚本。
2. 运行既有 Windward 定制验证脚本。
3. 运行相关单元测试、`git diff --check` 和 Gradle 检查。
4. 构建 all-ABI debug APK。
5. 构建并签名 all-ABI release APK。
6. 审计包名、版本、ABI、asset、图标、签名和 SHA-256。

## 任务 8：隔离运行验证与发布产物

- 在可用 Android 环境测试首次安装、错误重试、第二次启动与图标。
- 若没有设备，明确记录未完成的真实手机验证边界。
- 生成新的 release 目录、APK、校验文件和验证报告，不覆盖上一版本产物。
