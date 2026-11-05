# SR R3 分类选择组件与查询次数优化

对应 `docs/team-iteration-plan.md` 中孙睿的第三轮任务：分类选择组件，以及分类查询相关的 SQL 次数与索引验证。本轮不改数据库结构，不加 HTTP 接口，只优化「怎么查分类」并把选择器抽成可复用组件。

## 一、本轮交付

| 交付物 | 位置 | 说明 |
| --- | --- | --- |
| 批量可绑定判定 | `CategoryAccessService.bindableIds(Collection<Long>)` | 一次分类查询，返回入参中可绑定（存在、未删除、自身与祖先均启用）的 id 子集 |
| 公开可见子树 | `CategoryAccessService.publicSubtreeIds(Long)` | 返回分类及其全部可见后代 id；停用节点连同整棵子树跳过 |
| SQL 计数器 | `src/test/java/com/team/cms/support/SqlCounter.java` | MyBatis 层计数，只在测试源集，不进 WAR |
| 分类选择组件 | `src/main/webapp/assets/js/category-select.js` | `Cms.CategorySelect.mount(...)`，数据源为公开分类树 |
| 集成测试 | `src/test/java/com/team/cms/CategoryR3IntegrationTest.java` | 6 个用例，含 SQL 次数断言 |

`CategoryAccessService` 仍是跨模块唯一入口，袁运轩写物品、杨程写审核时只需调用批量方法，不接触 `CategoryMapper`。

## 二、查询次数证据

`CategoryR3IntegrationTest` 在同一个 H2 库、同一份数据上对比两种写法，断言直接写在测试里，回归时自动守住：

| 场景 | 语句数 |
| --- | --- |
| 20 个分类逐个 `isBindable(id)` | 20 |
| 20 个分类一次 `bindableIds(ids)` | 1 |
| 深度 3 的子树 `publicSubtreeIds(root)` | 1 |

`bindableIds` 在内存里回溯父链，并用 `memo` 缓存中间结论：同一条祖先链只走一次，所以批量越大优势越明显。`publicSubtreeIds` 与既有的 `publicTree()` 共用「一次全表加载 + 内存遍历」的范式，不引入递归 CTE，MySQL 5.7/8.0 都适用。

## 三、索引说明

`cms_category` 现有索引：

- `idx_category_tree (parent_id, deleted, sort_order)` —— 服务按父级取子节点（管理树、公开树、子树检索）
- `fk_category_parent (parent_id)` —— 外键

需要说明的是：分类表规模只有几十到几百行，批量判定走的是「一次全表读取」，此时索引帮不上忙；本轮的优化来自**把 N 次读取合并成 1 次**，不是来自索引。索引真正生效的是按 `parent_id` 的子节点查询，以及 `deleted=0` 的过滤组合。这一点如实记录，避免把两种优化混为一谈。

## 四、分类选择组件用法

```html
<script src="/assets/js/base.js"></script>
<script src="/assets/js/category-select.js"></script>
<div id="category-host"></div>
<script>
    var picker = Cms.CategorySelect.mount(document.getElementById('category-host'), {
        label: '所属分类',
        name: 'categoryId',
        placeholder: '请选择分类',
        required: true,
        onChange: function (categoryId) {
            // categoryId 为 number 或 null
        }
    });

    picker.getValue();        // number|null
    picker.setValue(12);
    picker.reload();          // 重新拉取公开分类树
</script>
```

组件只从 `/api/public/categories` 取数：这棵树本身已剔除停用节点及其子树，所以选出来的值一定是可绑定的，页面不需要再判一次。

`docs/api.http` 与 `src/main/webapp/pages/items/edit.html` 里现有的手写递归下拉，可以在后续替换成这个组件，替换时保持提交范围独立，不与物品模块的改动混在一起。

## 五、验证方式

```powershell
.\scripts\build.ps1 -Test
```

或只跑本轮相关用例：

```powershell
mvn -B -ntp test -Dtest=CategoryR3IntegrationTest
```

本轮结束时全量 112 个测试通过（分类 R1 14 个、R2 6 个、R3 6 个）。

## 六、给其它模块的接入约定

- 列表页要判断一批分类能否绑定时，调用 `bindableIds`，不要循环 `isBindable`。
- 按分类筛选公开内容时，用 `publicSubtreeIds(rootId)` 拿到可见子树 id，再拼 `IN`；返回空列表表示该分类不可见，直接返回空结果，不要退化成「全量」。
- 组件的 `contextPath` 选项留给部署在非根路径的场景；当前项目按根路径部署，默认值即可。
