# P0 基线记录

- 分支：ui/v2-refresh；正式 UI 基线：57d827c8201c0af0decc8924e325df19c1acc3a2。
- 正式应用代码及资源未修改，仅增加独立验证包中的截图夹具入口。
- 设备：RMX3700，Android 16 / API 36，ADB serial ac6c0ae8。
- 分辨率：1240×2772；密度：560dpi；逻辑宽度约 354.3dp；字体比例：1.0。
- 主色：accent_preset=0；浅色界面。首页课程列表位于顶部，选中周日，查看真实第 1 周。
- 学期：2026-09-28 起，结束日期通过 Calendar.add(WEEK_OF_YEAR,17) 生成。
- 三门课程均周日、1–17周、教师罗志坚；08:00–09:40 离散数学（1–2节、锡科503）；10:00–11:40 数据结构（3–4节、锡科503）；14:30–16:10 数据结构（5–6节、锡科301）。使用 customStart/EndMinutes 明确全段时间。
- 冻结图片拍摄于 2026-10-04 15:24:55–15:24:58，Asia/Shanghai。前两门已结束，第三门正在上课。时间截图记录的是实际时钟，未修改手机全局时间；尚未加入受控取时机制，因此不得将本次截图工具直接用于要求时间完全一致的 P6 比较。P6 前需完成仅验证环境启用的受控取时支持。
- baseline_home.png、baseline_settings.png、baseline_course_edit.png 位于 ui-baseline/。编辑页是第二门课程，顶部滚动位置；教师字段可滚动查看。截图后取消编辑，未保存编辑操作。

## 本次验证

- verify-logic.ps1：TimetableRules、PDF、五类 BugRegression 与 15 项 ActivityLogicRegression 通过；Android Java compile check passed。
- security-audit.ps1：通过。
- Debug app / AndroidTest：Gradle 构建通过。
- 原始验证及新增截图夹具后的完整真机验证：15 passed / 0 failed / 0 skipped。
- 截图夹具：3 张截图生成通过，仅运行于 .validation 包，正式应用数据未修改。
- git diff --check：通过。

## 已观察的基线问题

首页摘要时间截断、已结束课程辅助文字偏淡、插画与信息重叠。分别安排在 P2/P5/P6，不在 P0 改动。

## 阶段状态

已保存逻辑检查和视觉基线，待用户确认基线。尚未 Commit，也未进入 P1。
