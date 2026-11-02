# 高鸣远第二轮：公共页面与请求组件

实现日期：2026年10月23日；提交复核日期：2026年10月26日。基于第一轮总合并 `59b02b8` 开发，通过 `feature/gaomingyuan-base` 分支交付，未合入 develop。其他成员第二到第四轮仍按各自计划交付。

## 本轮目的和成果

为各模块提供同一套页面外壳、登录状态、同源请求和分页，让组员在独立页面内开发，不必同时修改首页。沿用真实 SSM 接口，不新增 Spring Boot、生产 Java 依赖、数据库迁移或业务工作流。

| 文件 | 用途 |
| --- | --- |
| `assets/js/api.js` | 返回 Result.data 的请求封装、CSRF、取消与超时、安全错误提示 |
| `assets/js/shell.js` | 公共导航、页头页脚、会话登录与退出、权限入口 |
| `assets/js/pagination.js` | 总数与页码、每页条数、前后页、加载期间防重复操作 |
| `assets/js/home.js` | 首页公开分类读取、重试与登录入口切换 |
| `assets/css/base.css` | 公共样式、响应式、键盘焦点与减少动态效果支持 |
| `pages/*/module.json` | 六个模块各自维护的导航入口清单 |
| `pages/examples/shared-components.html` | 真实只读日志分页接入示例，不加入学生导航 |

这些路径均相对于 `src/main/webapp/`。公共登录弹窗提供会话入口；郭金滔仍负责完整登录注册页和用户角色管理。示例使用杨程已合入的只读日志接口，不代替他的正式审计页面。首页保留未交付模块的“待开放”，不把计划功能显示成已经可用。

## 首页设计定位

用户已确认：在校学生为主要用户，管理与审核人员为辅助用户；核心任务是帮助物品找回或归还。当前首页引导登录、说明安全交接流程并展示真实分类，内容要可信、友善。

使用 frontend-design 的设计流程，先确认定位，再选择“校园失物登记卡”方向。原方案中无业务含义的星点被移除，保留单一黄色归还挂签作为记忆点；没有引入虚构物品、人数、成功案例或统计。

| 设计项 | 选择及其与内容的关系 |
| --- | --- |
| 核心颜色 | 登记蓝 `#173e58`、纸白 `#ffffff`、底色 `#f2f5f7`、挂签黄 `#e7bf62`、正文灰蓝 `#526a79`、分隔线 `#ccd7dc` |
| 字体 | 标题采用宋体风格，呼应校园登记材料；正文采用等线等中文无衬线字体，优先保证长句阅读。使用系统字体回退，无外部字体请求 |
| 版式 | 保留左导航、顶部登录、双栏首屏、三步说明、分类区域的顺序和真实文案；文字左对齐。900px 及以下转为顶部可横向滚动导航和单栏内容 |
| 图形 | 保留原“失而复得”和英文文案，组合成带穿孔、剪角与挂绳的物品挂签 |
| 动效 | 挂签首次落定 0.5 秒，登录弹窗开启 0.14 秒；仅在系统允许动态效果时启用 |

逐字核对改版前后的首页 HTML，只有无语义星点装饰被删除，原有真实文字和功能节点保留。桌面双栏和三步区域保持原布局关系；在 800px 检查发现标题过度换行后，将单栏断点提前到 900px。

## 组员如何接入

各人在自己目录内创建页面，例如孙睿的 `pages/category/manage.html`。使用和示例相同的结构，页面内容放在唯一的 main 中：

```html
<link rel="stylesheet" href="../../assets/css/base.css">
<script defer src="../../assets/js/api.js"></script>
<script defer src="../../assets/js/pagination.js"></script>
<script defer src="../../assets/js/shell.js"></script>
<script defer src="../../assets/js/category/manage.js"></script>
<!-- body 内 -->
<div data-cms-shell></div>
<main><!-- 本模块内容 --></main>
```

在 body 设置 `data-page-title="分类管理"`。模块脚本在 shell.js 之后执行，不再手工调用 mount。等待 `Cms.shell.ready` 后再加载私有内容。

对应页面和服务端权限完成后，修改自己的 `pages/category/module.json`：

```json
{
  "module": "category",
  "entries": [
    { "label": "分类管理", "page": "pages/category/manage.html", "permission": "category:manage" }
  ]
}
```

入口只能指向本模块目录的 HTML 文件。字段 page 相对应用根目录，不带 `/cms`。当前六份清单的 entries 都为空；上例是未来接入格式，未表示分类管理页已经完成。其他模块依次使用 auth、items、review、claims、audit 目录。

常用接口：

```javascript
await Cms.shell.ready;
const categories = await Cms.api.request('/api/public/categories');
const csrf = await Cms.api.csrf(); // 写请求会自动获取并附加，通常无需手工调用
const pager = new Cms.Pagination(document.querySelector('#pager'), loadPage);
// loadPage({ page, size }) 调用本模块真实分页 API，完成后执行 pager.render(result)
// 如需中断读取，给 request 传入 { signal: controller.signal }
```

`request(path, { method, body, signal, timeout })` 接收 JSON body，默认超时 15 秒，成功直接返回 Result.data。URL 从脚本位置推导应用根路径，支持子目录页面，不硬编码部署上下文。上传文件需要后续明确扩展，不能把 FormData 当作 JSON 传入。

`Cms.shell.user` 为当前会话；`hasPermission(code)` 控制入口展示。`cms:authchange` 的 detail.user 在登录、退出和会话刷新时更新；模块应在退出或无权限时清空私有 DOM，并中断旧请求。参照 examples.js 的序列号和 AbortController，避免旧响应在退出后重新填回页面。

自定义登录页成功后，应调用 `Cms.api.invalidateSession()` 和 `await Cms.shell.refreshSession()`。令牌只在内存中保存，不放入 localStorage；登录和退出会清理旧令牌及旧令牌请求。401 更新会话状态，403 清理缓存令牌，写操作不会自动重试。服务端现有鉴权和 CSRF 覆盖范围保持原实现，菜单隐藏不构成接口授权。

400、401、403、409、503 等响应使用固定中文提示，不展示响应体中的 SQL、HTML 或堆栈。业务模块捕获异常后可调用 `Cms.shell.report(error)`；取消请求保持静默，避免把主动切页显示为故障。

## 验证与运行

```powershell
./scripts/test-shared-ui.ps1
./scripts/build.ps1
./scripts/test-foundation-checks.ps1
./scripts/run-dev.ps1 -Port 8088
```

前端检查使用 Node.js 内置 node:test，无新增 npm 依赖。正常运行读取各自本机的 MySQL 私密配置；首页为 `/cms/pages/index.html`，组员示例为 `/cms/pages/examples/shared-components.html`。

本轮最终验证：75 项 Java 测试、16 项 JavaScript 测试和 6 项 WAR 污染包反例均通过，无失败、错误或跳过。实际 WAR 保持 34 个运行时 JAR，无 Boot、H2、私密配置或测试工具。

浏览器和额外 HTTP 检查在隔离 H2 测试预览中进行：真实登录和退出、会话失效、带 CSRF 创建草稿、公开分类读取、日志分页前后页及切换每页条数、400 与 409 错误。13 条日志和测试账号仅为隔离夹具，不写入真实 MySQL，也不作为真实产品数据。浏览器复核 360、800、1024、1366px 宽度，主页面无横向溢出，控制台无警告或错误；表格按需在自己的容器内横向滚动。

最后构建首次在沙箱内因回环连接被拒绝，ContainerSmokeTest 出现环境错误；允许本机测试通信后重新完整构建通过。原始日志保留在本地，不将环境连接限制计为业务缺陷。

2026年10月26日提交前重新运行上述75项Java、16项JavaScript和6项打包检查，全部通过。浏览器和额外HTTP检查保留2026年10月23日的原始验证日期，不将历史结果冒充本次新执行。

机器结果见 [gmy-r2-verification.json](gmy-r2-verification.json)。工具辅助完成实现与复测，高鸣远仍需理解请求、Session、CSRF 和模块入口。提交使用 andong163 与本人 Google 邮箱；本次只交付个人功能分支，不合并 develop，不修改组员分支。
