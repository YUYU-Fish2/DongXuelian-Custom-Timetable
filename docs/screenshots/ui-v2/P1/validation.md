# P1 Hero 与设置/加号图标

状态：实现与验证完成，待用户截图确认；P1 未提交。
P0 已获用户确认，提交 8582c36。

## 改动
- Hero 高度上限 150dp，屏幕高度比例 19%。
- sceneScale 初始值 1.22，当前保留此值；允许范围 1.18–1.26。
- 设置 sliders 线宽 1.8、圆头圆角、线长错开，旋钮半径 1.9→1.65（缩小约 13%）。
- 加号线宽 2.4→2.1。两组图标中性黑色源，由现有 View tint 着色。
- 仅修改 MainActivity 中 Hero 参数及两个 vector 文件，未修改业务规则、下一节卡、课程卡、提醒或存储。

## 验证
- verify-logic.ps1 全部通过，包含 15 项 ActivityLogicRegression 与 Android Java compile check。
- Gradle assembleDebug / assembleDebugAndroidTest 成功。
- 354dp 和约 411dp 两组真机截图生成成功；检查双眼及头饰主体完整，脸部不被按钮遮挡，Hero 底部渐隐无硬边。
- 状态栏图标目前可辨认；最终对比度和系统栏验收仍按 P8.5 执行。
- 原设备密度 560dpi，411dp 测试临时设为 483dpi；测试结束已 wm density reset，确认恢复 560dpi；字体仍 1.0。
- git diff --check 通过。
- 本阶段按计划运行 host regression，未声称重新执行完整 15 项设备回归；该完整回归已在 P0 执行。

## 截图
- ui-baseline/baseline_home.png：354dp，2026-10-04 15:29:08。
- 411dp/baseline_home.png：约 411dp，2026-10-04 15:29:41。
- 两组均使用 P0 固定三门课程、选中周日、preset=0、列表顶部；拍摄时均前两门已结束、第三门上课中。使用真实时钟，倒计时有少量差异，不作为 P6 Alpha 比较材料。
- 原有 354dp 下一节时间截断及课程信息/插画重叠保留，按 P2/P5/P6 分别处理。
