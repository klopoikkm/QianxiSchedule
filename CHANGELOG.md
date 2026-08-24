# Changelog

## 1.18.8

- 修复主屏「下一节课」按星期几回绕、不按实际日期前进的问题。原先只在当前教学周里找 `dayOfWeek`，今天的课上完后会落到本周一那条记录，日期也用「本周一 + 星期几」推出来，周五晚上会把已经过去的周一写成下一节。现在按真实日历日期解析，并套用周次范围、单双周和 weekMask，显示的是这门课下一次真正上课的那一天。
- 本周后面没有课、当前周整周无课、开学前打开应用时，都会跨到后面真正有课的那一天；学期结束后显示「本学期已结束」，后面确实没有课时显示「本学期剩余时间没有课」。
- 摘要卡片改为按整分刷新，「正在上课」会在下课那一分钟准时变成下一节；跨过零点会重建整页，而不是继续沿用昨天的日期。
- 主屏每次刷新只读一次课程表（原先一次刷新要全表扫描四次）；下一节课解析按课程直接算出它最近一次上课的日期并从该课程的起始周开始扫描，不再逐天推进。
- 新增 12 个下一节课解析用例，覆盖当天进行中、当天下一节、周五跨到下周一、单周跳过双周、空周跨到后续周、开学前等待、学期结束、超出学期长度的周次和不规则 weekMask。

## 1.18.7

- 新增单门课程的独立上课时刻（课程编辑 → 上课时间，每个时间段第二行）。此前时刻只能整体来自作息表，实验课占第 6–8 节格子但实际 14:00–17:00、补课与作息不一致这类情况无法表达。节次仍决定卡片画在网格哪一行，时刻只影响静音闹钟和「下一节课」摘要，因此单独设置不会让格子跑位。点该行选起止时刻，再次点击选「跟随作息」或长按该行可撤销。
- 打开已有课程时按存储分钟数与节次的作息窗口比对识别既有覆盖并保留，不会在下次保存时被悄悄抹回作息时间；被单独设置过的课程在网格卡片上附加一行实际时刻，因为左侧时间轴显示的是作息表时间，不标出来看不见差异。
- 新增课表文字增强（设置 → 显示）：背景图片与文字明暗接近时字迹发虚，现可叠加半透明白或半透明黑蒙版，深色图片选前者、浅色图片选后者，默认不叠加。蒙版位于背景层与内容层之间，不影响课程卡片自身配色。
- 修复导出备份丢失课程节次位置的问题：`startNode`/`step` 自 1.16.0 起就是网格定位依据，但备份始终未写入，导入后全部课程回落到默认的第 1 节并挤在同一行。备份现已包含这两个字段；旧备份缺字段时按分钟数映射到当前作息的对应节次，而不是继续沉默地钉在第 1 节。该缺陷同时会使上述「时间是否被单独设置」的判定对每门课误判。
- 新增 2 个备份往返用例（含节次缺失的旧格式），全套 74 个单元测试通过。

## 1.18.6

- 撤回 1.18.4/1.18.5 把页面钟点映射到本地作息行的做法。清华按 6 大节排课（`第2节 9:50-12:15`），本地作息是 11 个 45 分钟小节，两套网格无法互相换算：`第3节 13:30-15:05` 只覆盖本地第 7 节 15 分钟会被丢弃，`第4节 15:20-16:55` 又从本地第 7 节中间开始，相邻大节必然抢同一行。放松重叠门槛只会让抢行更严重，所以改为不再猜测，页面节次编号恢复为网格定位的唯一依据。
- 导入时读取页面自己声明的节次结构并询问是否采用。识别到的 6 个大节起止时间会完整列出，选“采用页面作息”后覆盖当前作息表并重新解析，课程时间与网格位置一并按新作息落位；选“保留本地作息”则完全按原样导入，不做任何改动。
- 节次结构改为独立一遍扫描，与课程走哪条解析路径无关：清华页面的课程由 `td[id]` 路径接管，不经过 `scanTable`，此前挂在 `scanTable` 上的采集等于没有生效。
- 节次标签判定比课程定位所用的规则更严格，只认单节标签（`第1节`、`第三节`、纯数字）。跨节标签（正方的 `第1,2节 08:00-09:40`、`第3-4节`）和 `第一大节` 一律不计入：一个钟点同时对应两节，记下来会得出比页面更粗的作息。正方、强智这类页面因此不会弹出询问，导入行为与此前完全一致。
- 仅当页面节次自 1 起连续、至少 3 节，且与本地作息存在 10 分钟以上差异时才询问；节次已经一致的页面不打扰。
- 新增 4 个节次结构解析用例（含畸形数据剔除与无标签页面），导入解析共 43 个用例、全套 72 个单元测试通过；注入脚本另经 17 组标签样本与整表端到端校验。

## 1.18.5

- 修复 1.18.4 的钟点映射在真机上不生效的原因：大节钟点写在表格行的标签列，而按 `td[id]`／`data-day` 定位单元格的扫描路径不携带行标签上下文，`startTime` 始终为空，网格定位只能回落到页面自身的节次编号。这两条路径现在从所在表格行的标签列回取钟点，`8:00～9:35` 这类大节才能正确跨多行。
- 行内没有钟点时返回空并沿用原有节次推导，`scanTable` 路径的既有行为不变，其他教务的导入结果不受影响。
- 新增按行标签回取钟点的回归用例；导入解析 42 个用例、全套单元测试通过。

## 1.18.4

- 修复 1.18.1 起“全周/前八周/后八周”规则实际从未生效的根因：页面用全角波浪号书写范围（`8:00～9:35`），而解析器的范围分隔符字符类只含 `-~至—`，六大节时间指纹全部失配，作息 profile 恒为空。全部 32 处分隔符统一补入 `～－–`，周次、节次、钟点三类范围一并受益。
- 修复课程格“课程名(教师；属性；周次；教室)”在没有教室时整条丢失的问题：整行以 `全周)` 结尾会被误判为周次说明行。周次说明行改为要求整行只由数字、分隔符和周/节字样组成，仅以“周”结尾不再算。有教室的课以房间号结尾，此前侥幸未受影响。
- 课程名清理新增分号元数据组剥离：`(李铁夫；限选；全周)` 既无逗号也无数字，此前四条规则全部放过，课名会带括号入库。课名自带的“（第二外国语）（1）”不受影响。
- 修复大节课被压成一小节、晚间课被画到下午的问题：页面按 6 大节编号，用户作息为 11 小节时，页面节次号被直接当作网格行号（`第6节 19:20~21:45` 画到第 6 行 14:00）。页面给出真实钟点且用户已配作息时，改按钟点覆盖的作息行定位，行重叠过半才计入。未配作息时仍沿用页面编号。
- 撤回 1.18.2/1.18.3 依据“限选/任选/五教/六教/法律图书馆”识别作息结构的启发式：这些词在多所学校通用，会把其他教务误套为半学期切分规则。判定恢复为仅凭六大节时间指纹或“前八周/后八周”字样。
- 新增 8 个回归用例覆盖上述四处缺陷（全角波浪号的周次/节次/钟点、无教室课程格、纯周次说明行、大节跨行、粗粒度编号、无作息回退）；导入解析共 41 个用例、全套 70 个单元测试通过。

## 1.18.3（已撤回）

- 依据“限选/任选”等课程属性词和“五教/六教/法律图书馆”地点词识别作息结构，并将时间指纹门槛降至 3 个。这些词并非某一作息结构专有，会误伤其他教务；且未触及真正的根因（全角波浪号），实际仍走通用解析。已在 1.18.4 撤回。

## 1.18.2（已撤回）

- 尝试以“全周”等描述性词直接触发半学期切分规则，并调整描述性周次的提取优先级。同样未触及全角波浪号根因，profile 仍为空，规则未生效。已在 1.18.4 撤回。

## 1.18.1

- 修复清华及同类网页课表使用“全周”“前八周”“后八周”“单周”“双周”文字时被全部判定为缺少周次的问题：分别解析为 1-16、1-8、9-16 和对应单双周。
- 读取课表左侧每个大节的实际起止时间，例如 08:00-09:35、13:30-15:05；导入后的课程和自动静音使用页面时间，不再强套本地默认作息。
- 支持“五教5103”“六教A112”等教学楼简称，并拆分同一课程格中的课程名、选课属性、周次和地点。
- 新规则仅在页面命中六大节时间结构指纹时启用，正方、强智、EAMS、EMAP/Vue 及其他通用教务继续沿用原解析规则。

## 1.17.20

- 新增通用 Vue 2/Vue 3 运行时课表通道：从组件 `$data`、`$props`、`setupState` 等状态中提取符合统一课程字段的记录，覆盖 EMAP、报表组件及其他将课程保存在 Vue 状态而非格子文字中的教务。
- 结构化数据或 Vue 状态一旦成功产出课程，就跳过普通 DOM 扫描，延续 WakeUp 的“一个页面只走一个可信解析通道”原则，避免月历日期、菜单编号等页面装饰混入候选。
- 结构化遍历加入对象去重和节点上限，避免 Vue 循环引用导致重复解析或卡顿；诊断新增 `structuredCandidates` 与 `vueRoots`。

## 1.17.19

- 参考 WakeUp 的 `#kbtable` 专用流程增强强智教务：当通用表格识别没有候选时，直接按行、列和 `.kbcontent` 的“老师/教室/周次(节次)”元数据解析，并支持同一单元格中的 `-----` 多课程分隔。
- 强智框架首页（`/jsxsd/framework/xsMainV.htmlx`）没有课程数据时，导入提示会明确要求先打开“个人课表/我的课表”，避免把首页骨架误判为适配失败。
- 诊断新增强智专用回退计数，便于继续适配不同部署的强智课表页面。

## 1.17.18

- 按 WakeUp 的单解析通道模型重构 EAMS 当前页拉取：检测到已求值的 Beangle `table*.activities` 后只采用该运行时课表，不再继续扫描同一页面的可见 DOM 课程格，根除同一课表被两个来源各读取一次的问题。
- 同一课程详情的时段身份改用 `day + startNode + step`；网页钟点与用户作息略有差异不再制造重复，钟点仍独立用于静音调度。
- `IMPORT-RESULT` 新增实际采用的 `source`，真机日志可明确显示 `eams-runtime`、`page-dom` 或 `visual-grid`，后续适配不再依靠截图猜测内部字段。

## 1.17.17

- 修复同一课表同时被教务运行时活动对象和可见课程格读取后生成两份课程的问题。
- 导入去重现在能识别仅相差校区后缀的同一地点（例如 `工学馆204` 与 `工学馆204(学校本部)`），合并周次并保留教师、完整地点等信息更丰富的记录。
- 去重仍会保留真实不同教室的同名同时间课程，避免把确实存在的分班或教室变更错误合并。

## 1.17.16

- 修复旧 EAMS 课程格将周次缩写为 `(4-15,工学馆307)` 等格式时未识别的问题：现在能从“周次,教室”元数据中恢复真实起止周，并继续支持不连续周和单双周。
- 删除导入解析失败时静默生成 `1-20周` 的危险兜底；无法确认周次的候选会被忽略并在预览/诊断中明确计数，避免把错误课程写入整个学期。
- 网页扫描器与 Java 解析层同时支持无“周”后缀的经典 Beangle/EAMS 格式，其他教务已有的明确周次格式保持不变。

## 1.17.15

- 撤回 1.17.14 自动跳转标准课表页的错误交互：用户仍在内置浏览器中自行进入任意教务课表，“拉取课表”始终只扫描当前页面，不再替用户选择 EAMS 功能入口。
- 确认旧 EAMS 骨架缺少的不是单独 jQuery，而是教务公开登录页声明的完整 Beangle 基础 bundle；在未装饰 EAMS 主文档解析前，补回站点自身的 `jquery/jquery,/scripts/beangle/beangle.js` 与默认主题，其中同时提供 jQuery 和 `window.bg`。
- 修复仅按 `/eams` 厂商路径和 HTML 内容触发，不绑定学校域名；WebVPN/反向代理前缀会被原样保留。CAS ticket 请求、其他教务、已完整装饰页面及子资源不拦截。
- 为缺少外层定义的 `openUrl` 提供仅负责普通页面导航的兜底，并新增 `SHELL-REPAIR` 诊断，避免再次用本地 jQuery 冒充整套厂商运行环境。

## 1.17.14

- 登录后若进入旧 Beangle/EAMS 的 `homeExt.action` 内容骨架，App 现在自动绕过不可用首页并进入标准个人课表入口，不再要求用户在缺失菜单的页面中导航或先手动点击“拉取课表”。
- 自动入口仍只按通用 EAMS 路径识别，不判断学校域名；支持保留 WebVPN/反向代理前缀，并设置单次跳转保护，服务器若退回首页不会形成循环。
- 教务诊断首行新增 App 版本号，同时记录 `VENDOR-AUTO-ENTRY` 或 `VENDOR-AUTO-RETURN`，可直接区分未安装新版本、已进入课表页和服务器拒绝入口三种情况。

## 1.17.13

- 真机诊断已证实 1.17.12 的 EAMS 主文档请求没有携带 `X-Requested-With`，因此撤回无效的 AndroidX 请求头实验并恢复较小的安装包体积。
- 明确旧 EAMS 的 `homeExt.action` 是缺少外层框架的页面片段：它同时缺少 jQuery、`bg`、`openUrl` 等宿主依赖，单独注入 jQuery 无法修复完整导航。
- 参照 WakeUp 的导入流程，首次在任意 EAMS/Beangle 页面点击“拉取课表”时，按通用系统路径进入 `courseTableForStd.action`；用户选择学期后再次点击即可扫描当前课表。该规则不识别学校域名，并完整保留 WebVPN/反向代理前缀。
- 继续坚持只读取用户当前打开的课表页面，不恢复东北大学或东北大学秦皇岛的学校专属注入、接口与域名分支。

## 1.17.12

- 重新定位旧 EAMS 首页缺失 jQuery、bg、openUrl 的共同根因：Android WebView 的 X-Requested-With 应用标识可能使旧教务把主文档误判为 AJAX 请求，只返回未套用基础脚本和导航模板的内容片段。
- 使用 AndroidX WebKit 的正式接口关闭 X-Requested-With 默认请求头，让主文档请求与普通浏览器一致；不拦截资源、不改写 HTML，也不绑定学校域名。
- 教务诊断记录每次主文档是否仍携带 X-Requested-With，便于在真机直接验证修复是否由当前 WebView 内核支持。
- 对照 WakeUp 较新的 2020 派生源码，统一 CAS 到教务内容页的浏览器身份并允许网页脚本发起窗口导航，避免认证会话因中途切换 User-Agent 失效。

## 1.17.11

- 修复部分旧式 EAMS/教务首页登录成功后只显示蓝色菜单条、报 jQuery is not defined 的问题：认证路径继续使用移动端浏览器身份，登录后的教务内容页统一使用桌面浏览器身份，避免服务器向 WebView 返回不完整的移动版壳。
- 禁用导入页旧脚本缓存，避免早期版本的资源改写或错误响应继续污染 jQuery/菜单脚本；登录 Cookie 和教务会话不受影响。
- 新增通用“列表课表”扫描：可从带有课程、星期、节次/时间、周次、教师、地点列的表格读取课程，不依赖固定学校接口；同时增强青果/URP 常见字段别名、节次范围和短课程名处理。
- 诊断新增列表行数量，便于区分已进入列表式课表但字段不匹配，还是尚未进入个人课表页面。

## 1.17.10

- 通用扫描器新增 Beangle/EAMS 运行时解析：从当前页面及同源 iframe 的 `table*.activities` 读取已由网站脚本求值的课程名、教师、教室和 `vaildWeeks`，合并连续节次并修正二进制周次；不绑定学校域名，也不请求固定接口。
- EMAP 结构化解析补齐 `weeksAndTeachers`、`titleDetail`、起止周、起止节次和明确时刻字段，支持同一课程在不同周次/教室的多条详情，东北大学主校区等 JWAPP 页面可直接复用。
- 导入诊断增加“运行时活动对象”数量，便于区分 EAMS 页面已正常生成课表数据但字段解析失败，还是用户尚未进入个人课表页面。
- 最终内嵌扫描脚本除 Java 单元测试外，增加独立 JavaScript 语法校验和模拟 Beangle 运行时对象回归验证。

## 1.17.9

- 教务导入按 WakeUp 的流程重构：内置浏览器只负责登录与普通导航，用户进入个人课表后统一读取当前页面、同源 iframe 与 Shadow DOM，再按正方、强智、青果、Beangle/EAMS、EMAP 页面结构识别课程。
- 删除东北大学和东北大学秦皇岛的专属运行时适配，包括固定接口、WebVPN 资源转发、主文档改写、本地 jQuery 注入、强制桌面视口和学校域名路由。旧版本保存的 `neu` / `neuq-eams` 配置会自动迁移为“自动识别”。
- 所有教务类型现在运行同一套当前页扫描脚本；`/jwglxt`、`/jsxsd`、`/urp` 只用于提前标记通用厂商类型，`/eams`、`/jwapp` 与 VPN 长网址由页面内容指纹判断，不再触发学校专属加载。
- 导入课程的节次时间统一使用用户当前作息，页面包含明确起止时刻时仍以页面为准；不再为任何学校内置一套不可修改的特殊上课时间。

## 1.17.8

- 东北大学秦皇岛旧 EAMS 的 jQuery 修复升级为 WebView document-start 注入：本地 jQuery 在网站任何内联脚本之前执行，解决部分手机虽然记录 `HTML-INLINE-JQUERY` 但脚本实际未运行、仍报 `JQUERY-STILL-MISSING` 的问题；脚本内部限制为 `jwxt.neuq.edu.cn`，其他教务不受影响。
- 导入的课程时间改为按用户自己的作息表推算，不再套用内置的 08:00 默认节次表：跨校导入后课程格子的时间和静音闹钟与课表界面显示的节次严格一致。页面本身给出 `13:30-15:10` 这类明确时刻时仍以页面为准；东北大学适配器保留自己已核对的校历。
- 节次查表优先匹配作息表里标注的节次号，再退回列表位置，手工改过节次编号（例如只保留下午 5–7 节）的作息不会再整体错位。
- 新增多套作息表（设置 → 上课时间 → 切换作息）：可按夏/冬季、双校区或不同学期分别保存节次时间并随时切换，当前作息同时驱动课表网格、课程编辑和按节次导入。老用户原有的单份作息自动迁移为「默认作息」，保存时同步写回旧字段，降级也不丢配置。
- 补齐更多教务的节次写法：`第2大节`（一大节等于连续两节）、`第3,4,5节` 这类逗号/顿号列表现在按实际跨度定位，不再只取第一个数字。
- 周次解析支持把单双周写在范围前面的格式（`单周1-16`、`双周 2-16周`），以及 `1-5,9-16周(双)` 这类混合列表加尾部单双的组合。
- 教室识别扩展到 `4#201`、`A-303`、`综合楼B区201`、`综合楼-234`、`教学楼 A305` 等分隔符写法；教师栏继续过滤 `实验员`、`带教`、`外聘`、`待定`、`单周` 等非人名词。
- 修复体育、线性代数、高等代数等较短课程名被当作噪声丢弃的问题：页面结构化字段给出的课程名一律信任，猜测得到的短名单独放行常见学科词。
- 修复课程节次超出当前作息节数时整门课在网格里不可见的问题（例如切到只含下午几节的作息，或导入的课表节数多于本地配置）：网格按课程实际用到的最大节次自动加长，作息未定义的那几行只显示节次号并标注「当前作息未设置时间」。

## 1.17.7

- 修复海南师范大学等正方页面点击拉取时报 `node.getAttribute is not a function`：所有 DOM 属性和选择器访问先检查函数能力，页面脚本包装非 DOM 数据时自动按纯文本或缺省字段处理，不再终止导入。
- 教务诊断现在同时记录课表拉取阶段：包含识别器、页面结构、表格/iframe/Shadow DOM 数量、候选数、有效课程数及解析异常；网页加载正常但导入失败时不再显示毫无线索的“最近 0 条”。
- 修复浙江大学正方单双周课表点击拉取时报 `part.replace is not a function`：课程格分段内容先转换为原生字符串，单个异常节点也不再中断整张课表扫描。
- 修复东北大学秦皇岛旧 EAMS 登录后首页再次退化为无菜单骨架：本地 jQuery 改为在主文档解析前直接内联，不再依赖部分 WebView 不会触发的虚拟脚本请求；注入源码转为纯 ASCII，兼容该站点的 GBK 页面。
- 教务导入流程进一步对齐 WakeUp：WebView 只负责登录和导航，用户进入“我的课表”后，应用统一读取当前页面及最多四层同源 iframe，不再要求东北大学必须位于固定域名或调用固定 API。
- 修复浙江大学正方教务页面被应用误换成 jQuery 1.7.2，进而触发 Bootstrap 版本错误、菜单空白的问题。旧 jQuery、主文档注入、资源转发和 1280px 桌面视口现在仅对东北大学秦皇岛旧版 EAMS 生效。
- 新增正方 `/jwglxt/`、强智 `/jsxsd/`、青果/URP `/urp/` 路由识别；兼容东北大学 WebVPN/代理长网址以及自定义教务入口。
- 通用解析器增强正方 `#kbgrid_table_0`、`#kbtable`、嵌套 `.kbcontent` 和同一格多门课程分隔解析，并继续保留秦皇岛 EAMS 的 `TaskActivity` 周次数据兜底。
- 修复浙江大学正方课表的“星期下分单/双两列”布局：14 个课程列现在正确归并到 7 天，单/双周随课程保存，跨列表头不再被误导入为每天一门课。
- 修复课程文本里的 `2节/周`、`3节/周` 被误当成第 2/3 节，课程位置以表格行和跨行高度为准；支持中文“第一节”至“第二十节”行标签。
- 增强东北大学及其他 EMAP/现代教务页面：识别 `KCMC/KSJC/JSJC/JSMC/JASMC/ZCMC` 等常见结构化字段和页面初始状态；导入失败时显示候选摘要，便于继续适配未知页面。
- 修复东北大学主校区动态课表只抓到 `28/29/30` 等底层格子编号的问题：纯数字格子不再作为课程；标准表格未产出课程时，按星期表头、节次锚点和可见课程卡片的位置计算星期、起止节次。
- 可视课表解析改为页面结构自动触发，不绑定学校域名；解析顺序为“结构化数据 → 标准表格 → 可视卡片”，前一步成功后不重复运行后一步。
- 明确分离东北大学主校区 EMAP/JWAPP 与东北大学秦皇岛 Beangle EAMS：主校区读取当前动态课表，秦皇岛继续使用 `courseTableForStd/TaskActivity` 专用解析器。
- 按 WakeUp 的页面算法补齐旧正方、新正方和强智结构：支持旧正方同格多个 `{周次}` 课程分段；读取新正方 `.title` 与 `p[title="教师/上课地点/节/周"]`；强智 `kbtable` 按 `1–2、3–4、5、6–7、8–9、10–11、12` 节分组定位。
- 扩展常见教务 JSON 字段别名，包括 `XQJ/SKXQ、KSJC/JSJC/JCS、KCMC/KCM、JSMC/JSXM、JASMC/JXCDMC、ZCMC/SKZC/ZCD`，并支持节次字段直接携带 `1-2` 范围。
- 增加 Shadow DOM 深层扫描，现代 Web Component 课表不再因课程节点位于组件内部而漏抓；页面初始状态同步兼容 `__NEXT_DATA__` 与 `__NUXT__`。
- 教务类型由单一 URL 判断升级为内容指纹评分：综合厂商路径、脚本标记、课表 DOM 与课程字段选择解析器，并在失败诊断中显示命中结构和分数。
- 东北大学秦皇岛专用 EAMS 解析超时、异常或返回空结果时，自动回退到当前页面的结构化数据、标准表格和可视卡片扫描，不再直接结束导入。
- 修复页面实际识别为通用结构时仍沿用旧手动教务类型的问题，课程时间不再被错误套用其他学校的节次配置。

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
