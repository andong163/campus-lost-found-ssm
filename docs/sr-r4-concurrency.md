# SR R4 并发边界复验与性能证据

对应 `docs/team-iteration-plan.md:154-158`：复验分类停用、删除、内容绑定的并发边界；补充分类切换、分页、删除的页面反馈；核对关联查询索引与 `EXPLAIN`；问题记录进入阶段文档；涉及接口进入接口说明。

本轮没有新增业务接口，交付物是**证据**：并发用例、执行计划、页面状态反馈与问题记录。

## 一、并发复验结果

用例在 `src/test/java/com/team/cms/CategoryR4IntegrationTest.java`，H2 内存库 + 运行期注册的 `CategoryReferenceGuard` 模拟内容引用，不依赖物品模块的真实表。

| 场景 | 用例 | 结论 |
|---|---|---|
| 删除分类 vs 内容绑定 | `concurrentDepositAndDeleteHaveExactlyOneWinner` | 两方**恰好一方**成功。同时成功即意味着出现指向已删除分类的悬空引用 |
| 删除父分类 vs 新增子分类 | `concurrentDeleteParentAndCreateChildNeverLeavesOrphan` | 不会出现「父已被逻辑删除、子分类仍存在」的孤儿节点 |
| 停用分类 vs 内容绑定 | `concurrentDisableAndDepositFinishWithoutDeadlockAndStayConsistent` | 15 秒内两方都返回（无死锁），停用后 `isBindable` 必为 false |

为什么会互斥，靠的是两层机制叠加：

1. **`inWriteTransaction(...)` 分类写锁**（`CategoryServiceImpl`）：分类新增、改名、移动、排序、启停和删除共用同一把锁，事务提交后才解锁。修复后不再按目标父级分锁，避免两个分类同时互相移动时各自通过旧结构校验。
2. **`selectForUpdate` 行锁**：绑定时在事务内 `SELECT ... FOR UPDATE` 锁分类行，删除时同样先锁行再检查。

分类写操作先取分类写锁，再执行数据库事务；内容绑定与分类删除通过相同分类行的数据库锁协调。`ManagementConsistencyTest` 补充验证两个分类并发互移时会拒绝成环操作。已验证场景的结果见测试，不将它扩大为所有业务路径均无死锁的保证。

> 分类树写锁适用于本项目的**单个 Tomcat 实例**。数据库行锁仍保护内容绑定与分类删除；仅靠这些行锁不能推导出多实例移动整棵分类树也安全。本项目未部署多实例。

## 二、执行计划与索引证据

`WHERE deleted = 0 AND parent_id = ?`（20041 行数据，其中该父下 41 行）：

```
SELECT "ID", "NAME"
FROM "PUBLIC"."CMS_CATEGORY"
    /* PUBLIC.FK_CATEGORY_PARENT_INDEX_3: PARENT_ID = CAST(1 AS BIGINT) */
    /* scanCount: 41 */
WHERE ("DELETED" = 0) AND ("PARENT_ID" = CAST(1 AS BIGINT))
ORDER BY "SORT_ORDER"
```

`scanCount: 41` 远小于全表 20041 行，说明走的是 `parent_id` 索引而不是全表扫描。H2 选中的是外键自动索引（`fk_category_parent`），MySQL 侧同名外键的最左列同样是 `parent_id`，会复用 `idx_category_tree(parent_id, deleted, sort_order)` 的最左前缀——两者结论一致，但**索引名不同，这点不能当成 MySQL 实测**，正式环境需要用 MySQL 的 `EXPLAIN` 复核一次。

另一类读取要诚实记录：`bindableIds` / `publicSubtreeIds` 的批量版本是「一次读全表 + 内存遍历」，它本身就是全表扫描。它是 R3 的查询次数优化（N 次 → 1 次），**不是索引优化**，`docs/sr-r3-optimization.md` 已按这个口径写。

## 三、页面反馈

分类管理页补了三处状态反馈：

- 加载树时禁用「刷新 / 新增根分类」，首次加载在树区域显示「加载中…」
- 保存、创建、删除进行中，对应按钮禁用并改成「保存中… / 创建中… / 删除中…」，完成后恢复原文案
- 所有失败路径统一走 `toast(error.message, true)` 红色提示；401 时自动退回登录框

## 四、问题记录

| 编号 | 问题 | 状态 |
|---|---|---|
| P1 | 并发下的悬空引用与孤儿节点 | 已修复并复验（本节第一部分） |
| P2 | 按父级查询的索引与扫描量 | 已用 EXPLAIN 证明走索引；批量全表读取属设计选择，分类总量小、可接受 |
| P3 | 锁粒度 | 分类管理写操作低频，使用统一写锁保护整棵树；公开查询不持有该锁 |

分类树结构检查涉及祖先和后代，不能仅按新父级拆锁。当前优先保留完整的结构约束，未根据小规模课设数据引入分布式锁或新的层级存储方案。

## 五、本轮涉及的接口（无新增，仅汇总）

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/api/categories` | `category:add` |
| GET | `/api/categories/tree` | `category:view` |
| PUT | `/api/categories/{id}` | `category:edit` |
| PUT | `/api/categories/{id}/parent` | `category:move` |
| PUT | `/api/categories/{id}/sort` | `category:sort` |
| PUT | `/api/categories/{id}/enabled` | `category:enable` |
| DELETE | `/api/categories/{id}` | `category:delete` |
| GET | `/api/public/categories` | 匿名 |
| GET | `/api/public/categories/subtree` | 匿名 |

RAND 之外的错误语义：404 表示分类不存在或已删除；409 表示结构性冲突（重名、自身/后代、有子分类或内容引用）；400 表示参数校验失败。

## 六、测试结果

```
CategoryR4IntegrationTest   5 个用例全通过
全量回归                    148 个测试，0 failure，0 error
```
