# 最新执行授权：自行视觉验收

用户取消逐阶段人工确认，授权代理对照 docs/screenshots/ui-v2/reference.png 自行验收并继续 P5–P9。保留逐阶段编译、回归、真机截图和独立 Commit；P6 普通/已结束透明度由代理依据固定场景四版对比选定。真实日期与业务规则优先于参考图里的示意数据。

# 执行计划补充与优先规则（2026-10-04 用户确认版）

本节整合用户确认的实施计划与后续十项补充。与下方原始 v2 文档冲突时，以本节为准；原文保留作为设计依据。旧 docs/UI-PLAN.md 不再作为本轮执行依据。

## 执行顺序与阶段门禁

P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P8.5 → P9。
每次只完成一个阶段：编译 → 指定自动回归 → 真机截图 → 用户确认 → 独立 Commit → 下一阶段。遮挡、裁切、日期错误或交互回归必须先修复，不得跳过确认。P6 用户选择后才锁定透明度。当前文档落盘不代表任何实现、构建或测试已完成。

## P0：逻辑与视觉基线冻结

- 从 main 的 57d827c8201c0af0decc8924e325df19c1acc3a2 创建 ui/v2-refresh；先确认工作区与基线差异，保留既有用户修改。
- 执行 verify-logic.ps1、security-audit.ps1 与完整 device validation；既有 15 passed / 0 failed / 0 skipped 记录仅是参考，必须记录此次实测结果。
- 在独立验证应用中固定测试课程：08:00–09:40 离散数学；10:00–11:40 数据结构；14:30–16:10 数据结构。不得清除或改写原应用数据。
- 保存 baseline_home.png、baseline_settings.png、baseline_course_edit.png。
- 记录设备型号、Android 版本、分辨率、密度/逻辑宽度、字体比例、主色 preset、课程完整字段、学期配置、选中日期/周、滚动位置、截图时间环境及课程状态。
- 使用验证专用时间输入冻结截图场景，不修改手机全局时钟，不改变正式版本的取时行为。P6 四版必须复用完全相同的场景。

## P1：Hero 与首批图标

- Hero maxHeight = 150dp，targetRatio = 19%。sceneScale 初始值 1.22，可在 1.18–1.26 内调整，不把 1.22 当作最终验收值。
- 验收双眼完整、头饰主体完整、脸部不被按钮遮挡、人物与卡片无突兀切边；检查浅天空上的状态栏图标，以及导航栏与页面衔接。
- 同步调整设置与加号图标，不混入其他阶段布局。

## P2：下一节卡与状态矩阵

| 状态 | 展示与验收 |
|---|---|
| A 正在上课 | 正在上课、剩余分钟、课程名、时间/地点、教师/周次/节次 |
| B 今天还有下一节 | 下一节、距离开始分钟、完整课程信息 |
| C 下一节在未来其他日期 | 下一节 · 星期 · 日期、课程信息；不显示跨日分钟倒计时 |
| D 今天课程已全部结束 | 今日课程已结束；若有未来课程，同时展示其日期和信息 |
| E 完全没有后续课程 | 暂无后续课程；清除旧课程信息和倒计时 |
| F 查看非当前周/预览模式 | 预览第 N 周；不显示真实倒计时，保留返回本周入口 |
| G 未开学 | 未开学；有首课则展示其日期和课程信息 |
| H 学期结束 | 本学期已结束；不显示倒计时 |

优先级：F → G/H → A → B → D（今天有课且全部结束，可带未来课程信息）→ C → E。仅复用现有课程查找与状态规则，展示分支不得修改业务算法。无下一节时不得残留上一状态的数据。下一节卡使用 Chevron；Pointer 仅用于时间轴“当前”胶囊。

## P3 / P4：唯一日期来源

- 统一 private displayedDateMillis(int week, int day)，内部优先委托现有 courseDateMillisForWeek(day, week)，注意原 helper 的参数顺序。
- 顶部日期、星期栏日期、今天判断和 P8 时间线全部基于这一入口；不创建第二套教学周换算算法。
- 禁止固定 24 小时毫秒偏移；自然日比较复用既有规则。
- P3 展示第 N 周 · 星期 · 日期与独立周范围，保留未开学/假期语义。
- P4 保留七天横向滑动及选中居中；文档五列图示是可见区域示例，不删除周日/周一。
- 验证跨月、跨年、夏令时、学期首尾及切换预览周。

## P5：课程卡与窄屏降级

- 课程名、时间、地点优先，统一辅助图标并减轻更多按钮视觉重量。
- 354dp 或大字体空间不足时，地点与教师优先，周次进入第二行；宁愿换行，辅助文字不得低于 11sp。
- 地点/教师 setSingleLine(true) 且 END ellipsize；无障碍描述保留完整信息。
- 保留课程操作、时间状态、动画、进度与颜色索引语义。

## P6：固定场景 Alpha 比较

- 普通卡片比较 0.45 / 0.58 / 0.70 / 0.95；已结束卡片同时比较，独立确定 COURSE_ART_ALPHA_NORMAL 与 COURSE_ART_ALPHA_COMPLETED。
- 每版同一设备、课程、选中日/周、滚动位置、主色、时间输入、课程状态、分辨率与字体比例。
- 四版同时覆盖普通未开始课程与已结束课程。已结束候选也以相同四档作为初次比较，用户可选择进一步降低；不得提前锁定示例数值 0.58/0.30。
- 记录候选值与截图一一对应关系，用户确认后定稿。

## P7：视觉尺寸与真实点击尺寸分离

- 44×44dp 外层 touch container，居中容纳 38×38dp 设置按钮或 42×42dp 添加按钮；点击事件和无障碍语义统一放在外层，避免重复焦点。
- 周导航同样保证实际触摸区域至少 44dp，不能仅靠视觉声明。
- 新增文件导入与通知状态图标；保留导入确认与权限入口的原逻辑。

## P8：按时间比例定位的当前时间线

定义每行 courseTimelineAnchorTop / Bottom，为左侧时间轴的有效纵向区域，排除卡片上下 padding；所有坐标转换到统一 timelineContainer 坐标系。

课中：progress = clamp((now − courseStart) / (courseEnd − courseStart), 0, 1)
Y = anchorTop + progress × (anchorBottom − anchorTop)

课间：gapProgress = clamp((now − prevEnd) / (nextStart − prevEnd), 0, 1)
Y = prevAnchorBottom + gapProgress × (nextAnchorTop − prevAnchorBottom)

首课前 clamp 到 timetable top；末课后 clamp 到 timetable bottom，不向列表外外推。空列表不构造比例轴；无效/零时长区间不得除零，隐藏该位置并记录验证失败。

- 仅查看真实当前周且查看日期是今天时显示；判断共同使用 displayedDateMillis。
- 在 post/布局监听回调后读取位置；分钟刷新、切日、切周、课程修改及屏幕尺寸变化后重算。旧布局失效时先隐藏旧指示器，避免闪现。
- Timeline Overlay：装饰背景 → 卡片背景 → 当前时间线 → 文字/图标/按钮。若现有结构无法细分，至少保证 gutter 完整可见，横线进入卡片使用 20%–30% Alpha，并避开课程名、时间与操作内容。
- 验证课前/课中/课间/课后位置、滚动坐标以及切日/切周后的隐藏。

## P8.5：Accessibility / Contrast / System Bar Review

- 主要正文对比度至少 4.5:1；辅助文字尽量达到 3:1，未达标项目须在截图确认时明确列出，不以淡色风格掩盖可读性问题。
- 设置、添加课程、上一周、下一周、返回本周、编辑课程等可点击项具有描述；纯装饰 IMPORTANT_FOR_ACCESSIBILITY_NO。
- 字体 100%：完整目标布局；115%：无裁切；130%：允许辅助信息换行，但功能不可丢失。
- 检查 Hero 各位置下状态栏图标的可读性、导航栏与底部背景衔接。

## P9：Token 与最终验收

- 收口颜色、字体、间距、圆角、透明度、按钮及图标 Token，新增 docs/ui-style-guide.md。
- 工具图标 24×24、1.8 线宽、圆头圆角；加号 2.0–2.1；调色盘改描边，更多圆点缩小，Launcher 不在本轮范围。
- 各阶段编译并运行 host regression；P2/P3/P4/P7/P8 增加对应 device validation。P9 执行完整 regression、安全审计与完整 device validation。
- 覆盖实际约 354dp 设备和 360/393/411dp，字体 100/115/130%；测试结束恢复测试前的设备显示/字体设置。
- 不修改存储、提醒调度、PDF 解析与冲突校验，不更改“确认导入”文本，不新增公共 API、持久化字段或迁移。
- 历史课程自动删除、保存失败处理、签名配置等既有问题保持范围之外。

---

# 原始 UI Upgrade Plan v2（保留参考）

# Mein-Stundenplan UI Upgrade Plan v2
## 基于 2026-10-04 Bugfix 后 `main` 分支的高质量 UI 升级计划

> 仓库：`YUYU-Fish2/Mein-Stundenplan`  
> 审查基线：`main` 最新合并提交 `57d827c8201c0af0decc8924e325df19c1acc3a2`  
> 对应核心修复提交：`0021718923895018dbb6a3e14af5bc1c5865164c`  
> 目标：**只升级 UI，不改变现有业务行为、课表规则、提醒规则、数据结构和持久化语义。**  
> 实施方法：**每次只完成一个视觉阶段 → 编译/自动回归 → 真机截图 → 人工确认 → Commit → 下一阶段。**

---

# 0. 本次代码审查结论

刚合并的 Bugfix **不会推翻原 UI 升级方向**，P1～P9 仍然成立。

但升级计划需要做 4 个重要修订：

1. **日期/周次 UI 必须沿用新修复后的“日历日”算法。**
   - 旧代码中的 `MILLIS_PER_DAY / MILLIS_PER_WEEK` 已移除。
   - `weekRangeLabel()` 已改为 `Calendar.add(Calendar.DAY_OF_YEAR, ...)`。
   - `currentTeachingWeek()` 与 `calendarTotalWeeks()` 已改为 `TimetableRules.daysBetweenDates(...)`。
   - UI 新增日期时禁止重新用 `24h * N` 计算，否则会把已经修好的 DST / 日期问题重新引入。

2. **新增的逻辑/真机回归测试应成为 UI 改造的强制护栏。**
   - 当前真机记录为 15 passed / 0 failed / 0 skipped。
   - UI 每一个较大阶段完成后至少跑 host regression；关键阶段跑 device validation。

3. **导入确认流程现在增加了二次节次校验与冲突校验。**
   - UI 改造不得绕过 `parsedAndShowCourses()` 的确认链路。
   - 不要为了“UI 更简洁”重写导入循环或移动冲突判断。

4. **图标系统需要加入正式改造范围。**
   - 当前图标并非全部低质量，但存在“线宽、填充方式、语义、视觉重量”不统一。
   - 高质量版应建立一套统一的 24×24 VectorDrawable 图标系统，而不是继续混用不同视觉体系。

---

# 1. Bugfix 对 UI Plan 的具体影响

## 1.1 `MainActivity.java` 日期相关变化

Bugfix 后：

```java
private String weekRangeLabel(int week) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(calendarStartMillis());
    calendar.add(Calendar.DAY_OF_YEAR, (week - 1) * 7);
    long start = calendar.getTimeInMillis();
    calendar.add(Calendar.DAY_OF_YEAR, 6);
    long end = calendar.getTimeInMillis();
    ...
}
```

同时：

```java
currentTeachingWeek()
```

已经改为：

```java
TimetableRules.daysBetweenDates(start, today)
```

`calendarTotalWeeks()` 同样使用：

```java
TimetableRules.daysBetweenDates(start, end)
```

### 对 P3 / P4 的要求

原计划里所有类似：

```java
calendarStartMillis() + dayOffset * 24h
calendarStartMillis() + weekOffset * 7 * 24h
```

全部禁止。

新增日期展示 helper 必须采用：

```java
Calendar c = Calendar.getInstance();
c.setTimeInMillis(calendarStartMillis());
c.add(Calendar.DAY_OF_YEAR, offset);
```

或者直接消费现有已经正确计算的日期 helper。

### 原因

“一个自然日”不总是严格等于 86,400,000ms。

DST 时区可能出现：

```text
23 小时的一天
25 小时的一天
```

Bugfix 已经针对这类问题修复并做回归测试。

UI 层不能重新引入毫秒加法。

---

# 2. Bugfix 后必须保护的业务行为

## 2.1 导入课程

最新代码增加：

```java
if (parsed.period < 1
        || parsed.endPeriod < parsed.period
        || parsed.endPeriod > periodCount()) {
    ...
}
```

并增加：

```java
if (hasCourseConflict(parsed)) {
    ...
}
```

因此 UI 改造时：

- 不改导入顺序。
- 不直接 `courses.add(parsed)`。
- 不绕过确认 dialog。
- 不改变“确认导入”按钮含义。
- 不更改 period 设置与导入预览之间的重新校验关系。

---

## 2.2 Reminder

最新版本修复：

- stale reminder
- reminder generation
- debug reminder 标记
- schedule clear 后旧广播不得继续通知

设置页面可以改视觉，但不能改：

```text
ReminderScheduler
ClassReminderReceiver
generation
requestCode
schedule replace / clear
```

---

## 2.3 PDF Parser

解析器修复：

- ASCII name
- standalone hex
- UTF 编码兼容
- compressed stream
- period boundary

这些全部属于 UI Plan 外部。

UI 只展示最终 Course。

---

# 3. 新增 P0 — Bugfix 基线冻结

在开始 P1 前先建立“UI 改造基线”。

## P0.1 记录 Git 基线

记录：

```text
BASELINE_MAIN=57d827c8201c0af0decc8924e325df19c1acc3a2
```

UI 分支从该提交创建：

```bash
git checkout main
git pull
git checkout -b ui/v2-refresh
```

---

## P0.2 运行逻辑回归

至少运行：

```powershell
.\tools\verify-logic.ps1
```

如果环境允许，同时：

```powershell
.\tools\security-audit.ps1
```

预期：

```text
PASS
```

---

## P0.3 真机基线

条件允许时：

```powershell
.\tools\verify-device.ps1 -Jdk "<JDK>" -Sdk "<SDK>"
```

已知当前基线：

```text
15 passed
0 failed
0 skipped
```

### 注意

UI 改造并不意味着每次都必须完整跑 AlarmManager 真机测试。

建议：

```text
P0  完整 device validation
P1  host regression
P2  host + device
P3  host + device（日期重点）
P4  host + device（日期重点）
P5  host regression
P6  host regression
P7  host + device（settings / reminder UI）
P8  host + device（时间状态）
P9  完整 regression + device validation
```

---

# 4. UI 改造总原则

最终视觉方向保持：

```text
浅蓝 / 白
冬雪莲
轻二次元
水彩
通透
安静
个性化
```

但整体质量标准提高为：

```text
统一的视觉语言
统一图标体系
统一圆角
统一线宽
统一间距
更明确的信息层级
更可靠的响应式布局
更少的视觉噪声
```

核心原则：

> 信息是主体，二次元视觉是品牌氛围，插画与装饰不能压过信息。

---

# 5. 图标系统专项审查

当前 Drawable：

```text
ic_bell.xml
ic_calendar_outline.xml
ic_chevron_right.xml
ic_clock_outline.xml
ic_file_text.xml
ic_location.xml
ic_more_horizontal.xml
ic_palette_outline.xml
ic_person.xml
ic_plus.xml
ic_pointer.xml
ic_settings_outline.xml
ic_sparkle.xml
ic_trash_outline.xml
ic_warning.xml
...
```

当前不是“图标都不好”，而是存在以下一致性问题。

---

# 6. 图标问题 1 — 线宽不统一

当前：

```text
location      1.8
person        1.8
warning       1.8

clock         2.0
calendar      2.0
bell          2.0
file          2.0
trash         2.0

chevron       2.2
plus          2.4
```

单个看都能用，但组合到同一界面会产生：

```text
有些图标显得轻
有些显得粗
有些像另一套 icon pack
```

## 新规范

工具型 outline icons：

```text
viewport：24 × 24
stroke：1.8
strokeLineCap：round
strokeLineJoin：round
```

主操作 `+` 可略重：

```text
2.0 ~ 2.1
```

但不继续使用 2.4。

---

# 7. 图标问题 2 — `ic_palette_outline` 风格明显不一致

当前 `ic_palette_outline.xml` 是：

```text
大面积实心 Material 风格
```

其他设置图标基本都是：

```text
outline
```

这是目前最应该替换的图标之一。

## 处理

重新绘制：

```text
ic_palette_outline.xml
```

新版本：

- 1.8dp outline
- 圆角线帽
- 调色盘主体为空心
- 3~4 个小色孔可以采用细线/小圆点
- 不使用大面积实心 Path

---

# 8. 图标问题 3 — `ic_pointer` 被错误承担“右箭头”语义

当前 `ic_pointer.xml` 是：

```text
实心小三角
```

资源注释本身说明它原本用于：

```text
时间轴“当前”胶囊的指向三角
```

但它现在同时被：

```java
statusChip
```

作为 Chevron 使用。

## 修改

下一节卡：

```java
R.drawable.ic_pointer
```

改成：

```java
R.drawable.ic_chevron_right
```

或者新增：

```text
ic_chevron_small.xml
```

`ic_pointer` 只允许用于：

```text
timeline label pointer
```

---

# 9. 图标问题 4 — Settings 图标概念合适，但需要重绘

当前：

```text
ic_settings_outline
```

实际不是齿轮，而是：

```text
三条 slider / tune
```

这个选择本身是好的。

它比传统齿轮更适合当前：

```text
柔和
年轻
个性化
轻量
```

的 UI。

因此：

> **不换成齿轮。**

但重新调整：

```text
主线：1.8dp
旋钮：缩小约 10~15%
三条线长度稍错开
保持圆线帽
```

---

# 10. 图标问题 5 — `more_horizontal` 略重

当前三个圆点半径约：

```text
1.8
```

课程卡右上角又有：

```text
36dp 白色圆形按钮
```

视觉上容易形成：

```text
“大白圆 + 三个很黑的点”
```

略显重。

## 建议

圆点半径：

```text
1.45 ~ 1.55
```

并保持：

```text
primaryTextColor tint
```

---

# 11. 图标问题 6 — PDF 导入语义可以更明确

当前：

```text
ic_file_text
```

表达的是：

```text
文档
```

但菜单行为是：

```text
导入 PDF 课表
```

高质量版建议新增：

```text
ic_file_import.xml
```

图形：

```text
文件 outline
+
向内 / 向下 import 箭头
```

---

# 12. 图标问题 7 — “通知状态”和“课程提醒”使用同一个 Bell

设置页当前：

```text
通知状态       → bell
课程提醒       → bell
```

连续出现两个完全相同的 Bell，扫描效率一般。

## 高质量方案

保留：

```text
课程提醒 → ic_bell
```

新增：

```text
通知状态 → ic_bell_status
```

`ic_bell_status`：

```text
bell outline
+
右下角小 check / status dot
```

---

# 13. 建议保留的图标

## `ic_sparkle`

保留。它符合“下一节”的轻提示与二次元氛围。

## `ic_location`

保留几何。已是 1.8dp outline。

## `ic_person`

保留几何。已接近目标规范。

## `ic_warning`

保留。当前语义清晰、线宽合适。

## `ic_chevron_right`

保留概念，但统一到 1.8dp。

## `ic_trash_outline`

保留概念，但统一到 1.8dp。

---

# 14. 图标颜色规范

当前 VectorDrawable 内部存在：

```text
#FFFFFFFF
#FF000000
```

混用。

因为代码已经大量采用：

```java
setImageTintList(...)
```

高质量版建议：

> VectorDrawable 本体统一使用 neutral source color，再由 View 层 tint。

建议：

```xml
android:strokeColor="#FF000000"
android:fillColor="#FF000000"
```

需要透明：

```xml
android:fillColor="@android:color/transparent"
```

Launcher assets 不参与该规范。

---

# 15. 图标尺寸规范

文件：

```text
24 × 24 viewport
```

实际显示：

```text
Meta icon：12~14dp
Card action：18~20dp
Settings menu：20~22dp
Header action：20dp
Chevron：16~18dp
```

触控区域：

```text
≥ 44dp
```

视觉尺寸和 Touch Target 分离。

---

# 16. 图标改造分批实施

## Icon Pass A — Header

随 P1 / P2：

```text
settings tune
plus
sparkle
clock
next-card chevron
```

## Icon Pass B — Course Cards

随 P5：

```text
clock
calendar
location
person
warning
more
```

## Icon Pass C — Settings

随 P7：

```text
file_import
bell_status
bell
clock
calendar
palette
trash
chevron
```

---

# 17. P1 — Hero 压缩

目标不变。

当前：

```java
HERO_CHARACTER_MAX_HEIGHT_DP = 174;
```

第一轮：

```text
174 → 150
22% → 19%
1.36 → 约 1.22
```

本轮只允许同时做：

```text
settings tune 图标线宽统一
plus 图标线宽统一
```

---

# 18. P2 — 下一节课程 Card

目标：

```text
✦ 下一节                         31 min 后

数据结构

◷ 14:30–16:10        ⌖ 锡科301

罗志坚                 1–17周 · 5–6节
```

业务来源继续使用：

```java
currentCourseInProgress()
nextUpcomingCourseFromToday()
```

不写第二套课程查找。

P2 图标：

```text
ic_sparkle
ic_clock_outline
ic_location
ic_person
ic_calendar_outline
ic_chevron_right
```

明确禁止：

```text
ic_pointer 作为普通 chevron
```

---

# 19. P3 — 顶部日期层级

目标：

```text
第5周 · 周四 · 10月1日
9.28 – 10.4
```

所有日期计算必须：

```java
Calendar.add(Calendar.DAY_OF_YEAR, offset)
```

禁止：

```java
base + offset * 24h
```

优先复用已有 helper。

---

# 20. P4 — 星期 + 日期

目标：

```text
周二      周三      周四      周五      周六
9/29      9/30      10/1      10/2      10/3
```

推荐实现：

```java
private long displayedDateMillis(int teachingWeek, int schoolDay) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTimeInMillis(calendarStartMillis());
    calendar.add(Calendar.DAY_OF_YEAR, (teachingWeek - 1) * 7 + schoolDay);
    return startOfDayMillis(calendar.getTimeInMillis());
}
```

如果 `courseDateMillisForWeek()` 可以表达同一语义：

> 优先复用它。

回归重点：

```text
DST
跨月
跨年
开学首周
最后一周
```

---

# 21. P5 — 课程 Card 信息层级 + Icon Pass B

课程卡逻辑不变。

继续依赖：

```java
courseTemporalState(...)
isCourseActive(...)
periodRangeTime(...)
```

课程卡全部采用统一 outline family：

```text
clock
calendar
location
person
warning
```

右上角 `more` 使用轻量小圆点版本。

---

# 22. P6 — 插画强度重新评估

原计划建议：

```text
0.25 / 0.35 / 0.45
```

但当前源码明确说明 0.95 是此前为了让插画更明显而提高的。

因此新版比较改成：

```text
A：0.45
B：0.58
C：0.70
Control：0.95
```

不要直接把插画降到几乎消失。

完成课程当前：

```java
courseArt.setAlpha(0.40f);
```

待普通卡最终 Alpha 确定后再调整。

判定标准：

```text
课程名先被看到
插画第二层可感知
插画仍有个性
文字区域无明显干扰
```

---

# 23. P7 — Action / Week Nav + Settings Icon Pass C

Header：

```text
设置：次级
＋：主级
```

建议：

```text
设置视觉尺寸 38dp
＋视觉尺寸 42dp
Touch Target 均 ≥ 44dp
```

Settings 正式替换：

```text
ic_file_text       → ic_file_import
通知状态 bell       → ic_bell_status
课程提醒            → ic_bell
ic_palette_outline → 新 outline palette
trash               → 统一 1.8 stroke
chevrons            → 统一 1.8 stroke
```

---

# 24. P8 — 当前真实时间线

目标：

```text
13:59 ● ───────────────── 现在
```

只在：

```text
当前查看周 = 当前真实周
当前查看日 = 今天
```

时显示。

日期比较必须使用自然日语义，不使用 `MILLIS_PER_DAY`。

---

# 25. P9 — Design Token + Icon Token 收口

最终统一：

```text
icon size
button size
card radius
spacing
text size
alpha
elevation
stroke family
```

建议新增：

```text
docs/ui-style-guide.md
```

记录：

```text
24×24 viewport
1.8dp standard outline stroke
round cap / join
filled icon 使用场景
icon optical size
touch target
colors / tint policy
```

---

# 26. 自动测试与 UI 改造协作规则

最新仓库新增：

```text
BugRegressionTest
DeviceValidationRunner
ActivityLogicRegressionTest
verify-device.ps1
```

当前 device test 会寻找：

```text
确认导入
```

因此本轮 UI Plan 不修改该可访问文字。

如果以后确实修改，必须同步更新测试，并确认只是文案变化。

---

# 27. 当前已知但不属于 UI Plan 的问题

最新真机验证文档仍记录：

1. 显式过去周次课程可能被自动删除，历史周浏览不一定完整。
2. 持久化提交失败时内存态可能已修改。
3. signing property precedence 与文档不完全一致。
4. PDF CMap 兼容仍有限。

这些问题不在本次 UI 改造范围。

P3/P4 会强化历史周与日期浏览，因此测试时如果旧周课程为空，不要直接判断为 UI 回归。

---

# 28. 高质量 Definition of Done

## 视觉

- [ ] Hero 不抢课表
- [ ] 下一节卡成为第一信息焦点
- [ ] 日期/星期关系一眼可懂
- [ ] 课程名、时间、地点层级明确
- [ ] 水彩插画存在但不妨碍文字
- [ ] 图标没有混搭感
- [ ] 工具型图标统一 1.8dp outline
- [ ] Filled icon 只用于少数品牌/状态强调
- [ ] 主次按钮明确
- [ ] Touch Target 足够

## 功能

- [ ] TimetableRules 行为不变
- [ ] Import conflict 行为不变
- [ ] stale reminder 修复不回归
- [ ] date / DST 修复不回归
- [ ] PDF import 行为不变
- [ ] persistence schema 不变

## 测试

- [ ] `verify-logic.ps1` PASS
- [ ] Security audit PASS
- [ ] P3/P4 后 device validation PASS
- [ ] 最终 device validation PASS
- [ ] 至少测试 360 / 393 / 411dp
- [ ] 字体 100 / 115 / 130%

---

# 29. 最新推荐实施顺序

```text
P0  Bugfix baseline freeze
 ↓

P1  Hero 压缩
    + Settings / Plus icon optical refine
 ↓ 截图确认

P2  下一节 Card
    + Header icon family
 ↓ 截图确认

P3  顶部日期层级
    + DST-safe date display
 ↓ 回归 + 截图确认

P4  星期 + 日期
    + DST-safe date display
 ↓ 回归 + 截图确认

P5  Course Card 层级
    + Course icon family
 ↓ 截图确认

P6  Course Art 0.45 / 0.58 / 0.70 / 0.95 对比
 ↓ 用户选择

P7  Action / Week Nav
    + Settings icon family
 ↓ 截图确认

P8  Current Time Indicator
 ↓ 回归 + 截图确认

P9  Design Token / Icon Token 收口
 ↓

完整 regression
完整 device validation
最终截图
```

---

# 30. 最终结论

Bugfix 后：

> **原升级方向不需要重做，但日期相关实现必须升级为 Bugfix-aware 版本。**

最重要的新约束：

```text
绝不重新使用 24h 毫秒加法计算自然日
```

最重要的新质量提升：

```text
建立统一图标体系
```

图标优先级：

```text
最高：
1. palette：Filled → Outline
2. statusChip pointer → 真正 Chevron
3. file_text → file_import
4. notification status → bell_status

中等：
5. settings tune 统一线宽
6. more dot 减重
7. clock / calendar / bell / trash → 1.8dp
8. chevron 2.2 → 1.8

保留：
sparkle
location
person
warning
```

最终目标不是单纯“换漂亮图标”，而是：

> **让 Hero、课程卡、设置页、时间轴看起来像同一个设计师、同一个产品、同一套 Design System 做出来的。**
