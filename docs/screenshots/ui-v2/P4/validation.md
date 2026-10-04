# P4 星期与日期条验证

状态：实现与验证完成，待用户截图确认；P4 尚未提交。P3 已确认并提交为 90a9e99。

## 改动

- 七个星期标签下新增 M/d 日期，全部调用 P3 的 displayedDateMillis(week, day)。
- 保留七天横向滑动、点击选日、滑块动画及选中居中。
- “今天”颜色/圆点/无障碍描述以真实自然日与显示日期一致为条件；预览其他周、未开学及假期最后周不会仅因星期相同而标记今天。
- 标签改为最小高度 64/68dp、实际高度 WRAP_CONTENT，给大字体留出空间；日期 12sp，未通过缩小字体硬塞。
- 无障碍描述包含星期、完整日期、今天及选中状态。
- 仅修改星期栏展示及测试，未改课程、日期业务算法、存储、提醒或导入规则。

## 验证

- 新增验证先捕捉旧版本失败：缺少日期行、预览/学期外误标今天，见 device-red.txt。
- verify-logic.ps1 通过，含全部规则/PDF/Activity 回归与 Android Java 编译检查；Activity 回归仍为 19 项。
- Gradle Debug 与 AndroidTest 构建通过。
- 完整真机：32 passed / 0 failed / 0 skipped。
- 验证首页和星期日期一致、跨月、跨年、春秋 DST、学期首末周、预览/学期外今天标记，以及真实点击周二后顶部变为 9月29日。
- 八状态截图检查：8 passed / 0 failed / 0 skipped。
- 实际约 354dp 设备上检查 100% 与 130% 字体，星期和日期行无裁切；字体测试结束恢复 font_scale=1.0，显示密度保持 560dpi。
- 安全审计及 git diff --check 通过。
- 360/393/411dp 与 115% 的最终完整组合按 P8.5/P9 执行，不声称本阶段覆盖全部组合。

## 后续待办

130% 字体截图暴露了原课程时间轴固定宽度下时间文字裁切，以及地点省略和插画重叠。P4 未修改该区域，纳入 P5 窄屏降级与 P6 插画比较；最终验收前必须处理。

## 截图

[100% 字体首页](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P4/354dp/baseline_home.png)

[130% 字体首页](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P4/home-font130.png)

[预览周：不同日期、不标今天](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P4/354dp/status-F.png)

[未开学](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P4/354dp/status-G.png)

[假期](C:/Users/sjtxd/Documents/Codex/2026-10-04/ni-2/outputs/ui-v2/P4/354dp/status-H.png)

截图采用真实手机时间，不作为 P6 固定时间 Alpha 比较材料。
