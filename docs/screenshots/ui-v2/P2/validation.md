# P2 下一节卡验收记录

状态：实现完成、待用户确认，P2 未提交。P1 已确认并提交为 9e180db。

## 实现

- 下一节卡移至 Hero 下方，使用完整页面内容宽度；保留人物面部与头饰主体。
- 顶行显示状态及倒计时；课程名独立一行；时间/地点、教师/周次/节次分别成行。
- 跨日课程日期独立一行，不显示跨日分钟倒计时；使用实际日期比较，支持下周同一天。
- 今日课程全部结束时保留该状态，有未来课程则同时显示未来信息；无未来课程清空详情。
- 预览周显示第 N 周，隐藏实时信息，点击卡片返回本周。
- 未开学显示首课日期及信息；学期结束不残留课程及倒计时。
- 复用 currentCourseInProgress / nextUpcomingCourseFromToday / 既有自然日和课程状态 helper，未改课表存储、PDF 导入或提醒规则。
- 倒计时通过既有定时 UI 刷新更新；从课中/下一节切入无课、预览、学期结束状态时同时清空文字及隐藏详情。
- Chevron 替换 Pointer；clock/calendar/Chevron 统一 1.8 线宽，原有 location/person 图形保留。

## 验证

- 新增八种状态测试先验证旧版本失败：device-red.txt 保留证据。
- 初步通过后将状态测试加强为完整 render → 原有课程查找 → 卡片展示链路；发现“今日结束但仍有未来课程”的测试夹具预期过窄，按已批准的 D 状态规则修正预期，并追加无未来课程独立场景。
- 完整真机：25 passed / 0 failed / 0 skipped；原有 15 项 + 八种状态 + 今日结束且无未来课程 + 预览卡返回本周。
- 八种状态截图测试：8 passed / 0 failed / 0 skipped。
- host regression、Android Java compile check、安全审计、Gradle Debug/AndroidTest 构建及 git diff --check 全部通过。
- 354dp 与约 411dp 首页截图检查通过；411dp 测试后已恢复原始 560dpi、字体 1.0。
- 尚未进行最终全部宽度/大字体/对比度矩阵，按 P8.5/P9 执行；未更改 P5/P6 的原课程卡信息与插画重叠问题。
- 所有真机夹具及权限验证只针对独立 .validation 应用；未修改正式应用数据。

## 首页

[354dp 首页](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/baseline_home.png)

[411dp 首页](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/411dp/baseline_home.png)

## 八种状态截图

| 状态 | 截图 |
|---|---|
| A 正在上课 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-A.png) |
| B 今天还有下一节 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-B.png) |
| C 下一节在未来日期（特意覆盖下周同一天） | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-C.png) |
| D 今日课程已结束且有未来课程 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-D.png) |
| E 完全没有后续课程 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-E.png) |
| F 预览第 8 周 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-F.png) |
| G 未开学 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-G.png) |
| H 学期已结束 | [查看](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P2/matrix/status-H.png) |

八种状态使用验证用例构造的场景，课程时间相对设备当前时刻生成，不是 P0 的固定三门课程，也不用于 P6 Alpha 比较。截图时间和结果见 status-matrix.txt；两种宽度的固定三门课程仍均为前两门结束、第三门上课中。所有截图基于实际设备时钟。
