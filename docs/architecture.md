# 校园失物招领架构与数据设计

## 请求与容器

```mermaid
flowchart TD
  Client[公开站点 学生页面 后台或ApiFox] --> Filter[UTF8 requestId 日志]
  Filter --> MVC[SpringMVC子容器 Controller 校验与权限AOP]
  MVC --> Service[根容器 Service 事务与所有权]
  Service --> Mapper[MyBatisPlus及XML 分页 乐观锁]
  Mapper --> MySQL[(cms_content)]
  MVC --> Result[统一 code message data]
```

两个容器分别启用 AOP；Service 在根容器，Controller 与 Advice 在 MVC 子容器。实际业务登录权限由郭金滔接入。MP 专用工厂加载 mapper XML；测试与数据库工具不打入 WAR。

## E R 关联

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
  CMS_ARTICLE ||--o{ CMS_CLAIM_REQUEST : claims
  SYS_USER ||--o{ CMS_CLAIM_REQUEST : applies
```

十张业务及关联表。SYS_OPERATION_LOG 独立保存操作者快照，避免账号变化影响历史。表字段、约束与索引以 sql/init.sql 和增量脚本为准。

## 内容状态设计

```mermaid
stateDiagram-v2
  [*] --> DRAFT
  DRAFT --> PENDING_REVIEW: 提交
  PENDING_REVIEW --> DRAFT: 撤回
  PENDING_REVIEW --> APPROVED: 审核批准
  PENDING_REVIEW --> REJECTED: 驳回
  REJECTED --> PENDING_REVIEW: 修改重提
  APPROVED --> PUBLISHED: 授权发布
  APPROVED --> DRAFT: 修改
  PUBLISHED --> OFFLINE: 下架
  OFFLINE --> PUBLISHED: 内容未修改且可公开
  OFFLINE --> DRAFT: 修改重审
```

## 认领与结案设计

```mermaid
flowchart TD
  Found[他人公开且未结案的招领] --> Pending[学生提交PENDING]
  Pending --> Verify[审核员线下核实]
  Verify --> Rejected[REJECTED记录原因]
  Verify --> Approved[APPROVED 唯一有效通过]
  Approved --> Handover[发布者确认实际交接]
  Handover --> Complete[申请COMPLETED 物品RESOLVED]
  Complete --> Close[同事务关闭其他待核实申请]
```

上面的图定义业务需求，状态服务仍由杨程与袁运轩实现。基础工程已提供枚举、认领约束和基础回归测试，不声称已经实现整条业务流程。
