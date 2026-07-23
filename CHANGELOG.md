# Changelog

## 1.17.6

- 区分“整个课表为空”和“当前周无课”：只有尚未导入任何课程时才显示添加/导入提示；
  单双周、假期等正常空周保持干净课表，不再出现“暂无课程”浮层，顶部摘要简化为“本周无课”。
- 左侧节次时间轴改为与课程格子严格共用同一行高的单一三行标签，参考 WakeUp 的同高布局；
  节数、起止时间、行距和自动字号统一随 40–80dp 格子高度变化，紧凑模式不再挤压或错位。

## 1.17.5

- **修复导入周次整体错开一周**（如 1-14 周变成 2-15 周）— beangle 的 `vaildWeeks` 二进制串本身是 **1 位起始**：下标 0 是占位（恒为 `0`），下标 n（n≥1）才表示"第 n 周有课"。之前 `weekBinToText` 又把下标 `+1` 存了一遍，相当于双重偏移。现在直接用下标本身，1.17.4 里"读 `table0.activities`"和"文本正则回退"两条路径共用这个函数，一并修好。
- **顶部日期和"下一节"卡片现在始终对应真实当前时间**，不再随浏览的周次漂移：
  - 顶部大日期之前只有"浏览周 == 本周"时才显示今天，否则显示所浏览周的周一——参照 WakeUp 的 `CourseUtils.getTodayDate()`（无条件显示今天，与选中周无关）改成顶部日期永远是 `LocalDate.now()`。
  - "下一节"卡片之前同样被"浏览周 == 本周"这个判断把关，一旦不满足（比如刚好切换到别的周，或者两个 `weekOf` 计算在某些边界产生分歧）就会静默兜底成"当前列表第一门课"，与真实时间毫无关系。现在改为直接按**当前周**重新查一次课程，不再依赖界面正在浏览哪一周。
- **新增"学期长度"设置**（设置 → 学期，10–30 周可调，默认 20 周）：当真实的当前周超过学期长度时，主界面"下一节"卡片改为显示"本学期已结束"，不再尝试计算下一门课。

## 1.17.4

- **真·修复课程名/教师显示为 `courseName+"`、`actTeacherName.join(',')` 的问题** — 1.17.3 曾试图把课表 HTML 塞进隐藏 `iframe.srcdoc`、让 `TaskActivity_01.js` 在里面重新跑一遍，但 `srcdoc` iframe 走的是 opaque origin，`TaskActivity_01.js` 里的 cookie/相对路径/`window` 依赖会静默失效，捕获垫片从没跑到，所以又回退到"文本正则"——文本层面永远不可能还原 `courseName+"(" + courseCode + ")"` 这种 JS 表达式，就一直是乱码。
  - **正确做法**：EAMS 课表页在主 WebView 里已经完整渲染时（Image #3 底部就是），`TaskActivity_01.js` **已经在当前 window 跑完**，`window.table0.activities` 里躺着一堆构造好的 `TaskActivity` 对象，`.courseName` `.teachers` `.room` `.vaildWeeks` 都是已经求值的真字符串。现在优先读取**当前页面（及其同源子 frame）**里的 `table0.activities`，用对象的字段而不是构造函数参数——`courseName+"` 这种前缀彻底消失。
  - 只有当"拉取课表"是在非课表页触发时，才回退到 iframe：这次也从 `srcdoc` 改成 `about:blank` + `document.write`，`about:blank` 继承父窗口 origin，脚本/cookie/相对路径都正常工作。
  - 迭代策略更新：**当前 window/frame 读 `table0` → 同源 iframe 重放 → 源码正则 → DOM 扫描**。
- **导入页彻底去掉"教务管理入口"下拉行** — 1.17.3 只是让"↕"图标不显示了，但仍占了一整行"选择教务系统"的下拉框。现在整行删除，导入页只剩"网址 + 打开"一栏，识别完全由 URL 域名自动判断。
- **主界面新增左右滑动切换上/下周**（对齐 WakeUp 的手势）：在课表容器上加了拦截式手势判定，只有当水平位移明显大于垂直位移（且超过 `TouchSlop`）时才认为是横向滑动，向右滑上一周，向左滑下一周；小抖动或竖向滚动仍由 `ScheduleGridView` 内部的 `ScrollView` 处理，不会误触发。
- **诊断入口顺便清理**：`ImportActivity.showProfileMenu()` 相关"保存当前入口/删除入口"仍然可用，只是入口的按钮此版本不再作为顶部下拉可见——保留代码路径以便后续需要多个入口时再放回来。

## 1.17.3

- **修复课程名和教师显示为 `courseName+"`、`actTeacherName.join(',')` 的严重问题** - 1.17.2 已经能正确解析 `new TaskActivity(...)` 的星期/节次/周次/教室，但课程名和教师仍是乱的。根因：NEUQ 的 `TaskActivity(...)` 传入的第 2/3/4 个参数不是字符串**字面量**，而是 **JS 表达式**（`courseName + "(" + courseCode + ")"`、`actTeacherName.join(",")` 等）；文本层面永远不可能把这些还原成真实值——只有 JS 引擎跑一次才行。
  - 现在把返回的课表 HTML 塞进一个隐藏 `iframe`，让页面自带的 `TaskActivity_01.js` 与内联脚本正常执行，同时在 `TaskActivity` 上装了一个**捕获垫片**：每次 `new TaskActivity(...)` 都会把**已经被引擎求值过的**参数记录到 `window.__qxActs`，然后由 `table0.activities[index]` 的下标直接读回真实的课程名、教师、教室、周次。
  - 迭代策略：**iframe 求值捕获 → 源码正则回退 → 页面 DOM 扫描**。前一步取到数据后就不会再走后续回退，兼容后台变动。
- **隐藏"教务管理入口"选择器** - 适配器已经根据当前站点自动匹配（NEUQ EAMS / NEU JWAPP / 通用扫描），不必再让用户手动选。导入面板更简洁。
- **"今"按钮改为高对比度的小圆胶囊** - `‹` `›` 两侧不变、中间"今"改为主色圆角药丸背景 + 白色加粗文字，字号统一为 12sp、宽度自适应但两侧留白足够。视觉重心突出，也不会再被字体裁切。
- **应用图标全新更换** - 主题少女图（紫发/时钟/书包）作为启动图标：
  - `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`、`ic_launcher_round.png`、`ic_launcher_foreground.png` 全套位图（48/72/96/144/192dp）
  - `mipmap-anydpi-v26/ic_launcher.xml`（Android 8+ 自适应图标：柔粉色底 + 前景）
  - `AndroidManifest.xml` 从 `@drawable/ic_launcher` 迁到 `@mipmap/ic_launcher{,_round}`，旧矢量图删除

## 1.17.2

- **修复东北大学（NEUQ）课表拉取到"授课计划"等杂乱信息、周次错乱** - 1.17.1 修好了脚本路由，但真正解析课表的 `TaskActivity` 正则从没匹配成功过，于是每次都回退到"扫描页面表格"，把左侧菜单、"授课计划"链接等当成了课程，周次也只能瞎猜。根因：beangle EAMS 里 `table0.activities[index]` 的下标是**变量 `index`**，不是数字，真正的星期/节次写在紧跟其后的 `index = 星期*unitCount + 节次` 表达式里（都从 0 开始），一节多单元的课会重复绑定多次。现在改为：
  1. 用括号配对（尊重引号）稳妥地取出 `new TaskActivity(...)` 的完整参数，不再被课程名里的逗号/括号截断。
  2. 在该 activity 与下一个 activity 之间扫描所有 `index = 天*unitCount + 节` 绑定，得到**真实星期、起始节、跨节数**。
  3. 第 7 个参数是二进制周次串（第 n 位=第 n 周有课），据此得到**准确的授课周**。
  这样课程名称、授课周、授课时间（节次）、授课教师、授课地点都直接来自后台数据，不再依赖页面是否渲染，也不会再把计划/小结当成课。
- **通用扫描增加"授课计划/教学计划/教学进度/授课安排"过滤**，即便走回退路径也不会再把这些误当课程。
- **前端修复**：
  - **背景图片现在铺满整屏**（含状态栏/信息栏下方）——主界面改为 edge-to-edge，状态栏与导航栏透明，内容层单独避让系统栏，背景层全出血；不再在信息栏下留一条纸色空白。
  - **去掉周次行与课表之间的那道横线**（WakeUp 无此硬分隔），视觉更连贯。
  - **修复顶部 `‹ 今 ›` 显示不全**：三个按钮改为统一的居中无内边距样式、加宽到 34dp、去掉字体额外内边距，"今"字不再被裁切；"+" 菜单按钮同步放大。

## 1.17.1

- **修复东北大学（NEUQ）课表拉取失败 / 页面空白** - 两个根因：
  1. `ImportScript.forAdapter()` 之前无论选哪个适配器都只返回"通用网页扫描"脚本，导致东北大学专用的 EAMS 接口脚本（直接请求 `courseTableForStd` 并解析 `TaskActivity` 周次）从未被执行。现在按适配器正确路由到 NEUQ EAMS / NEU JWAPP / 通用脚本。
  2. 当教务网址明确是 EAMS/JWAPP 系统时，**以域名识别为准**，即使下拉框误选成"青果教务"等也会自动纠正——避免在没渲染出来的门户页面上跑通用扫描。
- 因此即便门户页因 jQuery 未加载而显示为骨架（诊断里的 `jQuery is not defined`），拉取仍能工作：EAMS 脚本走后台接口取课表，不依赖页面渲染。

## 1.17.0

- **主界面全屏化课表** - 删除底部"导入课表/设置"栏和悬浮的橙色 + 按钮，功能收进右上角 **"+" 菜单**（添加课程 / 导入课表 / 设置）。课表改为**贴边全宽**，去掉左右外边距与大圆角，七列每列更宽，课程名不再被挤成竖排一列。
- **修复导入周次全部变成 1-20 周的严重问题** - 东北大学（beangle EAMS）课表页的真实周次不在表格文本里，而在页面底部的 `TaskActivity(...)` 内联脚本中（第 7 个参数是周次二进制串）。现在优先解析该脚本，提取每门课的**真实周次范围、星期、起止节次、教师、教室**，跨节课自动合并为一张卡片；脚本缺失时才回退到表格扫描。
- **同一时段多门课：只显一门 + 角标** - 完全重叠的多门课只画置顶一张，右上角显示数量角标；点击弹出列表，可查看全部课程并"置顶显示"指定的那门（每格偏好单独记住）。仅部分错峰重叠时才轻微右移并排。
- **可自定义格子高度** - 设置页"显示"分区新增滑块，课表格子高度 40–80dp 实时可调。
- **导入背景图片** - 设置页可选择相册图片作为课表背景，图片复制进应用私有目录（避免授权失效），按屏幕采样避免内存溢出；支持一键恢复默认渐变背景。
- 格子文字策略优化：单节课优先保证课程名完整，教师仅在卡片够高时显示；左侧时间轴变窄给课程列腾出空间。

## 1.16.0

- **数据模型节次化（对齐 WakeUp）** - 课程新增 `startNode`/`step`（起始节 + 占用节数），课表网格直接按节次定位卡片，不再用"最近分钟距离"反推，跨节课与重叠课的位置更稳。数据库升级到 v2，旧课程按当前作息一次性回填节次，无损升级。
- **课表视图精修** - 卡片定位改用 `startNode`/`step` 直接计算，删除了脆弱的二次布局（`OnGlobalLayoutListener`）；同一天重叠的课程改为向右堆叠错位显示，两张卡片都可点击。
- **课程编辑页重构（对齐 WakeUp 节次选择）**：
  - 时间选择由"分钟时间选择器"改为 **星期 + 起始节–结束节** 三滚轮选择（节次来自自定义作息，结束节早于起始节时自动纠正）。
  - 支持 **一门课多个时间段**（不同星期/节次/周次），可添加/移除时间段，等价于多条同名课程记录。
  - 周次改为 **周次网格勾选**（全选/单周/双周/点选），写入不规则周集合。
  - 保存时按时间段整体替换该课程的所有记录，冲突检测排除自身其他时间段。
- **导入准确性提升**：
  - 课程名：加固清理课程号、括号老师名、尾部乱码。
  - 教师：支持多位合上老师（`/`、`、`、`,` 分隔）合并显示，排除助教/监考/辅导等非任课角色。
  - 地点：覆盖更多楼名与"楼+房间号"组合（科技楼B105、3号教学楼201、信息楼201 等）。
  - 导入课程同步写入 `startNode`/`step`，网格渲染无需再从分钟反推。
- **收紧非课表区域 UI** - 顶部大标题、下一节摘要卡、底部按钮、编辑/设置页的行高与间距整体收紧，视觉更轻，贴近 WakeUp。

## 1.15.0

- **课表视图重构：节次网格布局（参考 WakeUp）** - 主界面课表从"分钟轴 Canvas 手绘"改为"节次网格"渲染，解决卡片又窄又挤、字看不清的问题：
  - 每节固定高度（56dp），课程卡片是真实子视图，间距均匀、多行文字排得开
  - 左侧时间轴显示"节次号 + 起止时间"（如 `1 / 08:00 / 08:45`），节数由自定义作息决定（默认 12 节）
  - 卡片样式照搬 WakeUp：4dp 圆角、2dp 半透明白描边、半透明填充
  - 配色改用 WakeUp 的 9 色方案（红/粉/蓝/绿/黄/橙/深橙/浅蓝/琉璃），按课程名分配
  - 表头左上角显示月份，周一–周日带日期，今天那列高亮
  - 同一天重叠的课程自动分栏并排显示
- 移除了旧的"当前时间红线"（节次网格上无法映射，WakeUp 亦无此设计）
- 周过滤、冲突检测、导入、静音模式等逻辑保持不变

## 1.14.2

- **修复：课程名称混乱问题** - 彻底清理课程名称中的课程号（如 `3030113067.01`）、括号内的老师名、以及尾部乱码（`n(`），现在显示纯净的课程名（如 `人工智能导论`）
- **修复：非课程内容被导入** - 增强过滤规则，自动排除：
  - UI 元素："操作"、"编辑"、"删除"、"查看"、"详情"等按钮文字
  - 元数据标签："代码"、"编号"、"课程号"、"上课时间"、"上课地点"等字段名
  - 非课程角色："实验辅导老师"、"助教"、"监考老师"等
  - 单独的短名字：4个字符以内且不包含课程关键词的文本（防止单独的老师名被当作课程）
- **改进：JavaScript 端和 Java 端双重过滤** - 在前端提取时和后端解析时都应用严格的噪音过滤，确保课表干净

## 1.14.1

- **修复：过滤"授课小结"等噪音数据** - 导入课表时会自动过滤掉"授课小结"、"教学小结"、"课程小结"、"备注"等非课程内容，以及"无"、"暂无"等占位符
- 改进导入逻辑，避免将教务系统的元数据误识别为课程

## 1.14.0

- **新增：自定义作息时间** - 设置页新增"上课时间"编辑功能，可以自定义每节课的开始/结束时间，支持添加/删除节次，默认12节（08:00-20:35）
- **修复："正在上课"时间显示错误** - 主界面顶部的当前课程卡片现在显示实际星期和正确时间（之前显示的是课程设定的星期，导致周日23:00这样的错误）
- **主界面视觉优化** - 渐变背景、半透明白色卡片、圆角阴影，参考 WakeUp 设计风格，视觉更精致
- 所有课程时间相关功能现在基于用户自定义的作息时间配置

## 1.13.6

- Fixed 1.13.5's script deduplication not working at all. Root cause: EAMS appends a cache-busting
  query parameter `?_=<timestamp>` (current milliseconds) to every script URL, so each request had a
  unique URL and the dedup cache never matched. The History.js error persisted.
  - Script cache now keys by **normalized URL**: `normalizeScriptUrl()` strips `?_=...` and `&_=...`
    before lookup/store, so requests with different timestamps hit the same cache entry.
  - Semantic query params like `?bg=3.4.3` (the server's actual bundle version) are preserved, since
    they genuinely affect the response body.
  - Second and subsequent requests for the same normalized URL return `SCRIPT-CACHED-304` and reuse
    the cached body without re-executing, fixing the History.js duplicate-load error.

## 1.13.5

- Fixed EAMS portal AJAX navigation breaking after clicking one menu item ("我的课表" would highlight
  but the content area stayed blank). Console error: "History.js Adapter has already been loaded".
  Root cause: EAMS bundles multiple libraries into one URL
  (`jquery-form,jquery-history,jquery-colorbox,jquery-chosen.js`), and every subpage re-references
  it. History.js detects duplicate execution and throws, breaking all subsequent AJAX navigation.
  - Added script deduplication in the interceptor: the first request for a `.js` URL is fetched,
    cached in memory, and served normally. Subsequent requests for the same URL return HTTP 304 Not
    Modified with an empty body, forcing the browser to reuse its cached copy without re-executing.
  - Script cache is cleared on every navigation (`loadUrl`) so separate page sessions start clean;
    History.js's objection is to within-page re-execution, not cross-navigation reuse.
  - Cached script detection is logged as `SCRIPT-CACHED-304` in diagnostics.

## 1.13.4

- Fixed EAMS portal still rendering as a bare skeleton despite 1.13.2's fixes. Root cause: the
  portal's jQuery script URLs carry `;JSESSIONID_AUTH=...` path parameters (URL rewriting for
  session tracking), but `isJQueryResource()` only matched filenames ending in `.js` — the
  semicolon suffix broke the match, the local jQuery fallback never triggered, and every inline
  script threw "jQuery is not defined".
  - `isJQueryResource()` now strips `;*` path parameters before matching the filename.
  - Main EAMS documents (homeExt.action, my.action, ...) are now intercepted and fetched via
    URLConnection; if the HTML has no jQuery reference at all, a `<script>` tag pointing at the
    bundled jQuery is injected right after `<head>` before the WebView parses it. Post-load
    injection cannot fix these pages — their inline bootstrap has already run and thrown.
  - The injected script tag references a virtual same-origin path (`/__qianxi__/jquery-*.js`)
    answered by the resource interceptor, so CSP/CORS stay happy.
  - Login redirects (URLs with `ticket=`) are never intercepted — CAS tickets are single-use, and
    consuming one in the interceptor while the WebView retries would break the handshake.
  - All `.js` subresource requests from NEUQ hosts are now logged (`JS-REQ`) so diagnostics show
    exactly which scripts the page asked for, whether they were served, and by which path.
  - WebView HTTP cache is cleared on every import session start: the 1.12.0 redirect loop may have
    poisoned it with failed/truncated jQuery responses that were then served indefinitely without
    ever consulting the network or the interceptor.
  - Post-load jQuery presence check simplified: it reloads once if missing (giving the injected
    HTML path a chance to work), then reports `JQUERY-STILL-MISSING` with full diagnostic context
    instead of attempting a runtime injection that cannot fix the inline scripts.

## 1.13.2

- Fixed the direct EAMS portal (http://jwxt.neuq.edu.cn) rendering as a bare skeleton. The
  diagnostics showed its jQuery script failing ("jQuery is not defined", then "bg is not
  defined") and avatar/my.action returning HTTP 403 — the WebView was dropping the session
  cookie/Referer on plain-HTTP subresource requests.
  - All NEUQ-host subresources (scripts, CSS, AJAX) are now refetched through URLConnection
    with the WebView session cookie, Referer and a stable UA, instead of only WebVPN pages.
  - Set-Cookie headers on those refetched responses are fed back into the WebView cookie store
    so the EAMS session no longer silently dies.
  - The bundled jQuery fallback now applies on any NEUQ host and is served as text/javascript.
  - Responses no longer force utf-8 when the server omits the charset (legacy EAMS pages are
    GBK; the forced charset corrupted them).
  - If the portal still finishes loading without jQuery, the page is automatically reloaded
    once so its inline bootstrap can re-run against the locally served jQuery.

## 1.13.1

- Fixed the app crashing the moment a URL started loading. `shouldInterceptRequest` (the WebVPN
  resource interceptor added in 1.11.1) runs on a WebView background thread, but it called
  `webView.getUrl()` for the Referer/User-Agent headers — Android forbids calling any WebView
  method off the UI thread and throws, killing the process. The current page URL is now mirrored
  into a volatile field on the UI thread (`onPageStarted`/`loadUrl`) and only the mirror is read
  from the interceptor; `isSamePageHost` uses the same mirror.
- Wrapped the whole interceptor in a catch-all so any future failure inside it degrades to
  normal WebView loading (and is recorded in diagnostics) instead of crashing the app.
- Made the diagnostics list thread-safe: it is appended from the interceptor's background
  thread and read/cleared from the UI thread.

## 1.13.0

- Fixed being unable to enter the EAMS portal at all: the diagnostics you reported (dozens of
  `favicon.ico` requests per second against `jwxt.neuq.edu.cn`) showed the portal reloading in a
  loop. 1.12.0 had reintroduced per-page user-agent switching (mobile UA on CAS login, desktop UA
  on EAMS); CAS binds its session to the User-Agent header, so each switch invalidated the ticket
  and bounced the browser back to login forever. The user agent is now pinned to one desktop
  value for the whole session again (the fix 1.9.0 originally shipped), and a regression test
  asserts the UA stays constant across the full CAS → EAMS chain.
- favicon.ico failures are collapsed to a single diagnostics entry so a reload loop can no
  longer flush the useful errors out of the capped diagnostics list.
- The About screen now reports the real installed version instead of a hardcoded one.

## 1.12.0

- Restored the legacy EAMS rendering profile: desktop pages disable overview scaling while CAS
  login pages keep responsive scaling and use the mobile authentication user agent.
- Bundled jQuery 1.7.2 as a local WebView fallback for old EAMS pages that reject or omit their
  absolute jQuery script request, preventing `jQuery is not defined` from aborting page setup.
- Added Referer, session-cookie and identity-encoding headers to compatibility resource retries.

## 1.11.1

- Fixed the WebVPN portal rendering as a bare skeleton (empty menu, "jQuery is not defined").
  Diagnostics showed the portal's scripts/CSS/AJAX were requested straight from the raw internal
  host (http://jwxt.neuq.edu.cn/...) instead of the proxy, so they loaded without the WebVPN
  session and failed. The WebView now intercepts those raw-internal GET requests while on a
  WebVPN page and re-fetches them through the proxy base (https://vpn.neuq.edu.cn/http/<prefix>)
  with the session cookie, then feeds the bytes back so jQuery, styles and the menu load.
- Added a visible "诊断" button in the top toolbar (the long-press gesture was easy to miss).
- Rewrite attempts and outcomes are recorded in the diagnostics view for follow-up.

## 1.11.0

- Added a load-diagnostics view (long-press the refresh button) that records every failed
  subresource request, HTTP error and JS console error, not just main-frame failures. This is
  aimed at the WebVPN portal rendering as a bare HTML skeleton: the page's stylesheets, scripts
  and AJAX calls fail silently under the proxy, and those failures were previously discarded.
- The diagnostics can be copied to the clipboard for reporting.

## 1.10.0

- Forced a desktop-width layout viewport on the EAMS portal so its side navigation stays
  expanded instead of collapsing into a drawer that would not open inside the WebView, which
  had left the home page with no reachable menu items.
- Injected the viewport override after each portal page finishes loading and nudged a resize
  event for frameworks that gate their sidebar on a JS resize listener.
- Kept login/CAS pages in their native narrow responsive layout.

## 1.9.0

- Pinned one stable desktop user agent for the whole session to stop the reload loop that
  hung academic pages: switching the user agent mid-navigation on CAS/WebVPN redirect chains
  forced repeated main-resource refetches.
- Kept overview scaling disabled for EAMS desktop pages (fixes the 1.8.0 skeleton render) and
  enabled only for CAS/authserver login pages, since viewport changes do not trigger a reload.
- Parsed academic/WebVPN URLs leniently so unescaped characters no longer misroute the render
  profile.
- Removed custom multi-window WebView handling that interfered with legacy navigation.
- Added regression tests for direct EAMS, WebVPN EAMS and NEUQ authentication URLs.

## 1.8.0

- Fixed incomplete NEUQ WebVPN/EAMS rendering caused by mobile user-agent responses.
- Applied one stable desktop Chrome user agent before any page navigation.
- Allowed legacy HTTP styles and scripts inside HTTPS WebVPN pages.
- Cleared stale WebView resources when opening the import browser.
- Detected `vpn.neuq.edu.cn` as Northeast University automatically.

## 1.7.0

- Removed fixed school URLs so any academic-system address can be entered or saved.
- Removed hostname restrictions from specialized Northeast University adapters.
- Detected compatible EAMS and JWAPP systems from URL paths on custom domains.
- Changed all adapters to scan only the timetable page opened by the user.
- Added WebView connection, TLS and render-process diagnostics with generic-page fallback.

## 1.6.0

- Fixed legacy JavaScript menus, popup windows and target-window form navigation.
- Unified Northeast University adapters into a single visible choice.
- Applied system-bar insets to every screen.

## 1.5.0

- Added a dedicated Northeastern University at Qinhuangdao EAMS adapter.
- Discovered the internal student id and current semester from the authenticated course-table page.
- Requested and rendered the EAMS personal timetable from the home page login session.
- Warned before saving a manually edited course that overlaps an existing active week.
- Reported existing-schedule conflicts in the import preview.
- Distinguished odd/even weeks, irregular week masks and adjacent time ranges.

## 1.4.0

- Rebuilt generic table scanning as a logical grid with rowspan and colspan support.
- Detected weekday columns from table headers before using positional fallback.
- Added adapter, page, table, iframe and candidate diagnostics to empty import errors.

## 1.3.0

- Kept weekday and date headers fixed while the timetable body scrolls vertically.
- Preserved seven-day phone layouts, today highlighting and body hit testing.

## 1.2.1

- Made the settings screen vertically scrollable while keeping its toolbar fixed.
- Kept permissions, backup controls and app information reachable on compact phones.

## 1.2.0

- Added a next-class summary to the timetable home screen.
- Added local JSON backup export/import with validation and merge/replace restore.
- Extended generic import scanning for SPA JSON state, data attributes and common grid IDs.
- Added backup parser regression tests.

## 1.1.0

- Redesigned the timetable for seven-day, no-horizontal-scroll phone layouts.
- Added a dedicated Northeastern University 2026 academic-system adapter.
- Added NEU term and campus discovery through the current WebView login session.
- Added the verified NEU 12-period timetable from 08:30 through 22:10.
- Added named custom academic-system URLs with adapter binding and local reuse.
- Added automatic adapter detection for `jwxt.neu.edu.cn`.
- Added asynchronous import progress, timeout handling and actionable errors.
- Added structured fields, table `rowspan` support and equivalent-session merging.

## 1.0.0

- Initial weekly timetable, course editing and local SQLite storage.
- Generic educational-administration page import.
- Overlap-safe automatic silent mode with original ringer restoration.
