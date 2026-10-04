# 开发说明

本项目使用 Java 原生 Android 界面和 Material 组件，布局由 Java 动态构建。项目展示名为“东雪莲定制课表”，应用 ID 和 Java 包名继续使用 `com.example.meinstundenplan`，存储字段、Keystore 别名与签名配置标识也沿用原值。

## 环境

| 组件 | 配置 |
| --- | --- |
| JDK | 17 |
| Android Gradle Plugin | 8.13.0 |
| Gradle Wrapper | 9.2.0 |
| compileSdk | Android 36，minor API 1（SDK 目录 `android-36.1`） |
| targetSdk / minSdk | 36 / 23 |
| Build Tools | 36.1.0 |

Android Studio 可直接打开仓库根目录。本机 SDK 路径放在 `local.properties` 或环境变量中，凭据和本机路径不要提交到 Git。

```powershell
$env:JAVA_HOME = "<JDK 目录>"
$env:ANDROID_HOME = "<Android SDK 目录>"
```

第一次构建需要下载 Wrapper 与依赖。缓存完整后可使用 Gradle 的 `--offline` 参数。

## 构建与 APK 打包

| 类型 | 用途 | 构建命令 | 默认 APK 路径 |
| --- | --- | --- | --- |
| Preview | 试用版；R8 代码压缩、资源裁剪、调试证书签名，关闭测试课程入口 | `:app:assemblePreview` | `app/build/outputs/apk/preview/app-preview.apk` |
| Debug | 开发调试；保留测试课程入口 | `:app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk` |
| Release | 正式发行配置；需另行提供发布签名 | `:app:assembleRelease` | `app/build/outputs/apk/release/` |

例如构建并打包 Preview：

```powershell
.\gradlew.bat --no-daemon :app:assemblePreview
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\package-apk.ps1
```

APK 打包脚本读取本机构建结果，默认输出到 `dist/apk-v1.3.1/`，包含 `dongxuelian-timetable-v1.3.1-preview.apk`、其 ZIP 压缩包和 `SHA256SUMS.txt`。ZIP 还附有安装说明，安装时使用解压后的 APK。

Preview 使用本机 Android 调试证书。其他机器默认生成的调试证书可能不同；同包名的覆盖安装仍需使用同一证书。Release 未配置签名时会生成 `app-release-unsigned.apk`，不能直接安装。不要将未签名文件当作可安装发行包。

`tools/package-release.ps1` 将已提交的干净工作区归档为完整源码 ZIP，包含文档与截图。存在未提交或未跟踪文件时会拒绝运行；已跟踪的密钥、本机配置、APK、构建缓存和 PDF 也会阻止打包。默认输出为 `dist/dongxuelian-timetable-v1.3.1-source.zip`。

## 验证

### 本地逻辑、构建和审计

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-logic.ps1
.\gradlew.bat --no-daemon :app:lintPreview :app:assemblePreview
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\security-audit.ps1
git diff --check
```

`verify-logic.ps1` 包含教学周、节次、PDF 解码和 Activity 逻辑回归，并用 `android.jar` 编译 Java 源码。该编译检查对 Material/AppCompat 使用本地编译桩，不能代替真实依赖的 Gradle 构建。

Activity 回归从实际 Java 方法和导入循环生成 JVM 测试壳，验证日期、夏令时、导入冲突及失效提醒过滤。它替换了 Android 系统和持久化边界，因此 Keystore、通知和布局仍需真机验证。

`tools/build-verify.ps1` 保留 `clean + lintRelease + assembleRelease` 检查流程。需要验证 Preview 时使用上面的 Preview 任务。

### 真机

连接已授权 USB 调试的手机，保持解锁，然后执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\verify-device.ps1 `
  -Jdk "<JDK 目录>" -Sdk "<Android SDK 目录>"
```

脚本通过 `tools/device-validation.init.gradle` 构建独立包 `com.example.meinstundenplan.validation`，测试包为 `.validation.test`，避免覆盖日常使用的应用。多设备连接时增加 `-Serial "<设备序列号>"`。安装和权限提示需要在手机上处理；权限导致的跳过须保留在报告中。

验证包使用固定测试课程与时间环境，不能拿它的测试结果代替 Preview 的签名、压缩后行为或升级验证。字体和宽度矩阵使用密度调整模拟时，也要记录原始分辨率、密度、字体比例并在结束后恢复。

历史验证范围、截图和结果见 [文档索引](README.md)。修改涉及存储、提醒、日期或导入规则时，优先重跑对应回归；发布前检查实际要分发的 APK。

## 发布签名

Release 签名入口保留 `MEIN_*` 名称，以兼容原有配置。环境变量优先于 properties：

```text
MEIN_STORE_FILE
MEIN_STORE_PASSWORD
MEIN_KEY_ALIAS
MEIN_KEY_PASSWORD
```

本地也可填写 `secrets/release-signing.properties`，模板在同目录。用户级配置路径为 `~/.gradle/mein-stundenplan-signing.properties`；两个 properties 文件同时存在时，当前实现后读取的用户级文件会覆盖同名值。

配置的路径按 `app/build.gradle` 解析，签名文件和密码均不进入版本库。升级包应复用既有发行证书，不能用新证书覆盖相同包名的已安装应用。

## 代码结构

```text
app/src/main/java/com/example/meinstundenplan/
  MainActivity.java           首页、编辑、设置、校历与交互
  TimetableRules.java         教学周、节次与时间规则
  PdfCourseParser.java        PDF 文本和课程提取
  SecureStorage.java          Android Keystore + AES/GCM
  ReminderScheduler.java     提醒排程、续排与权限降级
  ClassReminderReceiver.java 提醒触发和通知
  ReminderBootReceiver.java  开机与升级后的提醒重排
  ImportException.java       导入错误
app/src/main/res/            图标、水彩素材、字符串和主题
app/src/androidTest/         真机验证运行器与回归夹具
tools/                       构建、校验、审计和打包脚本
docs/                        样式规范、计划、截图与历史验证
secrets/                     签名模板；实际凭据不提交
```

日期展示统一经 `displayedDateMillis(week, day)` 调用已有自然日规则，不能通过固定 24 小时毫秒偏移计算。课程数据结构、颜色索引、提醒代际号和 PDF 冲突规则的改动应与纯视觉调整分开。
