# 变更日志

记录每次改进的发现、处理结果、遗留问题与后续方向。与 git 提交一一对应。

---

## 2026-08-12 · 批次 1（P0）：基线建立与签名安全加固

**Git 提交**：`dd8f1fd chore: baseline with hardened signing layout`

### 发现的问题

1. **签名密码明文存放在仓库目录**
   `keystore/release-signing.properties` 明文保存 keystore 密码，且 store 密码与 key 密码相同。虽然旧 `.gitignore` 忽略了 `keystore/`，但目录整体位于项目内，容易随复制、打包、备份扩散。
2. **`.git` 是空目录，项目实际无版本控制**
   `git log` 报 not a git repository，5000+ 行代码没有任何历史保护。
3. **README 安全声明失真**
   README 声称"应用没有 INTERNET 权限"，但 `AndroidManifest.xml` 声明了 `android.permission.INTERNET`（用于在线校历导入）。
4. **`artifacts/` 调试产物（36.7MB 截图/录屏）未隔离**
   若直接提交会进入版本库，拖慢 clone 与历史体积。

### 解决的问题

1. **签名凭据迁移到专用目录 `secrets/`**
   - `mein-stundenplan-release.jks`、`release-signing.properties` 移入 `secrets/`，目录整体 git-ignored。
   - 新增 `secrets/release-signing.properties.template`（唯一入库文件），说明填写方式。
   - `app/build.gradle` 签名解析改为三级回退：环境变量 `MEIN_STORE_FILE`/`MEIN_STORE_PASSWORD`/`MEIN_KEY_ALIAS`/`MEIN_KEY_PASSWORD` → `secrets/release-signing.properties` → `~/.gradle/mein-stundenplan-signing.properties`。CI 可用环境变量，本地不依赖仓库内文件。
2. **建立版本控制基线**
   - 删除空 `.git`，`git init -b main`，提交基线 `dd8f1fd`。
   - `artifacts/`、`build/`、`app/build/`、`secrets/*`（模板除外）全部排除在版本库外。
3. **README 修正**
   - 安全设计一节如实说明 `INTERNET` 权限用途与约束（仅 HTTPS、校方域名白名单、禁止重定向、512KB 上限）。
   - 新增"校历在线导入"功能说明与请求约束小节。
   - 新增"发布签名"小节说明凭据解析顺序。

### 待解决的问题

- Gradle Wrapper 缺失，构建仍依赖 `tools/build-verify.ps1` 中的本机硬编码路径（批次 2）。
- `MainActivity` 与 `ReminderScheduler` 的重复加密代码未合并（批次 3）。
- PDF 解析逻辑仍在 `MainActivity` 内且无测试（批次 4）。
- Lint 8 个警告（5 处 `commit()`、3 个未使用资源）未处理（批次 5）。

### 改进方向

- 每个批次完成后提交一次 git，保持可回退粒度。
- 后续批次顺序：构建可移植性 → 存储层去重 → 解析层拆分+测试 → lint 清理。

---

## 2026-08-12 · 批次 2（短期）：Gradle Wrapper 与构建可移植性

**Git 提交**：`d9bd828 build: add offline Gradle wrapper 9.2.0 and portable build config`

### 发现的问题

1. **缺少 Gradle Wrapper**
   仓库没有 `gradlew`/`gradlew.bat`/`gradle/wrapper/`，构建依赖 `tools/build-verify.ps1` 里写死的本机路径（含 `gradle-wrapper\dists\...\<hash>\...` 这种随版本变化的 hash 目录）。换机器或 Gradle 升级小版本都会直接失败。
2. **缺少 `gradle.properties`**
   没有 JVM 内存、并行、缓存等常规配置。
3. **`app/build.gradle` 签名块变量遮蔽 DSL**
   批次 1 引入的局部变量 `storeFile`/`keyAlias` 等与 Gradle DSL 方法同名，导致 `No signature of method: java.lang.String.call()` 构建失败。
4. **离线环境无法生成 wrapper**
   Gradle 9 的 `wrapper` 任务需联网校验发行版 URL（`services.gradle.org` 不可达），标准命令失败。

### 解决的问题

1. **手动离线生成 Gradle Wrapper（9.2.0）**
   - 从本机已缓存的 Gradle 发行版中提取官方 `gradle-wrapper.jar`（`lib/plugins/gradle-wrapper-main-9.2.0.jar` 内嵌）。
   - 手写标准 `gradlew.bat`（Windows）与 `gradlew`（POSIX）启动脚本。
   - 编写 `gradle-wrapper.properties`，并设置 `validateDistributionUrl=false` 以适配离线环境。
2. **修复签名变量遮蔽**
   局部变量重命名为 `resolvedStoreFile` 等，避免与 DSL 冲突；`assembleDebug` 与 `assembleRelease` 均构建成功（release 走 `secrets/` 签名）。
3. **新增 `gradle.properties`**
   `-Xmx2048m -Dfile.encoding=UTF-8`、`parallel`、`caching`。
4. **`tools/build-verify.ps1` 改用 wrapper**
   不再硬编码 Gradle dist hash 路径；JDK/SDK/GRADLE_USER_HOME 支持环境变量覆盖，默认回退 D 盘。
5. **README 构建章节重写**
   改为以 wrapper 为主入口，保留 D 盘环境变量说明。
6. **发行版缓存对齐**
   将 `gradle-9.2.0-bin` 发行版复制到 `<Gradle 用户目录>\wrapper\dists`，使 `GRADLE_USER_HOME=<Gradle 用户目录>` 时 wrapper 离线可用。

### 待解决的问题

- `MainActivity`/`ReminderScheduler` 重复加密代码（批次 3）。
- PDF 解析逻辑拆分与测试缺失（批次 4）。
- Lint 8 个警告：5 处 `commit()`、3 个未使用资源（批次 5）。

### 改进方向

- 构建入口统一为 `.\gradlew.bat`；后续脚本与文档都以 wrapper 为准。
- 可考虑后续开启 configuration cache（Gradle 已提示）。

---

## 2026-08-12 · 批次 3（短期）：抽取 SecureStorage 消除重复加密代码

**Git 提交**：`a4e1c3a refactor: extract SecureStorage for shared Keystore AES/GCM encryption`

### 发现的问题

1. **两套几乎相同的 AES/GCM 实现**
   `MainActivity`（课程数据，alias `mein_stundenplan_courses_key`）与 `ReminderScheduler`（提醒计划，alias `mein_stundenplan_reminder_schedule_key`）各自实现了 `encrypt`/`decrypt`/`getOrCreateSecretKey`，逻辑完全相同（JSON envelope：`{"iv","data"}` Base64、128-bit GCM tag、`setRandomizedEncryptionRequired(true)`），仅 alias 不同。重复约 80 行。
2. **维护风险**
   将来升级加密方案（如换算法、加版本号、密钥轮换）需要同时改两处，容易漏改导致数据不兼容。

### 解决的问题

1. **新增 `SecureStorage.java`**
   - 包私有 final 类，构造时传入 key alias。
   - `encrypt(String)`/`decrypt(String)` 实例方法，envelope 格式与原实现逐字节兼容（已有密文无需迁移）。
   - `getOrCreateSecretKey()` 私有化。
2. **`ReminderScheduler` 改造**
   - 删除私有加密三方法与 4 个加密常量，新增 `private static final SecureStorage STORAGE = new SecureStorage(KEY_ALIAS)`。
   - 调用点改为 `STORAGE.encrypt/STORAGE.decrypt`。
   - 清理 9 个不再使用的 import。
3. **`MainActivity` 改造**
   - 删除私有加密三方法与 4 个加密常量，新增实例字段 `secureStorage`（alias `mein_stundenplan_courses_key`）。
   - 调用点改为 `secureStorage.encrypt/decrypt`。
   - 清理 8 个不再使用的 import。
4. **编译验证通过**：`gradlew compileDebugJavaWithJavac` BUILD SUCCESSFUL。

### 兼容性说明

- 密文格式未变（相同 envelope、相同 GCM 参数），仅密钥管理代码搬家，用户已有数据不受影响。
- 两个 alias 保持不变，Android Keystore 中已有密钥继续复用。

### 待解决的问题

- PDF 解析逻辑仍在 `MainActivity`，无测试（批次 4）。
- Lint 8 个警告（批次 5）。
- `MainActivity` 其余职责（UI、校历、倒计时）尚未拆分——本批次只处理了加密重复这一最安全的切入点。

### 改进方向

- 后续若加密方案升级（例如增加 envelope 版本字段），只需改 `SecureStorage` 一处。
- `TimetableRulesTest` 的测试基建可复用同样的"纯 Java + android.jar 编译检查"模式扩展到 `SecureStorage` 的 envelope 格式测试（需设备/模拟器跑 Keystore，单测只能覆盖 JSON envelope 结构）。

---

## 2026-08-12 · 批次 4（短期）：拆分 PDF 解析为纯 Java 并补充测试

**Git 提交**：`3e0bbc4 refactor: extract PDF course parsing into pure-Java PdfCourseParser`

### 发现的问题

1. **PDF 解析逻辑全部堆在 `MainActivity`**
   PDF 头部校验、stream 解压、文本项提取、课程识别等约 500 行代码都以实例方法放在 `MainActivity` 内，与 UI、校历、倒计时职责混在一起，难以单独验证。
2. **无测试**
   解析逻辑只能靠手工导入 PDF 验证；`TimetableRulesTest` 建立起的"纯 Java + 命令行断言"测试基建没有扩展到 PDF 解析。

### 解决的问题

1. **新增纯 Java 的 `PdfCourseParser.java`（无 Android 依赖）**
   - 公开入口 `parseCourses(byte[] pdfBytes, int maxPeriods, int maxStreamBytes, int maxCourses)`，返回 `ParseResult(courses, debugLog)`。
   - 内聚 `PdfTextItem` / `PdfLiteral` / `ParsedCourse` 数据类与全部解析辅助方法；`MainActivity` 不再持有任何 PDF 解析代码。
   - 调试日志改为写入 `debugLog` 由调用方输出，不再在解析器中直接调用 `android.util.Log`。
2. **`ImportException` 提升为顶层共享异常**
   原 `MainActivity` 内部类移至 `ImportException.java`，`MainActivity` 与 `PdfCourseParser` 共用同一个异常类型，`importPdf` 的 catch 行为不变。
3. **`MainActivity` 瘦身**
   - `importPdf` 改为调用 `PdfCourseParser.parseCourses(...)` 并把 `debugLog` 交给 `android.util.Log` 输出。
   - `parsedAndShowCourses` 负责把 `ParsedCourse` 映射回 `Course`，配色沿用原有 `previewCourseColorForDay` 累积逻辑，导入行为不变。
   - 删除未被调用的 `parsePdfCourses` 死代码路径。
   - 删除 2 个不再使用的 import（`ByteArrayInputStream`、`InflaterInputStream`）。
4. **新增 `tools/PdfCourseParserTest.java` 并接入 `tools/verify-logic.ps1`**
   - 覆盖：非法文件头、空 PDF、单课程（列轴/行轴）、周次时间后缀清理、非法周次跳过、超范围节次跳过、多 stream、stream 大小上限、课程数上限、字面量转义、十六进制字符串、越界坐标拒绝、debugLog 非空。
   - `verify-logic.ps1` 现在依次编译并运行 `TimetableRulesTest` + `PdfCourseParserTest`，再做全量 Android java 编译检查。

### 兼容性说明

- 解析字段与原先逐项对应（day/period/endPeriod/name/weeks/room/teacher），配色仍在 `MainActivity` 内按原顺序分配，用户导入结果不变。
- 删除的 `parsePdfCourses` 是未被调用的死代码，无行为影响。

### 待解决的问题

- Lint 8 个警告（5 处 `commit()`、3 个未使用资源）（批次 5）。
- `MainActivity` 其余职责（UI、校历、倒计时）尚未拆分。

### 改进方向

- 可复用同一纯 Java 测试基建为 `SecureStorage` 的 envelope 格式补充测试（单测仅覆盖 JSON envelope 结构）。
- 后续批次按计划处理 Lint 清理。

---

## 2026-08-12 · 批次 5（进行中 · 未提交）：UI 优化 preview + 自定义色调

**Git 提交**：`未提交`（工作区改动，待确认后提交）

### 背景

用户打开 MUMU 模拟器体验应用后，提出两个方向：① UI 朝 Play 商店 / Material 3 风格改进；② 允许用户自行选择界面色调。本批次为 preview 迭代，改动集中在 `MainActivity.java`（全部程序化 UI）。

### 已完成的改动（工作区，未提交）

1. **P0 三项基础优化**
   - 课程卡片阴影由 `elevation 4/11dp` 降为 `1/5dp`，更接近"细边框 + 浅影"的现代卡片。
   - 摘要行拆分为"主行（第X周 · 周X · N门课）" + "状态 chip（正在上/下一节）"，新增 `statusChip` 与 `updateStatusChip()`。
   - 空状态增加「＋ 添加课程」「导入 PDF 课表」两个 CTA 按钮（替代纯文字提示）。
2. **Play 商店风格化**
   - 课程卡片节次徽章改为 56/64dp 圆角方块（应用图标感），圆角 16/20dp。
   - 时间从 accent 大字改为 tonal chip（容器色小胶囊）。
   - 普通卡片圆角 18 → 24dp；日期 tab 选中从"实心紫药丸"改为 tonal 容器色 + accent 文字。
   - 新增 `tonalContainerColor()`（主色 10% 混入卡片色）。
3. **自定义界面色调（用户可选）**
   - 新增 `ACCENT_KEY`（SharedPreferences 持久化）与 7 组 `ACCENT_PRESETS`：蓝/青绿/绿/紫/橙/红/靛，各含浅色/深色两套值。
   - `accentColor()` 按预设 + 深浅模式返回；`todayColor()` 用 HSV 色相偏移（+42°）从主色派生，避免与 accent 混淆。
   - 设置面板新增「外观 → 界面色调」入口（`showAccentColorDialog()` + `renderAccentChoices()`），点击色块保存并 `recreate()` 全局生效。
   - 硬编码 accent 容器色（状态 chip、空状态导入按钮）统一改为 `accentContainerColor()`。
   - 新增 drawable `ic_palette_outline.xml`；同步更新 `tools/compile-stubs/R.java` 存根。

### 验证状态（已通过）

- `tools/verify-logic.ps1`：TimetableRulesTest + PdfCourseParserTest + Android 编译检查全过。
- `assembleDebug`：BUILD SUCCESSFUL；已安装到 MUMU（127.0.0.1:16384）。
- 无崩溃（logcat crash buffer 无本应用记录）。
- 自定义色调**像素级验证**：注入绿色预设（accent_preset=2）重启后，右上角添加按钮由 `#69B2FF`(105,178,255) 变为 `#6FD08A`(111,208,138)，日期 tab 选中区同步变绿，课程徽章不变；恢复默认后回蓝。
- 注意：MUMU 上 `input tap` 坐标与视图树有偏移（content 起点 y=42，且旋转坐标映射异常），自动化点击不可靠；`uiautomator dump` 因应用持续刷新拿不到 idle。后续如需 UI 自动化，需先解决这两点。

### 截图（build/emu-shots/）

- `preview-play-style.png`：Play 风格（有课程状态）
- `preview-empty-state.png`：空状态 CTA
- `preview-accent-green.png`：绿色色调效果
- `preview-restored-blue.png`：当前默认蓝（交付状态）

### 待办 / 下一步

- 等用户确认效果后提交本批次（建议拆两个 commit：UI 优化+Play 风格 / 自定义色调）。
- 可选继续调整：卡片圆角/徽章渐变、日期 tab 药丸化、header 按钮加文字标签、`toggleTheme` 图标随 accent 换色。
- 长期：`MainActivity` 其余职责（UI/校历/倒计时）尚未拆分。

---

## 2026-08-13 · 批次 5 后续（未提交）：UI 规范对齐与视觉改进

**Git 提交**：`未提交`（工作区改动，待确认后提交）

### 已完成改动

1. **修复节次徽章“节”字被裁**
   - 徽章高度由固定 56/64dp 改回 `WRAP_CONTENT`，垂直 padding 收窄到 8dp，保证“第 11 节”等两行内容完整显示。
2. **色板无障碍与按压反馈**
   - 新增 `ACCENT_PRESET_NAMES`、`COURSE_COLOR_NAMES`，为界面色调 7 个、课程颜色 10 个 swatch 补 `contentDescription`（选中态带“，已选”）。
   - 新增 `swatchStateListBackground()`，色板按下变深。
3. **空状态视觉与交互**
   - 空状态由“大号日期文字”改为“日历矢量图标（accent 容器色底板）+ 标题 + 日期副标题 + CTA”。
   - 两个 CTA 按钮补按压反馈：`addCta` 用 `interactiveButtonBackground`，`importCta` 用新增 `interactiveTranslucentBackground`（保留半透明容器色）。
4. **头部图标风格统一**
   - 主题切换按钮改用 outline 矢量图标 `ic_sun_outline` / `ic_moon_outline`，并用 `accentColor()` 动态着色，与设置/加号按钮风格一致。
   - 删除不再使用的 `ic_sun_10_white.xml`、`ic_moon_deep_blue.xml`。
5. **节次数字自适应缩放**
   - 跨节次（如 `11-12`）或长数字时自动缩小 `periodNum` 字号，避免横向被裁。

### 验证状态（已通过）

- `tools/verify-logic.ps1`：逻辑测试 + Android 编译检查全过。
- `gradlew assembleDebug`：BUILD SUCCESSFUL。
- MUMU 重装后启动无崩溃。
- 像素复核：header 旧深蓝月亮色（#123A63）像素数归零，accent 轮廓图标生效；徽章“节”字完整渲染。

### 待办 / 下一步

- 可继续：日期栏选中药丸化、卡片圆角/阴影层级统一、倒计时区域动效。
- MUMU `input tap` 坐标与画面存在旋转映射偏移，UI 自动化点击仍不可靠，后续需先解决。

### 补丁（2026-08-13，同日）

- 修复切换界面色调后色盘短暂残留：选中色块后先 `dialog.dismiss()` 再 `recreate()`，避免对话框在 Activity 重建过程中多显示一会。

### 风格方向（2026-08-13，同日）

- 为贴近原生 Android，将全应用字体从自定义 `DengXian` 改为系统原生字体栈 `sans-serif`（Roboto + Noto Sans CJK），方法重命名为 `appTypeface()`。
- 后续可选：Material 3 权重（标题/按钮用 `sans-serif-medium`）、给按钮补 ripple 按压波纹、统一 Material 形状与色板角色；iOS 风格不建议整体套用（平台不一致），但日期栏分段控件已具 iOS 观感。

### 原生 Android 化补丁（2026-08-13，同日）

- 交互：把 `interactiveSurfaceBackground` / `interactiveButtonBackground` / `interactiveGlassSurfaceBackground` 从自定义 `StateListDrawable` 换成框架 `RippleDrawable`，按钮/设置行/CTA 点击出现原生水波纹。
- 语义色：删除按钮、警告区、状态 chip 的散落硬编码红/灰改为 `dangerColor()`、`dangerContainerColor()`、`withAlpha(secondaryTextColor())` 等语义角色；移除失效的 `pressedSurfaceColor`。
- 组件语义（依赖说明）：`MaterialButton`/`CardView` 需要引入 `com.google.android.material:material` 与 AndroidX，而当前本地 Gradle 缓存无该依赖且环境离线，故本轮未引入；已用框架 Ripple + 语义色达到近似原生 Material 效果。

### Material 组件化（2026-08-13，同日）

- 引入依赖 `com.google.android.material:material:1.12.0`，并开启 `android.useAndroidX=true`。
- 主题从框架 `Theme.Material.*` 迁移到 `Theme.Material3.Light/Dark.NoActionBar`（values / values-night / v27 / night-v27）。
- `MainActivity` 由 `android.app.Activity` 迁移到 `androidx.appcompat.app.AppCompatActivity`。
- 设置面板动作按钮由 `Button` 改为 `MaterialButton`（cornerRadius / stroke / ripple / inset）。
- 空状态卡片由普通 `LinearLayout` 改为 `MaterialCardView`（cardBackgroundColor / radius / elevation / stroke）。
- 为离线 `verify-logic.ps1` 增加 `AppCompatActivity`、`MaterialButton`、`MaterialCardView` 的 compile-stub，保证离线编译检查仍可运行。
- 验证：`verify-logic.ps1` 通过，`assembleDebug` BUILD SUCCESSFUL，MUMU 重装启动无崩溃。

---

## 交接备忘（给下一个会话 / 明早继续）

**状态**：本会话所有改动都还在**工作区未提交**。基线是之前 4 个 batch 的 git 提交，UI / Material 相关改动均未 commit。

### 本会话已完成（可据此继续）
1. 修复节次徽章“节”字被裁（高度 WRAP_CONTENT + padding 8）。
2. 色板无障碍标签 + 按压态。
3. 空状态升级：日历图标 + 标题 + 日期副标题 + 两个 CTA。
4. 头部主题图标 outline + accent 着色。
5. 节次数字自适应缩放。
6. 全应用字体改系统原生 `sans-serif`（`appTypeface()`）。
7. 交互背景改 `RippleDrawable`（涟漪按压）。
8. 语义色整理（`dangerColor()` / `dangerContainerColor()` 等）。
9. 引入 Material 1.12.0 + AndroidX：`AppCompatActivity` + `Theme.Material3.*`。
10. 设置按钮 `MaterialButton`、空状态卡片 `MaterialCardView`。

### 明早继续的优先项
- 课程卡片换 `MaterialCardView`：注意 `createCourseCard` 现在用自定义 `BoundedFrameLayout` + `CourseProgressView` + foreground，替换时必须保留“进行中”进度层。
- 对话框换 `MaterialAlertDialog`。
- 继续语义色统一：`ClassReminderReceiver` 里的硬编码通知蓝可改为共享 accent。
- 可选：日期栏选中态药丸化、倒计时区域动效。

### 验证命令
- 快速编译 + 逻辑测试：
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-logic.ps1`
- 构建（首次需联网，之后可离线）：
  `$env:JAVA_HOME='<JDK 目录>'; $env:ANDROID_HOME='<Android SDK 目录>'; $env:ANDROID_SDK_ROOT='<Android SDK 目录>'; $env:GRADLE_USER_HOME='<Gradle 用户目录>'; .\gradlew.bat --no-daemon assembleDebug`
- 离线构建加 `--offline`。

### 注意事项
- MUMU（`127.0.0.1:16384`）的 `input tap` 坐标与截图有旋转映射偏移，自动化点击不可靠；`uiautomator dump` 常因应用持续刷新拿不到 idle。
- 已给 `tools/compile-stubs` 增加 androidx / material 的 compile-stub；若 MainActivity 再引用新的 Material 类，需同步补 stub，否则 `verify-logic.ps1` 离线编译检查会失败。
- 全部改动未提交，建议确认效果后按主题拆分 commit。

---

## 2026-08-13 · 课程卡片 MaterialCardView 化 / 对话框 MaterialAlertDialog / 通知色共享

**Git 提交**：`未提交`（工作区改动，随批次 5 一并提交）

### 已完成的改动

1. **课程卡片换 MaterialCardView**
   - 自定义 `BoundedFrameLayout` 升级为 `BoundedMaterialCardView`（`MaterialCardView` 子类），保留 `onMeasure` 最大高度封顶逻辑；"进行中"进度层 `CourseProgressView` 仍作为子视图叠加，`COMPLETED` 状态不添加。
   - 圆角/阴影/描边改用 Material API：`setRadius(28/24dp)`、`setCardElevation(5/1dp)`、`setStrokeWidth(1dp)`、`setStrokeColor`（进行中为半透明白，普通卡片为卡片色混合课程色）。
   - 卡片背景统一走语义色：进行中用课程色实底，普通卡片为卡片色 + 课程色混色（深色 8% / 浅色 4%）。
2. **对话框换 MaterialAlertDialogBuilder**
   - 全部对话框构造改为 `MaterialAlertDialogBuilder`，适配 `Theme.Material3`。
   - import 由 `android.app.AlertDialog` 切换为 `androidx.appcompat.app.AlertDialog`。
   - 保留 `styleDialogWindow` 对确定/取消按钮的 accent 着色与字重设置。
3. **通知色共享 accent**
   - `ClassReminderReceiver` 通知 `setColor` 由硬编码 `Color.rgb(37, 99, 235)` 改为 `MainActivity.accentColorFor(context)`，跟随用户选择的界面色调（浅色/深色各自取值）。
4. **补离线编译 stub**
   - `tools/compile-stubs` 增加 `androidx.appcompat.app.AlertDialog` 与 `com.google.android.material.dialog.MaterialAlertDialogBuilder`。

### 验证状态（已通过）

- `verify-logic.ps1`：逻辑测试 + 全量 Android Java 编译检查通过。
- `gradlew.bat --no-daemon --offline assembleDebug`：BUILD SUCCESSFUL。

### 待办 / 下一步

- 连同批次 5 其余 UI 改动，确认效果后按主题拆分 commit。

---

## 2026-08-13 · APP 图标（adaptive icon 完整化 + legacy 密度适配）

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

1. **manifest 图标引用修正**
   - `AndroidManifest.xml` 的 `icon` / `roundIcon` 由 `@drawable/ic_launcher_foreground` 改回 `@mipmap/ic_launcher` / `@mipmap/ic_launcher_round`，adaptive icon 完整生效（含圆形图标）。
2. **重绘 `ic_launcher_foreground.xml`**
   - 前景重新设计：深青铃铛 ×2 + 米白日历卡片 + 黄色日历头 + 青色表格行，全部矢量 path 手绘。
3. **重绘 `ic_launcher_background.xml`**
   - 纯色背景与前景协调。
4. **补充 legacy PNG**
   - 新增 `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher(.round).png`（48/72/96/144/192），覆盖 API 23-25。
5. **验证产物**
   - `assembleDebug` BUILD SUCCESSFUL；生成 `artifacts/icon-preview.png` 与 `artifacts/icon-preview-round.png` 预览图。

---

## 2026-08-13 · 对话框窗口样式统一

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 修复界面色调切换后对话框短暂残留：选中色块后先 `dialog.dismiss()` 再 `recreate()`（批次 5 补丁）。
- `styleDialogWindow` 与 MaterialAlertDialog 的 Material3 按钮样式对齐，去掉自定义 `dp(2)` 边距带来的观感不一致。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL，MUMU 实测对话框样式与页面主题一致。

---

## 2026-08-13 · 节次设置对话框滚动优化

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- `showPeriodSettingsDialog` 的规则列表放入 `ScrollView`，高度上限约 `dp(300)` 并以 `weight=1` 占满剩余空间，规则多时不再溢出屏幕。
- 节次按钮（恢复默认 1-11 节等）统一为 MaterialButton 50dp 高度。
- MUMU 上 uiautomator 校验对话框 bounds（88px 高度对齐）。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL。

---

## 2026-08-13 · 界面元素对齐微调

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 修复紧凑宽度下元素对齐：uiautomator 实测某行 y 坐标由 693 调整到 714（约 21px ≈ 12dp），使与相邻元素间距一致。
- 间距统一为 12dp 级。

### 验证状态（已通过）

- `assembleDebug` BUILD SUCCESSFUL。

---

## 2026-08-13 · 通知总开关（SwitchCompat）

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 设置面板新增「课程提醒」总开关，使用 `androidx.appcompat.widget.SwitchCompat`。
- 关闭（`notifications_enabled=false`）：先 `cancelScheduledClassReminders` 取消已排闹钟，再 `ReminderScheduler.clear` 清空提醒计划，并清除 `REMINDER_CODES_KEY` 记录；开启时重新 `scheduleClassReminderNotifications` 排程。
- `scheduleClassReminderNotifications` 同步刷新设置里的 `notificationStatusText`。
- 补离线编译 stub：`androidx.appcompat.widget.SwitchCompat`。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL，MUMU 实测开关 true/false 生效。

---

## 2026-08-13 · MUMU 旋转/分辨率适配（compact 宽度）

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- MUMU 模拟器处于 `rotation=1`（横屏），通过 adb `wm size 1080x720` 使宽度 411dp，落入紧凑模式（compact）。
- 为 compact 模式收紧卡片内边距/字号/徽章尺寸，按钮高度 88px 级对齐。
- 发现 MaterialAlertDialog 在 `values-night` 下按系统深色渲染，但应用 `isDarkMode=false` 时对话框与页面主题不一致（见下一条）。

---

## 2026-08-13 · 修正 App 深色模式与对话框主题不一致

**Git 提交**：`未提交`（工作区改动）

### 已完成的改动

- 根因：`values-night` 资源按系统深色生效，而应用强制浅色时 `isDarkMode=false`，MaterialAlertDialog 仍取深色资源。
- 修复：`onCreate` 中调用 `AppCompatDelegate.setDefaultNightMode(...)`，将 `theme_mode`（0=跟随系统 / 1=MODE_NIGHT_NO / 2=MODE_NIGHT_YES）同步给 AppCompat，使对话框与应用主题一致。
- 补离线编译 stub：`androidx.appcompat.app.AppCompatDelegate`。

### 验证状态（已通过）

- `verify-logic.ps1` 与 `assembleDebug` BUILD SUCCESSFUL，MUMU 实测背景色由深色 `(43,41,48)` 切回浅色 `(~238-248)`。

---

## 2026-08-13 · 移除"校历在线导入"功能（v1.2 / versionCode 3）

**Git 提交**：`未提交`（工作区改动）

### 动机

- 在线导入实为"联网验证页面标题 + 返回内置常量日期"的半假功能（见早期分析报告 #4），换学期即失效；
- 移除后应用可彻底去掉 `INTERNET` 权限，回到完全离线，与 README 安全定位一致（早期分析报告 #3 的根因消除）。

### 已完成的改动

- `MainActivity.java`：删除校历对话框 URL 输入与"在线导入"按钮；删除 `importAcademicCalendarOnline`/`fetchAcademicCalendar`/`decodeCalendarText`/`parseAcademicCalendar`/`inferCalendarName`/`applyAcademicCalendar`/`AcademicCalendar`；删除 `DEFAULT_CALENDAR_URL`/`CALENDAR_YEAR_PATTERN`/`MAX_CALENDAR_BYTES` 与 `java.net.*`/`Charset` 导入。PDF 导入共用的 `readWithLimit`/`ImportException` 保留。
- 删除 `CalendarDateExtractor.java` 与 `tools/CalendarDateExtractorTest.java`（移入回收站）。
- `AndroidManifest.xml`：移除 `INTERNET` 权限（保留 `usesCleartextTraffic="false"` 作为纵深防御）。
- `tools/security-audit.ps1`：断言反转——禁止 INTERNET 权限、禁止 Java 源码出现任何 http(s) URL 与网络类；`tools/verify-logic.ps1`：移除对应编译/测试条目。
- README：删"校历在线导入"章节，新增"校历设置"说明与安全声明更新。
- 版本：`versionCode 1 → 3`，`versionName 1.0 → 1.2`（git HEAD 基线为 1 / 1.0）。

### 验证状态

- 校历仅支持手动设置：设置 → 校历设置 → 填写开学日/散学日。

---

## 2026-08-31 · PDF 跨页课程名解析修复（v1.2.1 / versionCode 4）

**Git 提交**：`未提交`（工作区改动）

### 改动

- `PdfCourseParser`：课程跨 PDF 分页时（课程名留在上一页底部、节次元数据出现在下一页顶部，中间夹杂其他列文本），原逻辑遇到非课程名行即 `break` 导致课程名关联失败；改为跳过干扰行、沿同一列/行轴继续回溯（`continue`），仅轴向不匹配时才中断。
- `tools/PdfCourseParserTest.java`：新增跨页关联回归用例。
- 新增命令行复现工具 `tools/ReproRun.java`（未入库；读取样例 PDF 输出解析结果与 debugLog）。
- 版本：`versionCode 3 → 4`，`versionName 1.2 → 1.2.1`。

### 验证状态

- `PdfCourseParserTest` 全部通过；`verify-logic.ps1` 通过。

---

## 2026-09-05 · 周切换浏览与状态保留（v1.2.2 / versionCode 5）

**Git 提交**：`未提交`（工作区改动）

### 改动

- 周切换浏览：主界面顶部新增周导航条（`‹ 第N周 · 日期范围 › [回到本周]`），支持浏览任意教学周课表；点击周标签打开周选择对话框；"回到本周"恢复跟随真实当前周。
- 状态保留：新增 `onSaveInstanceState`，主题切换 `recreate()` / 进程重建后保留当前查看的星期与周次（`viewingWeek`），修复切换后视图跳变到其它星期。
- 切换星期/周后自动滚动使选中星期进入视野（`scrollSelectedDayIntoView`）。
- 校历引导：首次运行且未设置校历时主动弹出校历设置对话框（`maybePromptCalendarSetup` / `showCalendarImportDialog(firstRun)`），并兼容检测旧版校历数据（`containsLegacyCalendar`）。
- 强调色选择面板重构（`renderAccentChoices`）；状态标签统一为 `updateStatusChip`（区分预览周）。
- README：新增"周切换浏览"功能点与说明。
- 版本：`versionCode 4 → 5`，`versionName 1.2.1 → 1.2.2`。

### 验证状态

- `security-audit.ps1`、`verify-logic.ps1`、`assembleDebug`、`assembleRelease` 全部通过（2026-09-07 全面检测复核）。

---

## 2026-09-07 · Lint 清理：adaptive icon monochrome 层 + legacy 图标圆角

### 改动

- 新增 `drawable/ic_launcher_monochrome.xml`：单色剪影（挂环 + 日历主体，内部三条线以 `fillType="evenOdd"` 挖空），供 Android 13+ 主题图标使用。
- `mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml` 补 `<monochrome>` 标签，消除 2 个 `MonochromeLauncherIcon` 警告。
- `mipmap-mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi` 五个方形 `ic_launcher.png` 做圆角透明处理（半径 = 边长 25%，抗锯齿），消除 5 个 `IconLauncherShape` 警告；风格与圆形版图标一致。
- CHANGELOG：修正 v1.2 条目版本行为 `1 → 3`（git HEAD 基线为 1 / 1.0），并补 v1.2.1 / v1.2.2 两条记录与本条目。

### 验证状态

- `lintDebug`：0 errors / 0 warnings（原 0 errors / 7 warnings）；`security-audit.ps1`、`verify-logic.ps1`、`assembleDebug`、`assembleRelease` 全部通过。
