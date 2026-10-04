# 1.3.0-preview 发布记录

2026-10-04，项目展示名更新为“东雪莲定制课表”，GitHub 仓库路径更新为 `YUYU-Fish2/DongXuelian-Custom-Timetable`。应用 ID、课程存储字段和 Keystore 别名沿用原值。

## 安装包体积

| 文件 | 大小（十进制 MB） |
| --- | ---: |
| 上一版 UI v2 Debug APK | 13.47 |
| 当前优化后的 Preview APK | 约 6.63 |
| 当前 APK 的 ZIP 下载包 | 约 6.09 |

APK 体积减少约 51%。Preview 采用 R8 代码压缩和资源裁剪，关闭调试与测试课程入口。五张大图从 PNG 转为无损 WebP，合计由 7,531,052 字节降到 5,155,480 字节；尺寸、RGBA 像素和透明像素中的 RGB 数据逐字节一致。编译后的 APK 也保留相同的 WebP 文件内容。

ZIP 内的 APK 与单独下载的 APK 完全一致，不修改 APK 签名。附件 `SHA256SUMS.txt` 用于检查下载文件。

## 本次验证

- Preview 和 Debug 使用实际 Android 依赖构建成功。
- 教学周、PDF 和日期边界回归通过，Activity 逻辑回归 21 项通过，Android Java 编译检查通过。
- Preview Lint：0 个错误，9 条既有警告；静态安全审计通过。
- 核对 APK 的应用名、包名、版本号、权限、签名、图片内容及 ZIP 内文件的一致性。
- 签名证书与上一版 UI v2 Debug APK 相同：`04108283c3f4e97e31daf036cf7552af446db141bedd2bcf4e1e365d82960949`。

按用户要求，本次跳过 USB 和真机检查，未复测压缩后运行或覆盖安装。此前各阶段的设备测试仍保留在原记录中，不能视为这个发布包的新验证结果。

## 仓库整理

README 更新下载、当前截图和使用说明；开发与历史验证集中到文档索引，补充问题反馈和 Pull Request 模板。APK、压缩包和构建报告统一放在 Releases。

旧的 1.2.2 APK 已原样保存在 [历史存档](https://github.com/YUYU-Fish2/DongXuelian-Custom-Timetable/releases/tag/v1.2.2-archive)，上传后的大小与 SHA-256 已核对。Git 历史、旧标签和已有 UI v2 发布保留。
