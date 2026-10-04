# P3 顶部日期与独立周范围

状态：实现及验证完成，待用户确认；P3 尚未提交。P2 已确认，提交 6c06c9f。

## 实现

- 顶部改为“第 N 周 · 星期 · M月d日”，移除该处课程数量，文字使用主文字色及 medium 字重，保持单行。
- 日期基于选中日和查看周，预览周切换时同步更新，不固定展示真实今天。
- 周导航仅展示 M.d–M.d；保留原周次选择、前后周、返回本周交互。无障碍描述仍包含第 N 周。
- 新增统一 private displayedDateMillis(week, day)，内部调用 courseDateMillisForWeek(day, week)，由既有 TimetableRules 计算日期。
- 顶部日期及新的 displayedWeekRangeLabel 都使用此入口。后续 P4/P8 复用此入口，不再自行计算日期。
- 原 weekRangeLabel 保留以维护既有回归覆盖，不再作为周导航 UI 展示入口；本阶段不改星期栏，按 P4 加日期。
- 未开学及假期标题语义保留；此时日期随实际查看的第一/最后教学周和选中日展示。
- 未修改课表业务、存储、PDF 导入及提醒。

## 验证

- 新 helper 实现前，新增 host 用例失败记录：host-red.txt。
- host regression 通过：ActivityLogicRegression 由 15 项增至 19 项；额外检查春/秋 DST、跨月、跨年和第 17 周日期；原有全部回归与 Android Java 编译检查通过。
- Gradle assembleDebug / assembleDebugAndroidTest：通过。
- 完整真机：30 passed / 0 failed / 0 skipped。
- 新增真机日期场景：跨月、跨年、春 DST、秋 DST、未开学/假期状态。时区变更仅在验证进程，finally 恢复，不修改手机系统时区。
- 真机状态矩阵截图：8 passed / 0 failed / 0 skipped，包含当前周、预览第 8 周、未开学与假期。
- 354dp 和约 411dp 首页均已截图并检查，顶部日期无截断。字体 1.0；411dp 检查后恢复 560dpi。
- Security audit 与 git diff --check：通过。
- 全部宽度/动态字体/对比度终验仍按 P8.5/P9 完成。

## 截图

[354dp 首页](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P3/354dp/baseline_home.png)

[411dp 首页](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P3/home-411dp.png)

[预览第 8 周](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P3/354dp/status-F.png)

[未开学](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P3/354dp/status-G.png)

[假期](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P3/354dp/status-H.png)
