# 1.3.1-preview 发布记录

2026-10-05，本版更新桌面图标，应用名仍为“东雪莲定制课表”。

## 图标

使用用户提供的蓝白水彩课表插画，保留日历、花朵和右下角人物。原图以无损 WebP 保存于 `docs/assets/launcher-icon-source.webp`，像素与提供的 PNG 一致；应用使用 512×512 的缩放版本。

自适应图标采用白色背景，将插画放在 108dp 图层中央的 66dp 区域，给桌面形状裁剪留出空间。旧版桌面补齐 mdpi 至 xxxhdpi 的圆角与圆形 PNG。支持主题图标的桌面使用日历勾选轮廓。

![圆角和圆形图标预览](assets/launcher-icon-preview.png)

## 构建与验证

- 版本：1.3.1-preview，versionCode 7；包名沿用 `com.example.meinstundenplan`。
- Preview 构建成功，继续启用 R8 和资源裁剪，关闭调试与测试课程入口。
- 逻辑回归通过，包括 Activity 的 21 项用例；Android Java 编译检查通过。
- Preview Lint：0 个错误、9 条既有警告。
- 核对五档图标尺寸，确认 APK 内插画与资源 WebP 字节一致。
- 安装包签名验证通过，证书 SHA-256 与上一版 Preview 相同：`04108283c3f4e97e31daf036cf7552af446db141bedd2bcf4e1e365d82960949`。
- APK 约 7.18 MB，ZIP 约 6.64 MB；ZIP 内 APK 与独立 APK 一致，下载附件包含 SHA-256 校验值。

按用户要求，本次跳过 USB 和真机验证。上述图标预览为离线裁剪预览，不是真机桌面截图。

原始素材由用户提供；仓库不据此声明新增素材的独立授权。
