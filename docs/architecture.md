# 架构与数据库初稿

## 请求与容器

```mermaid
flowchart TD
  Client[页面或 ApiFox] --> Filter[UTF8 编码与 requestId 日志]
  Filter --> MVC[SpringMVC 子容器 Controller 校验和异常处理]
  MVC --> Service[Spring 根容器 Service AOP和事务]
  Service --> Mapper[MyBatisPlus Mapper 分页和乐观锁]
  Mapper --> MySQL[(MySQL cms_content)]
  MVC --> Result[统一 code message data]
```

SpringConfig 只扫描业务、公共组件；SpringMvcConfig 只扫描 Controller 和 Advice。PersistenceConfig 使用 MP 专用 MybatisSqlSessionFactoryBean。WAR 中只有生产代码，本地工具和验证实体位于 src/test。

## 数据库关联

```mermaid
erDiagram
  SYS_USER ||--o{ SYS_USER_ROLE : has
  SYS_ROLE ||--o{ SYS_USER_ROLE : assigned
  SYS_ROLE ||--o{ SYS_ROLE_PERMISSION : grants
  SYS_PERMISSION ||--o{ SYS_ROLE_PERMISSION : defines
  SYS_USER ||--o{ CMS_ARTICLE : authors
  CMS_CATEGORY o|--o{ CMS_CATEGORY : parent
  CMS_CATEGORY ||--o{ CMS_ARTICLE : groups
  CMS_ARTICLE ||--o{ CMS_REVIEW_RECORD : reviews
  SYS_USER ||--o{ CMS_REVIEW_RECORD : decides
```

SYS_OPERATION_LOG 独立保留操作者ID与名称快照，避免账号变化影响历史。图中是关联概览，字段、索引和约束以 sql/init.sql 为准；后续由各模块作者完善字段设计图。

## 内容流程

```mermaid
stateDiagram-v2
  [*] --> DRAFT
  DRAFT --> PENDING_REVIEW: 提交
  PENDING_REVIEW --> APPROVED: 批准
  PENDING_REVIEW --> REJECTED: 驳回
  REJECTED --> PENDING_REVIEW: 修改后重提
  APPROVED --> PUBLISHED: 发布
  PUBLISHED --> OFFLINE: 下架
  PUBLISHED --> DRAFT: 撤回后修改
  OFFLINE --> DRAFT: 修改并重新审核
```

该图是模块共同设计，当前基础工程只定义状态枚举，具体状态服务尚待杨程与袁运轩实现。
