# 分类服务注入提示修复（第一轮补丁）

日期：2026年10月21日，基于 SR R1 合并提交 `9077f8c`。

## 截图含义与排查边界

截图是 IDEA 的“无法自动装配，存在多个 CategoryService 类型的 Bean”提示，候选包含服务接口 `categoryService` 和实际实现 `categoryServiceImpl`。截图中的权限 TODO 也表明所展示文件尚未包含 SR R1 合并后的权限接入补丁。

远程当前源码中，`CategoryService`、`CategoryAccessService` 是普通 Java 接口，只有 `CategoryServiceImpl` 标注 `@Service`。MyBatis 的扫描同时要求 `annotationClass = Mapper.class`，服务接口没有 `@Mapper`，因此不应被注册为 Mapper。

本次通过真实 Spring 父子容器检查确认：两个服务接口都只有 `categoryServiceImpl` 一个实际 Bean，指向同一服务；接口名没有额外注册为 Bean，真正的 `categoryMapper` 则正常存在。因此仓库当前版本没有复现运行时多个服务 Bean 的问题。结合截图候选，IDE 把接口推断为另一个候选是可能原因；仅凭照片无法确认朋友电脑的插件、索引或本地配置是否存在其他差异。

## 修复内容

`CategoryController` 和 `PublicCategoryController` 的构造器参数增加 `@Qualifier("categoryServiceImpl")`，明确使用现有实现，不改变接口签名、HTTP 路径或实际服务实现。这是 Spring 支持的构造器参数限定方式，见 [Spring 官方说明](https://docs.spring.io/spring-framework/docs/5.2.x/spring-framework-reference/core.html#beans-autowired-annotation-qualifiers)。

没有将接口标记为 Service，没有把 Service 改为 Mapper，没有添加第二个实现，也没有用全局 `@Primary` 隐藏其他注入问题。MyBatis 扫描仍按 `@Mapper` 过滤，规则见 [MyBatis 官方说明](https://mybatis.org/spring/mappers.html#scan)。

## 朋友如何同步

先提交或备份本机尚未提交的改动，在自己的 feature 分支执行：

```powershell
git fetch origin
git merge origin/develop
```

在 IDEA 的 Maven 工具窗口重新加载项目，再执行 `./scripts/build.ps1`。修改只涉及第一轮分类注入和测试，不要求重置分支或重新克隆；第二至第四轮的开发继续保留在个人分支。

若本机确实启动失败，以运行控制台中的异常为准：检查是否还有其他 `CategoryService` 实现被标注 `@Service`、接口是否被误加 `@Mapper`，或本地的 `@MapperScan` 是否删掉了注解过滤。此类本机差异需要结合实际源码定位，不能用关闭 IDEA 检查代替修复。

## 验证记录

执行 `./scripts/build.ps1`，JDK 11 下共 45 项测试全部通过（44 项单元及集成测试，加 1 项独立 JVM 真实 WAR 部署测试），失败、错误、跳过均为 0。分类测试共 14 项，覆盖实际 Spring Bean 注册、分类真实登录权限和父子容器；WAR 私密配置和测试类排除检查通过。原有 SR R1 的 44 项快照保留为历史验收记录，不覆盖。
