# 公开信息页筛选区 UI 调整

本文件按先后记录筛选样式、日期交互和紧凑吸顶布局。前文“业务脚本未改”和 datetime-local 的描述只对应当次调整；当前日期与布局以末尾后续各节为准。

日期：2026-11-03。范围为 `/cms/pages/items/public.html` 的筛选表单和该页加载的新组件资源。未改后端、既有查询/分类/分页业务脚本、共享 base.css/modules.css、侧栏、页头、字体或其他业务页面；未提交、未推送。

## 焦点样式来源

原 `assets/css/base.css` 的 `--focus: #926519` 与全局 `:focus-visible { outline: 3px solid var(--focus); outline-offset: 4px; }` 形成棕色粗外圈，并与控件自身边框隔开叠加。没有发现独立的筛选容器 focus-within 粗框规则；普通聚焦不是校验错误。

新增 `public-filters.css` 仅在 `#filter` 中覆盖 input/select/button 的 focus 与 focus-visible：深蓝 `#173e58` 边框、2px 浅蓝 `#c6dfed` outline、0 offset、无叠加 box-shadow。键盘焦点仍清楚可见，没有全局取消 outline。只有 `aria-invalid=true` 的实际错误状态使用错误色；强制颜色模式使用系统 Highlight。

## 表单布局与下拉组件

筛选区使用 grid、工具栏使用 flex，16px 间距，44px 控件高度，统一细边框和既有圆角。关键词占两列；关键词、类型、分类、地点、开始时间、结束时间、处理状态、排序依据、排序方向都有可见标签。排序与查询分组，查询按钮沿用站点深蓝色；窄屏逐步降为两列/一列。

现有项目没有自定义下拉组件，新增无依赖的 `Cms.Select`（`assets/js/select.js`、`assets/css/select.css`），仅增强标记 `data-cms-select` 的单选框。目前只由公开信息页加载。

- 原 select 保留 name/value 和表单归属，增强后隐藏原生视觉控件；原 FormData、URL 参数和事件行为继续使用原 select。选择时发送 input/change，按钮为 type=button，Enter 选项不会误提交查询。
- 触发器使用 combobox、aria-expanded、aria-controls、aria-activedescendant，菜单为 listbox、选项为 option 并标记 aria-selected/aria-disabled；原标签关联到触发器。
- 菜单为白底、浅灰边框、轻阴影，选项有内边距；悬停/活动项为浅蓝底，选中项为深蓝粗体及对勾。
- 支持鼠标、方向键、Home/End、Enter/空格确认、Escape 取消、Tab 继续移动、点击外部关闭和焦点离开关闭；关闭后不会无故抢走外部控件的焦点。
- 菜单挂到 body，使用 fixed 定位并按视口选择上下空间，避免被筛选容器裁切；窗口尺寸/滚动变化时重定位。
- 监听原生 change、表单 reset、选项和 disabled 等变化；可通过实例 destroy 清理监听并恢复原 select。现有 URL 初始化发生在增强之前，分类后续追加选项由 MutationObserver 同步；未另写分类请求。

## 实际验证

使用已运行的独立 H2/Tomcat 演示服务，在 Chrome 实际操作；以下是 AI 浏览器检查，不等同于袁运轩本人验收。

| 检查 | 实际结果 |
| --- | --- |
| 默认与输入聚焦 | 浏览器截图确认标签、分组和间距；控件实测约 43.99px 高。输入框 computed style 为深蓝边框、浅蓝 outline、0 offset、box-shadow none。当前浏览器缩放使 computed outline 显示约 1.67px，源码为 2px。 |
| 下拉展开 | 键盘空格及鼠标均可打开。实际看到白色菜单、轻阴影、选中对勾和浅蓝活动项；菜单父元素为 BODY。 |
| 键盘 | 方向键和 Enter 可将类型提交为 LOST；Escape 不提交临时活动项；Tab 关闭并从处理状态继续到排序依据；Home/End 可选择首/尾选项；焦点留在相应触发器。 |
| 外部关闭 | 打开分类后点击地点输入框，菜单全部关闭，焦点保留在地点。 |
| 查询参数与结果 | 鼠标选择 OPEN、eventTime、asc，配合类型 LOST 查询，URL 保留原值并返回一条独立测试记录；关键词“演示”和地点“隔离”也能参与原查询。 |
| URL 回填 | 刷新带 keyword/itemType/location/from/to/resolutionStatus/sortBy/direction 的 URL 后，输入值和增强下拉显示与原 select 一致。 |
| 窄屏 | 实际 DOM 视口宽度为 400px、316px 时检查截图与控件边界，document scrollWidth 等于 clientWidth，没有筛选控件越过视口，控件高度仍约 44px；菜单在视口内且不被表单裁切。浏览器的临时视口设置已恢复。 |
| 静态检查 | `node --check assets/js/select.js` 与 `git diff --check` 通过。原 inline 业务脚本逐字符相同，SHA256 为 `a2d8b11427d6a492805e0ac13a3b5554015d9a87435e36336e8b3f89892c78ed`。未运行后端测试或全量构建。 |

分类的真实远程选项加载受下述首屏错误影响，当前只能验证“全部分类”默认项的展开/关闭，不能把真实分类树筛选验收记为通过。没有为演示注入或伪造分类选项。

## 单独登记给高鸣远的既有问题

### 首屏 Cms is not defined

位置：`pages/items/public.html` 原 inline 初始化脚本，以及 head 中 defer 加载的 api.js/shell.js。浏览器刷新公开页实际显示 `Cms is not defined` 和“加载失败”；inline 初始化先于 defer 脚本运行。

影响：首屏分类和公开列表初始化失败；脚本随后加载后，用户点击查询可重试列表。新下拉只同步现有 select，不重试分类接口、不等待 Cms 后重跑旧初始化、不隐藏或替换原错误。本次原 inline 脚本保持原样，问题继续由高鸣远协调处理。

### 含时间条件的原查询返回 400

实际操作：填写 `from=2026-11-01T08:00`、`to=2026-11-04T18:00` 并查询，页面显示原公共请求封装的参数错误提示；本地请求日志记录 GET `/cms/api/public/items` 为 400。清空两个时间条件，保留关键词、类型、地点、处理状态和排序后可返回测试记录。

本次时间控件的 name、datetime-local 类型、原参数拼接和原查询处理均未改变。只读检查发现 `PublicArticleQuery.from/to` 使用 LocalDateTime，未在此字段看到显式日期格式声明；是否为绑定配置问题需高鸣远进一步确认，本记录不将推测写成已确认根因。本轮不修改日期参数、后端绑定或业务校验，含时间条件的业务查询仍未通过验收。

## 查看方式

已打开预览：`http://127.0.0.1:8094/cms/pages/items/public.html`。该演示服务直接读取源前端文件；本次没有重新打包旧 `target/cms.war`。本次 CSS/JS 通过内容 hash 更新资源版本，正常刷新即可取得新样式。演示使用独立测试数据，业务库未改动。


## 后续日期筛选交互调整

本节记录 2026-11-03 随后的日期交互任务。用户明确允许修改相关前端参数构造与展开交互，保持后端 from/to 契约和业务规则。

### 实际代码

- `pages/items/public.html` 将两个时间输入改为 date，组合为“丢失／拾获日期（选填）”，分别标注“起始日期”“截止日期”，说明按事发日期筛选，与发布时间、认领期限无关。默认两端留空。
- 日期、处理状态、排序放入原生 details/summary 的“更多筛选”，主区域只有关键词、类型、分类、地点；查询按钮在 details 外，折叠时也可提交。折叠不禁用/重置字段，提示已选日期、状态或非默认排序；默认 publishedAt/desc 不计入已启用条件。
- 新增 `assets/js/public-date-filters.js`，仅处理本地年月日字符串：from 为 `YYYY-MM-DDT00:00:00`，to 为 `YYYY-MM-DDT23:59:59.999999999`。不构造 UTC 日期，也不调用 toISOString，不发送纯日期代替 LocalDateTime，不截断纳秒或改成可能漏数据的较早截止时间。
- 旧 URL 中的日期时间截取本地日期部分回填，再依照本次整天语义提交；其余筛选值、原分页与排序结构保留。修改条件点击查询仍回到 page=1，恢复旧链接时保留原 page。
- 起始日期晚于截止日期时在日期字段旁提示“起始日期不能晚于截止日期，请调整日期范围。”，标记 aria-invalid，并在提交时展开区域、聚焦起始日期，阻止网络查询。同一天可提交。
- 复用已有选择组件与深蓝焦点样式；新增分组只调整 `assets/css/public-filters.css`。advanced 内日期在窄屏变为单列，处理状态和排序有正常标签及间距。

### 自动与浏览器验证

新增 `src/test/js/public-date-filters.test.cjs`，`node --test` 共 8 项通过，覆盖空值/单边、同日完整截止边界、跨天跨年、逆序和闰日、旧日期时间链接、非法日期不被自动滚动到相邻日期、四个 TZ 下边界一致、高级提示判断。新 JS 和页面 inline 语法检查、git diff --check 通过。未运行后端全量测试。

Chrome + 当前隔离演示服务实际检查：

| 场景 | 前端实际结果 | 接口或限制 |
| --- | --- | --- |
| 全部不填 | 高级区域默认折叠；查询按钮可用；不发送 from/to | HTTP 200，返回两条隔离数据。 |
| 只填起始 | 只发送 `from=2026-11-01T00:00:00` | 当前服务 HTTP 400，字段 from 类型转换失败。 |
| 只填截止 | 只发送 `to=2026-11-03T23:59:59.999999999` | 当前服务 HTTP 400，字段 to 类型转换失败。 |
| 同一天 | 允许提交，00:00:00 到 23:59:59.999999999 | 当前服务日期绑定仍返回 400；未将前端允许提交记作查询成功。 |
| 跨天 | 生成两端各自的本地全天边界，保留其他筛选 | 当前服务日期绑定仍返回 400。 |
| 起始晚于截止 | 就近中文错误、展开并聚焦字段；URL 保持上次有效查询 | 演示服务公开列表请求计数提交前后均为 17，新增请求为 0。 |
| 刷新及旧链接 | 旧 `2026-11-01T14:30` / `2026-11-03T09:15:00.123456` 恢复为日期；日期、状态、自定义排序使区域自动展开 | 保留 keyword/itemType/location/resolutionStatus/sortBy/direction/page=3，提交边界改为完整日；刷新后仍能恢复。 |
| 修改后折叠查询 | 改为 11 月 2 日，折叠后查询，日期及状态均保留，page 从 3 回到 1 | 不因折叠丢弃高级参数。 |
| 键盘、菜单、窄屏 | Enter 可展开 summary；展开的自定义菜单在折叠区域时关闭且不清空条件。桌面与实测 316px CSS 视口检查无筛选控件越界，日期和高级控件按单列排列 | 临时窄屏覆盖已恢复。 |

以上为 AI 自动/浏览器核对，没有标记成人工逐行检查或用户亲自验收。

### 后端解析的明确限制

只读请求直接验证当前运行接口 `/cms/api/public/items`：无日期为 200；ISO 起始午夜、纳秒截止时间、同日完整范围均为 400。错误 data 分别是 `from: 字段类型不正确`、`to: 字段类型不正确`；同时提供两端时两字段均报错。因此限制不仅是纳秒精度，当前 Web 绑定连完整 ISO 午夜时间也未接受。

相关源码 `PublicArticleQuery.from/to` 为 LocalDateTime，当前字段未声明 DateTimeFormat，SpringMvcConfig 也未提供相关日期格式注册。SQL 按 `a.event_time >= from` 和 `a.event_time <= to` 筛事发时间，但请求在绑定阶段已经失败。已将限制交接到本记录供高鸣远协调处理；本次没有修改 DTO、MVC 配置、SQL 或已合并规则。上述三个后端文件与本次开始快照逐字节一致。

前端按完整边界实现，当前后端的日期查询端到端仍不通过。没有静默改用 23:59:59、毫秒精度或忽略日期来制造“成功”。原首屏 Cms is not defined 也仍按之前约定保留并交高鸣远，本次没有重排其历史初始化。

本次仅涉及 public.html、public-filters.css、新日期辅助 JS 和对应前端用例/记录，没有提交、推送或重新打 WAR。演示服务继续直接读取源前端资源。


## 后续紧凑布局与吸顶调整

本节对应随后提出的公开信息页内容布局任务，仅涉及 public.html、public-filters.css 和新增 public-items-layout.js。没有修改共享 base.css、modules.css、shell.js、select.js、日期辅助函数或后端。现有 query/date/category/pagination inline 脚本与任务开始时逐字相同，SHA256 为 `9785ae3b8cf463af87e66076e8f070ca62197278c8281daec2e31f2cde101df0`。

### 实测依据和实施

开始时浏览器的 scrollingElement 为 HTML，body/workspace/main/form 的 overflow-y 均为 visible，没有单独的内部滚动区。原 topbar 的 position 实测为 static，高约 112.61px，并非已经固定。

本次仅在公开页用 body 类限定布局：将原页头设为 sticky，保留其内容、配色、宽度与高度，现有顶栏页面名称由 P 改为唯一 H1，保持原字体和行高；移除正文重复大标题。主内容 padding-top 为 24px，不使用垂直居中或占位高度。

主筛选栏约 98px 高，字段与查询按钮在宽桌面同行，控件约 42px、间距 12px。“更多筛选”作为旁边的次要按钮，使用 aria-controls/aria-expanded 与原 details 同步，已启用条件保留可视提示和屏幕阅读器说明。日期等高级内容在单独的普通流区域展开，不属于吸顶盒子；收起时该区域无边框、内边距或占位高度。

表单包含结果与分页，以便主工具栏的 sticky 包含块覆盖列表长度；分页按钮显式设置 type=button，避免变成表单提交按钮。结果位于工具栏/展开区之后约 22px，空状态适度留白，分页在结果后 12px，无额外 min-height。

public-items-layout.js 通过 ResizeObserver 测量页头及工具栏实际高度，不在每次滚动时改位置。窄屏/低高度使用普通流；当换行后的页头与工具栏总高度超过可用空间的约 35% 或 240px 时，也关闭工具栏吸顶。实际高度不足时不会让大块表单固定占屏。焦点滚动留白只作用于结果链接与高级字段，不给主筛选控件设置全局滚动预留。已有下拉菜单继续挂在 body，避免被布局容器裁切。

测试中发现深处滚动时主动展开高级区域可能在视口外，因此增加仅在用户主动展开且面板不可见时定位到工具栏下方的处理；继续向下滚动后，面板正常滚走，不一直占据吸顶空间。

### 实际浏览器验证

使用原 8094 演示以及另起的 8095 独立内存数据库预览。8095 在临时测试夹具中增加 30 条长标题记录，合计 32 条，以真实接口返回每页 10 条进行滚动检查；没有在产品页面注入假数组，也没有改 8094 数据或业务库。临时夹具位于 /private/tmp，不进入仓库与 WAR；检查后已关闭 8095 标签页和服务，原 8094 服务与数据保留。

| 场景 | 实际结果 |
| --- | --- |
| 初始页面与标题 | 仅一个 H1，位于顶栏；原页头高度仍约 112.61px，工具栏距页头约 23.99px，宽桌面四字段、查询和次要入口同行。 |
| 长列表吸顶 | 真实接口返回 10 条长标题记录，总 32 条，页面高约 1180px；滚动到约 563.89px 后，页头 top=0，工具栏 top≈112.99px、高≈98.09px，没有遮住页头。 |
| 下拉与焦点 | 吸顶状态的类型菜单完整展开，菜单在 BODY；实际鼠标点击及键盘空格打开前后 scrollTop 均保持 563.89px。Escape/Tab 正常关闭和移动。键盘进入高级日期字段时字段处于工具栏下方可见区域。 |
| 高级区 | 通过键盘从主字段到查询、更多筛选，再 Enter 展开，状态同步。深处打开后面板可见；继续滚动，面板 top 变为负值而主工具栏仍在约 113px，证明高级内容不是固定占位。 |
| 分页 | 点击下一页后 URL 与页码均为 page=2，没有因表单结构调整意外重置到第 1 页。 |
| 空结果 | 输入不存在关键词后显示“暂无符合条件的信息”；列表区约 57.20px 高，结果距主栏约 22px，分页距列表约 12px，没有高度撑开。 |
| 高级启用提示 | 选择 OPEN 后提示已启用，表单状态及原筛选值保留。 |
| 换行/窄屏 | 实测 816px 时工具栏自然换行并按高度降为 static，避免大型吸顶区；316px 时控件未越过视口，scrollWidth=clientWidth，页头与工具栏均 static。 |
| 低高度 | 实测视口高度 444px，页头及工具栏均 static。临时视口覆盖已恢复。 |
| 静态检查 | 新 JS 与原 inline 脚本 node --check、git diff --check 通过；没有运行后端测试或全量构建。 |

这些是 AI 浏览器核查结果，没有写成人工逐行检查或袁运轩亲自验收。原 Cms is not defined 首屏问题与日期解析 400 的处理归属仍按前文，本次没有修复或掩盖这些历史问题。未提交、未推送。


## 后续地点自由填写与查询提示

本次只修改公开页的地点标签/占位文字，以及现有编辑页的地点说明与局部换行样式。没有新增地点字典、下拉选项、别名识别或输入改写；两页原脚本与本次开始快照完全一致。

- public.html：标签为“地点关键词（选填）”，占位为“如：教学楼、图书馆、食堂、宿舍”。继续沿用原 location 查询参数和分页逻辑。
- edit.html：保留“地点”标签、自由文本、required、minlength=2、maxlength=100；通过 aria-describedby 关联提示“请填写建筑名称及具体位置，如：第一教学楼302教室、图书馆二楼、三号宿舍楼入口。”局部网格及 muted 提示允许自然换行。
- 当前工作区未找到信息新增页（现有新增后端接口不等于存在新增页面），因此新增页提示尚待接入；没有为了增加提示另建新增流程。

### 原查询与校验核对

PublicArticleQuery.locationPattern() 对空值/空白返回 null，非空值转义 LIKE 特殊字符并加两侧百分号。ArticleMapper.xml 的 publicWhere 仅在 pattern 非空时追加 `a.location LIKE #{query.locationPattern} ESCAPE '!'`，是 MyBatis 参数绑定的包含匹配。因此本次直接复用，无后端差异。CreateDraftRequest 与 EditArticleRequest 的地点必填、2～100 字限制未改动。以上四个后端文件已与本次开始的 SHA256 快照对比一致。

### 实际验证（AI 浏览器核查，非用户人工验收）

使用独立 8095 Spring/Tomcat + H2 内存隔离数据，新增三个公开地点示例和一条本人草稿，仅供本次查询/提示检查，不写现有业务库。

| 场景 | 实际结果 |
| --- | --- |
| 地点留空 | 查询返回全部 5 条公开隔离记录，URL 不含 location 参数。 |
| 教学楼 | 返回 1 条，地点为“第一教学楼302教室”。 |
| 图书馆 | 返回 3 条，包括“图书馆二楼”“图书馆东门”及原图书馆演示记录，证明复用包含匹配。 |
| 编辑提示与自由输入 | 通过真实隔离账号 Session 打开本人草稿；提示显示，required/2/100 与 aria-describedby 均在。输入“实验楼北侧停车场”原样保留，未保存修改。 |
| 桌面和窄屏 | 公开标签、占位及编辑提示可见；316px CSS 视口两页 scrollWidth=clientWidth，地点字段不越界，编辑说明自然换行。临时视口覆盖已恢复。 |
| 变更检查 | 两页脚本与开始快照一致，四个相关后端文件不变，git diff --check 通过。未运行后端全量测试或重打 WAR。 |

原公开页首屏 `Cms is not defined` 在重新打开时仍可见，按既有约定继续交高鸣远；没有隐藏错误或修改初始化。查询验证在公共脚本加载后主动点击“查询”完成。日期绑定历史问题也没有在本次处理。8095 临时标签页/服务检查后关闭，原 8094 演示服务与数据保留。未提交、未推送。


## 本地运行版本同步（2026-11-03）

按用户反馈核对后，确认源码 public.html 为新版，target/cms 与原 WAR、原 target/local-tomcat/webapps/cms 仍是旧版。实际监听 8094 的 PID 89804 是 YyxR4BrowserServer 临时 H2 预览（工作目录为本项目，context docBase 指向 src/main/webapp），并非当时正在运行 LocalServer。重启前无缓存 HTTP 请求已与源码一致，所以不能将该预览里的旧浏览器显示单独归因为 WAR；构建产物确实落后，需要同步。此前记录“源预览不重打包”的说明描述的是当时状态，不再代表本次完成后的启动方式。

使用既有 JDK 11（/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home）、项目 .mvn/settings.xml 和 .cache/m2 缓存执行：

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home mvn -gs .mvn/settings.xml -s .mvn/settings.xml -Dmaven.repo.local=.cache/m2 -DskipTests -B -ntp package
```

首次沙箱内依赖仓库 DNS 访问失败；获准在沙箱外重跑后 BUILD SUCCESS（2:05），Tests are skipped，没有重跑全量测试。新 WAR 和 target/cms 中公开页逐字节等于源码。

仅停止已确认的 8094 旧进程 89804。原生成部署目录移至 /private/tmp/yyx-cms-deployment-before-20261103-123911 备份，没有清理源码或业务库。改用仓库现有 LocalServer 从 target/cms.war 启动，Tomcat base 为 target/local-tomcat，context /cms，展开目录 target/local-tomcat/webapps/cms；启动后监听进程 PID 20510。

首次启动命令把完整 target/test-classes 放入父类路径，导致测试 Mapper 被扫描而出现 BaseMapper 类加载失败；未修改业务源码，按 scripts/common.ps1 的既有方式将启动工具复制至 target/dev-tools，仅用该目录、Tomcat 和注解 API 作为启动类路径后启动成功。启动命令保存在本机临时脚本 /private/tmp/run_yyx_war_8094.sh，日志 /private/tmp/yyx-war-8094.log。使用已有被忽略的 jdbc-local.properties，没有初始化、迁移、重建或清空任何数据库，也没有停止其他服务。原内存 H2 预览随旧进程退出，当前入口使用项目现有本地数据库配置。

### 实际 HTTP 和浏览器验证

只读请求公开页及其全部直接引用资源：HTML 1 个、CSS 4 个、JS 5 个均 HTTP 200；每个响应体与源码、新 WAR、target/cms、运行中的 Tomcat 展开目录逐字节一致。核对结果含 SHA256，保存在 /private/tmp/yyx-deployed-ui-http.json，检查脚本 /private/tmp/yyx_check_deployed_ui.py。

Chrome 对 8094 已打开的页面执行刷新并展开“更多筛选”，确认仅顶栏一个 H1，main 中没有重复大标题；紧凑工具栏、地点关键词（选填）及占位提示可见；高级区包含丢失／拾获日期说明、起始日期/截止日期（均 type=date）、处理状态与排序。实际截图 /private/tmp/yyx-new-war-public.jpg。

新版 WAR 的浏览器首屏仍显示 `Cms is not defined`，已明确复现；版本同步没有解决此历史脚本初始化问题。本次没有重排脚本、隐藏错误或扩大到业务修复，继续交高鸣远。

正确入口： http://127.0.0.1:8094/cms/pages/items/public.html 。本次操作没有改动任何应用源码或私密配置；完成前逐文件 SHA256 和 git status 与操作前一致（随后仅追加本节部署记录）。未提交、未推送。


## 信息模块公开侧栏入口

仅在 pages/items/module.json 的 entries 首项增加 `{"label":"寻物招领","page":"pages/items/public.html"}`，不设置 permission。原“我的信息” item:view:own、“信息管理” item:view:any 的对象及顺序逐项保留。沿用 shell.js 已有 manifest 读取、权限过滤、URL 匹配高亮，不修改公共布局、导航脚本或后端权限。共享脚本、全部后端源码/资源和 CSS 已与本次开始的指纹核对一致。

使用现有 JDK 11、项目 Maven 配置执行 -DskipTests package，BUILD SUCCESS（0.591 秒），未运行后端全量测试。新配置已进入 target/cms.war 和 target/cms，并仅将该静态 JSON 更新到正在运行的 8094 Tomcat 展开目录 target/local-tomcat/webapps/cms/pages/items/module.json；无需重启或操作数据库。HTTP 实际返回 200，响应体与源码/构建产物一致，SHA256 d5ddc8779cbcdb5b5e9b5061b86020627dd3fb34441e15835da441da7f6345b3。

AI 浏览器实测（不是用户人工验收）：

- 8094 真实本地实例，匿名刷新后显示“寻物招领”，不显示“我的信息”“信息管理”。从侧栏“校园首页”再点击“寻物招领”，实际进入 public.html，公开入口具有 active 和 aria-current=page，首页不高亮。截图 /private/tmp/yyx-menu-guest.jpg。
- 普通用户检查复用 8095 独立 H2/Tomcat 夹具和真实 EDITOR Session；登录后显示“寻物招领”“我的信息”，不显示“信息管理”。点击公开入口跳转正确且仅该项高亮；点击“我的信息”后高亮切至该项，公开入口取消高亮。截图 /private/tmp/yyx-menu-user.jpg。没有向 8094 本地业务库新增账号或写入数据；8094 的真实普通账号登录未另行验证。隔离测试标签页和 8095 服务已关闭，8094 保持运行。

原首屏 Cms is not defined 仍存在，没有在菜单任务中修复或隐藏。git diff --check 通过，未提交、未推送。
