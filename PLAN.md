# 潜溪课表 1.17.0 — 布局重构 + 导入周次修正 + 可定制课表

针对截图反馈的六项改进，参考 WakeUp。

## 一、主界面布局：去底栏 + 去悬浮，课表最大化（核心）
问题：底部"导入课表/设置"占空间；橙色 FAB 悬浮遮挡课表；课表卡片四周留白+圆角，格子偏窄字被截断（截图里"人工智能导论"竖排挤成一列）。

改动（`MainActivity`）：
- **删除底部 actions 栏**（导入课表 / 设置）。
- **删除悬浮橙色 FAB**（+）。
- 右上角周导航行改为：`‹ 今 ›` + 一个 **"+" 菜单按钮**。点击 "+" 弹 `PopupMenu`：添加课程 / 导入课表 / 设置。
- 课表卡片改为**贴边全宽**：去掉左右 12dp 外边距与大圆角，`scheduleCard` padding 降到 0–2dp，让 7 列每列更宽。
- 空态提示保留，但不再被 FAB 遮挡。
- 顶部渐变背景保留（WakeUp 同样有背景层），下节摘要卡保留但更紧凑。

## 二、课表格子更大更清晰
- `ScheduleGridView`：卡片文字策略优化——首行课程名允许 2–3 行（`maxLines` 放宽），`@地点`/教师按可用高度显示；小格（step=1）优先保证课程名完整。
- 列变宽后竖排挤压缓解；再将左侧时间轴 `TIME_COL_WIDTH_DP` 32→28dp 挤出横向空间。

## 三、可自定义格子大小（设置里滑块）— 已确认
- `AppSettings` 新增 `itemHeightDp`（默认 56，范围 40–80）。
- `SettingsActivity` "显示"分区加 `SeekBar`，实时预览数值；`ScheduleGridView` 用该值替换常量 `ITEM_HEIGHT_DP`。

## 四、导入背景图片 — 已确认
- 设置里"选择背景图片"：`ACTION_OPEN_DOCUMENT` 选图 → **复制到应用私有目录** `filesDir/bg.jpg`（避免 URI 权限失效）。
- `AppSettings` 存 `backgroundPath`；`MainActivity` 背景层若有图则用 `BitmapDrawable`（center-crop），否则用现有渐变。
- 加"恢复默认背景"清除。

## 五、导入周次修正（核心 bug）
问题：截图所有课都落在"第 20 周 / 1-20 周"。根因：beangle EAMS 的真实周次不在渲染单元格文本里，而在页面内联脚本 `new TaskActivity(教师,课程,教室,"周次二进制串")` + `index=day*unitCount+start` 里；当前 `scanTable` 只读 `<table>` 文本，`extractWeeks` 拿不到周次→回退 1-20。

改动（`ImportScript` NEUQ_EAMS + GENERIC 补充）：
- 解析返回 HTML 里的 **`TaskActivity` 内联脚本**：
  - 提取每个 activity 的 教师名 / 课程名 / 教室 / **周次二进制串**（如 `01111111110…`，第 n 位=第 n 周上课）。
  - 从 `index = <day>*unitCount + <start>` 与连续 `table0.activities[index][k]` 归并出 day / startNode / step。
  - 二进制串转成人类可读周次串（`2-10周` / `1-16周(单)`）传给 Java 端 `weeks` 字段。
- `ImportParser`：`weeks` 支持直接接收二进制串或范围串；`extractWeeks` 已能解析范围/单双，补充"纯二进制串"解析分支。
- 兜底：脚本解析失败时才回退到表格文本扫描（保留现有逻辑）。
- 单测：TaskActivity 周次串 → 正确 weekMask（非全 1-20）。

## 六、改进抓取与防错（scraping 健壮性）
- EAMS 抓取流程：请求 `courseTable.action` 后**优先解析内联脚本**，脚本命中即用；无脚本再 `renderAndScan` 表格。
- 去重：同一 (课程,教师,地点,day,startNode) 的多周合并为一条 `weekMask`（已有 `mergeCourse`，确认覆盖二进制路径）。
- 导入预览增加"周次"展示，方便用户核对（如 `2-16周`）。

## 七、同一时段多节课：只显一节 + 角标，点击选择 — 已确认（WakeUp 同款）
问题：现在同格重叠用"右移堆叠"，视觉杂乱（截图右侧多列叠一起）。
改动（`ScheduleGridView`）：
- 同一 (day, 起止节) 完全重叠的多门课**只画置顶一张**，右上角显示数量角标（如 `2`）。
- 点击弹 `AlertDialog` 列出该格全部课程（名/师/地/周次），用户可"设为置顶显示"——记住每格偏好（`AppSettings` 存 `slotPreferred` map: `day-startNode → courseId`）。
- 不再无脑右移堆叠；仅"部分重叠"（跨节交错）时才轻微并排。

## 八、收尾
- `CHANGELOG.md`（1.17.0）+ `build.gradle.kts`（versionCode 28 / 1.17.0）。
- `./gradlew testDebugUnitTest` + `assembleRelease`，产物拷到根目录 `QianxiSchedule-1.17.0.apk`。
- 冲突检测、静音闹钟、备份、WebVPN 逻辑保持不变。

## 风险
- TaskActivity 脚本格式各校/各版本略有差异；解析写得宽容，失败回退表格扫描，保证不比现状差。
- 背景图片走私有目录避免 SAF URI 失效；控制图片尺寸，避免 OOM（按屏幕采样）。
