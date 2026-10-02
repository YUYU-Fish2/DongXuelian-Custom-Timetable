# UI 升级方案（会话恢复存档）

> 本文件由当前会话从 DSH 会话日志（session-76d48859, 2026-10-02）中恢复，原文为用户确认过的
> `exit_plan_mode` 方案，未做内容改动。目标稿见会话附件（1024×1536 效果图）。
>
> **当前状态（2026-10-02 22:5x）**：工作区是 `v1.2.2`（versionCode 5）的干净 clone，
> `git status` 无改动，方案尚未落地任何一行代码 —— 相当于 phase 0。
> 已确认边界：底部导航不做；角色插画暂不落地（仅留 drop-in 占位）。
# 我的课表 · UI 风格升级实施方案

## 0. 已确认边界

| 项 | 决策 |
|---|---|
| 底部导航（课表/待办/我的） | **不做，整体忽略**（你已确认） |
| 信息架构 / 功能布局 / 交互逻辑 | **不推翻**。所有开关、弹窗、手势、通知、PDF 导入逻辑原样保留 |
| 角色插画 | **暂不落地**，只做 drop-in 挂钩 + 无人物矢量装饰版（原因见 §11） |
| 视觉比例 | 70% 功能 UI / 20% 二次元氛围 / 10% 装饰 |
| 主色 | 默认 `#4C63D9`，其余按你给的低饱和色板 |
| 交付物 | 可编译安装的 debug APK + 实机截图对比 |

**技术前提（已核实）**：纯 Java + Material 1.12.0，UI 全部在 `MainActivity.java`（5143 行）用代码构建，**无 XML 布局**；`minSdk 23`；设备 realme RMX3700 逻辑宽度仅 **354dp**（`isCompactWidth()` 命中 compact 分支）。所以所有设计都要按窄屏优先来定尺寸。

---

## 1. 设计令牌（新增集中层）

在 `MainActivity.java` 现有 `bgColor()/cardColor()/accentColor()` 同一区域（4460–4536）旁新增一组 token 方法，**不引入 XML theme、不改 `styles.xml` 结构**。

```
背景      顶部 #F5F8FD → 中 #F3F7FC → 底 #F7F9FD   （三段竖直渐变，去掉现在的纯色）
主色      #4C63D9   次蓝 #7895E8   浅蓝 #E9F0FF
辅色      淡紫 #9A88D8  淡青绿 #8FC4B2  淡粉 #E6A9B1  淡橙 #E5B07C
文字      主 #1B2233 / 次 #6B7488 / 三级 #98A2B8
描边      #E8EEF9（浅色） / #2A3346（深色），1px
圆角      卡片 16dp · 进行中 20dp · 按钮 16–20dp · 胶囊 100dp · 小标签 10dp
阴影      卡片 elevation 2dp；浮层 3dp。禁止黑色重阴影
间距      4 / 8 / 16 / 24 / 32
字重      仅 3 档：Regular(`sans-serif`) / Medium(`sans-serif-medium`) / Bold(`sans-serif`)
```

**关键决策：不动已存数据。**
- `ACCENT_PRESETS`（78–86）只把 hex 重调到低饱和色板 —— 存储的是 index（`accent_preset`），**无需迁移**。
- `COURSE_COLORS`（139–150）**保持原值不变**（它们被逐课写进加密 JSON，改了会让 `safeColor()` 把已有课的颜色全部重映射）。改为新增一个 **显示期去饱和函数** `courseTint(int storedColor)`，把存储色朝白/主色混合后用于渲染。数据零迁移、观感统一。
- 字重：`appTypeface(style)` 保持不动（标题用 BOLD）；新增 `appTypefaceMedium()` 给课程名/时间/标签用。不打包字体文件 —— 你的 brief 指定 PingFang SC，Android 无此字体，**接受偏差**，用系统 CJK 栈。

## 2. 背景与 Hero 区（`buildLayout()` 685–928）

- `safeFrame` 背景纯色 → 三段竖直 `GradientDrawable`。背景**固定在 `safeFrame` 上不随滚动**，形成"浅色纸上浮动卡片"的观感。`configureWindow()` 里 `setStatusBarColor` 改用渐变的**顶部色**，避免状态栏割裂。
- 头部新增 hero 容器，高度硬上限 `min(屏高*20%, 170dp)`，**保证不压缩课表可视空间**。
- hero 右上：柔光晕（`GradientDrawable` 径向，主色 → 透明，约 160dp）+ 角色插画位。
- 无人物时用原创矢量装饰填充：花瓣 / 云 / 星 / 书本 / 植物 / 耳机，透明度 8–20%，只贴边缘与四角，**不进入文字区**。
- 装饰数量克制：hero 区 3–5 个，页面其余部位 ≤2 个，手写感文案每屏最多 1 句。

## 3. 顶部动作按钮（`titleBlock` / `actions` 775–819）

- 尺寸 48dp → **44dp**；白底半透明 + 极浅阴影 + 主色图标；`+` 保持主色实心填充 + 白图标。
- **保留设置齿轮**。因为底部导航已砍掉，齿轮是设置唯一入口，会变成 4 个圆钮（月亮 / 调色板 / 齿轮 / ＋）。这是砍掉底部导航的直接后果，明确记录。
- 位置：compact 下动作行右对齐独占一行（沿用现有行为），宽屏与标题同行。间距按新 token 收紧。

## 4. 「下一节」胶囊组件（新增，替换 `statusChip`）

现有 `statusChip`（760–773）+ `updateStatusChip()`（989–1014）从"一个纯文字 chip"重构为一个组件：

```
┌────────────────────────────────────┐
│ ⭐ 下一节   14:30 – 16:10        ›  │   ← 第一行：sparkle 图标 + 标签 + 时间 + chevron
│ 计算机组成原理                      │   ← 第二行：课程名 SemiBold
└────────────────────────────────────┘
```
- 背景：浅蓝 → 淡紫渐变，圆角 16dp，主色 12% 细描边。**不用 emoji**，用新增矢量 `ic_sparkle`。
- 三态：`正在上课`（主色加重）/ `下一节` / `暂无后续课程`（整体弱化为灰）。
- 数据源完全复用现有 `currentCourseInProgress()` / `nextUpcomingCourseFromToday()`（4156–4204），**不新增业务逻辑**。
- `previewMode`（预览周次时的"点按回到本周"）也落到这个组件上，保持 `returnToCurrentWeek()` 行为。
- 点击：若该课不在当前选中日 → `switchToDay(course.day)`；否则不响应。纯增量交互，不改现有手势。

## 5. 周次导航（`weekNavRow` 822–888）

结构不动，只重样式：`weekRangeText` 背景从灰蓝 `segmentedTrackColor()` → 白/半透明 + 细描边；箭头按钮白圆 36dp；`本周` badge 对齐新 chip token。`renderWeekNav()` 逻辑不改。

## 6. 日期标签条（`renderDayTabs()` 1255–1315）

- **去掉整条 track 背景**（现在是 `elevatedCardBackground(segmentedTrackColor())`），目标 UI 无 track。
- 选中态：主色实心胶囊 + 白字 + 下方小圆点；今天：主色文字/点；其余：次级灰。
- `daySelectionSlider` 滑动胶囊机制**保留**，只改颜色/尺寸/圆角。
- **7 天同屏**（这是你截图里"只显示到周五"的修复）：compact 下 tab 宽 62dp → **44dp**，间隙 2dp → 7×44 + 7×2 + 8 = 330dp，正好落在 354−24 内。`HorizontalScrollView` 保留作为更窄屏的兜底。
- swipe 切天逻辑（`dispatchTouchEvent` 311–453）**一行不改**。

## 7. 时间轴 + 课程卡片（最大改动，`renderCourseList()` 1423–1569 / `createCourseCard()` 1571–1770）

新增 `createTimelineRow(course, isFirst, isLast, isCurrent)`：`renderCourseList()` 由"直接 add 卡片"改为"add 行"。

```
行 = LinearLayout(HORIZONTAL, clipChildren=false)
  ├ 左 gutter（FrameLayout，compact 52dp）
  │   ├ 时间文字（clockText(courseStartMinutes(course))），右对齐
  │   ├ 竖线 1dp，#E8EEF9，贯穿整行（含行间距，靠把间距做成行内 padding 实现）
  │   ├ 圆点 7dp：当前课=主色 / 普通=浅灰 / 下一节=淡青绿
  │   └ 「当前」小胶囊（主色底白字 10sp），仅当前课显示；首行不画上线、末行不画下线
  └ 右 courseCard（1f = 现有 createCourseCard 改造）
```

`createCourseCard` 改造点：
- 背景 `courseTint(course.color)` 的极淡混合；进行中卡片保留主色填充。
- 圆角 24/28 → **16/20dp**；描边 `borderColor()` → `#E8EEF9`；elevation 1 → 2dp。
- 节次徽章：`dp(56~64)` 宽三行文字 → **圆角方形 44dp、半径 12dp、两行**（`1-2` / `节`），用 `courseTint`。
- 时间：从"主色胶囊" → **线性时钟图标 + 文本**（按 brief 统一线性图标）。
- 详情：`secondaryText()` 用 `·` 拼的单行 → 拆成 `日历/定位/人` 三组图标+文本。
- **窄屏适配决策（实测 354dp 放不下三组）**：
  - 宽屏：`🕐 08:00–09:40` 一行；`📅 1-17周 · 📍 锡科503 · 👤 利珊` 第二行。
  - compact：把周次上提合并到时间行（`🕐 08:00–09:40 · 📅 1-17周`），详情行只放 `📍 锡科503 · 👤 利珊`。两组两行，稳定不截断。
  - 兜底：若 phase 3 实测仍溢出，再退化为详情分两行。
- 右下角主题小插画：无素材，用**原创矢量小物件**（几何/书本/芯片/耳机/叶子），透明 8–15%，按课程名哈希选取。
- **保留不动**：进行中进度层 `createCurrentCourseProgressLayer`、倒计时、completed 灰化、抖动动画。`pauseTimedViews`/`invalidateCourseProgressViews`/`cancelCourseCardAnimations` 都是整树递归，套一层行容器天然兼容。
- 抖动动画挂在 `shell` 上（`view.setTag(Animator)` 约定，2116–2134），所以行容器需 `setClipChildren(false)`，抖动不会带动 gutter —— 视觉反而更好。

## 8. 空状态（`renderCourseList()` 1462–1563）

你的 brief 说这里可以放开。重新设计为空状态卡片：大圆角、浅色渐变、插画位（`empty_art` 挂钩）、一句手写感文案（`今天也可以好好休息一下。`）、保留两个 CTA（＋ 添加课程 / 导入 PDF 课表）。逻辑一行不改。现有 `⚠️` 改掉 —— 用矢量图标，全项目不新增 emoji。

## 9. 深色模式

保留（月亮按钮 + 你的「夜间蓝」主题）。深色 token：底 `#0F1420` / `#131A28`，卡片 `#1B2333`，主色取 preset 的 dark 变体，装饰降到 5–10%。所有 `isDarkMode` 分支**收进新 token 方法**，不在调用点散开加判断。

## 10. 分阶段交付（每阶段可独立编译 + 装机截图 + 叫停）

| 阶段 | 内容 | 风险 |
|---|---|---|
| 1 | token 层 + 背景渐变 + 圆角/描边/阴影统一 | 低 |
| 2 | Hero + 「下一节」胶囊 + 动作按钮 + 周次导航 + day tabs（含 7 天同屏） | 中 |
| 3 | 时间轴 + 课程卡片重构 | **高**（最大） |
| 4 | 空状态 + 弹窗视觉一致性 + 装饰插画 + 进行中呼吸动画（可选） | 中 |
| 5 | 深色模式校准 + 全量回归 | 中 |

弹窗一致性只做**集中层的配色/圆角/阴影**（`glassPanelBackground`、`settingsSectionLabel`、`settingsMenuRow` 等），不逐个重排，避免破坏交互。

## 11. 角色插画的调研结论（按你的要求已查）

我实际搜了，结论是**拿不到可合法打包的素材**，我不建议把第三方同人图塞进 APK：

- 萌娘百科有[东雪莲词条](https://moegirl.icu/zh/东雪莲)和[共享资源分类](https://commons.moegirl.org.cn/zh/Category:东雪莲)，但站点有 Cloudflare 防护，我这边 **403 / 超时**，既无法枚举图片也读不到授权标签。
- 检索到的都是第三方衍生物：一个 [BOOTH 上的 MMD 模型](https://booth.pm/ko/items/6232243)、我的世界皮肤转载、AI 翻唱音频。这些都不是能给 Android 应用打包的授权素材。
- 从词条的「前世」「相关人士」体例看，她是**中文虚拟 UP 主**类角色，角色形象权属在绘制者/主体方，第三方同人图各有其作者。

**所以**：hook 先埋好，素材等你。你后续接入生图后，或者你直接给图，都能一行代码接通。

## 12. 资源改动清单

**新增矢量 drawable（约 14 个）**：`ic_school`、`ic_sparkle`、`ic_clock_filled`、`ic_calendar_filled`、`ic_location`、`ic_person`、装饰 `deco_petal/cloud/star/book/plant/headphones`、卡片小物件 `art_geometry/book/chip/headphones/leaf`。

**Drop-in 插画挂钩**：`res/drawable-nodpi/hero_character.png` 与 `empty_art.png`，先放 **1×1 透明 PNG 占位**，用 `R.drawable.*` 直接引用。
> 为什么用占位文件而不是 `getIdentifier` 按名查找：release 开了 `shrinkResources`，只按名字引用的资源会被剥掉，按名查找在 release 会静默失效。占位文件方案编译期就安全。**你只要用同名文件覆盖即可，无需改代码。**

**其他**：`values/colors.xml` + `values-night/colors.xml` 的 `status_bar`/`navigation_bar` 从米色 `#F7F4EC` 改成新背景色系。`styles.xml` 不改结构。新文案按项目现有习惯直接写在 Java（全项目仅 2 条在 `strings.xml`）。

## 13. 验收与回归清单

**每阶段必过**：`tools\verify-logic.ps1`（现有逻辑回归，期望 `Android Java compile check passed.` + 两个测试通过）→ `gradlew assembleDebug` → adb 安装 → 实机截图。

```
$env:JAVA_HOME="D:\soft\JAVA\jdk-17"
cd E:\dsh\我的课表-ui修改
.\gradlew.bat assembleDebug
```

**装机后逐项核对**：
1. 切天（点 tab / 左右滑）+ 切周（前后 / 「本周」）—— 不崩、动画正常
2. 正在上课卡片：倒计时数字滚动、进度层、抖动动画、点击停止
3. 已完成课程灰化；空状态页面
4. 浅色 / 深色 / 跟随系统 三种模式
5. 7 个主题色逐个切换，整体是否和谐（不彩虹）
6. 添加/编辑课程弹窗、设置弹窗、周次选择器、节次设置可用
7. PDF 导入仍可用（回归 `PdfCourseParser` 路径）
8. **354dp 专项**：详情行是否截断、7 天是否同屏、hero 是否压缩课表空间
9. **系统字号放大到 1.3x**：两行课程名 + 时间 + 详情不被裁切

## 14. 明确不做

- 底部导航 / 三个 tab / 待办 / 我的页面
- 改信息架构、加无功能按钮、加 emoji
- 改数据模型、存储格式、加密逻辑、通知提醒、PDF 解析
- 打包第三方动漫图
- 引入 XML 布局或 Compose（沿用纯 Java 代码构建 UI 的现有方式）

## 15. 假设与开放项（不阻塞开工）

- 主色默认 `#4C63D9`；7 个 preset 的 hex 会重调，**index 语义不变**。
- 课程颜色存储值不变，只在显示期做去饱和转换 —— 已有课表数据不会变色、不会丢。
- `hero_character.png` / `empty_art.png` 未提供时，界面按"无人物版"完整可用，不留空洞。
- 呼吸动画（brief 的"非常慢、非常克制"）列为 phase 4 可选项；若与现有动画取消机制冲突就直接放弃，因为它属于 10% 装饰层。


