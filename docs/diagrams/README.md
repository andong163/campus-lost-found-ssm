# 第四轮设计图

架构图、十表 E R 图和业务流程图根据当前配置类、SQL 外键及状态服务整理。SVG 为可缩放图稿，PNG 便于插入 Word/PDF，DOT 和 Mermaid 为可编辑源稿。E R 图建议在横向页面使用或直接查看 SVG。

| 图 | 查看 | 源稿 |
| --- | --- | --- |
| 传统 SSM 架构 | [SVG](architecture.svg) / [PNG](architecture.png) | [DOT](architecture.dot) / [Mermaid](architecture.mmd) |
| 十表 E R | [SVG](database-er.svg) / [PNG](database-er.png) | [DOT](database-er.dot) / [Mermaid](database-er.mmd) |
| 发布认领与交接 | [SVG](business-flow.svg) / [PNG](business-flow.png) | [DOT](business-flow.dot) / [Mermaid](business-flow.mmd) |

E R 箭头为父表到外键表的一对多关系，标注对应外键字段；NULL 关系在表中标明。sys_operation_log 保存操作者快照，不将其误画为具有操作者外键。流程图描述接口级状态规则，不表示其他组员第四轮页面已交付。
