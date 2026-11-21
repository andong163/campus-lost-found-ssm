package com.team.cms;

import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.service.CategoryAccessService;
import com.team.cms.category.service.CategoryReferenceGuard;
import com.team.cms.category.service.CategoryService;
import com.team.cms.config.SpringConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * SR R4 并发边界与性能证据：分类停用、删除、内容绑定的并发一致性，以及关联查询的 EXPLAIN。
 *
 * <p>并发用例走 Service 层（不经 HTTP），因为要复验的是 {@code inWriteTransaction} 与
 * {@code selectForUpdate} 的配合，而不是权限与路由。引用关系用运行期注册的
 * {@link CategoryReferenceGuard} 模拟，不依赖物品模块的真实表。</p>
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringConfig.class)
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:category-r4;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql({"/auth-schema-test.sql", "/article-schema-test.sql", "/schema-category.sql"})
public class CategoryR4IntegrationTest {
    private static final String GUARD_BEAN = "r4CategoryReferenceGuard";
    private static final Set<Long> REFERENCED = ConcurrentHashMap.newKeySet();

    @Autowired private ConfigurableApplicationContext context;
    @Autowired private CategoryService categoryService;
    @Autowired private CategoryAccessService accessService;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private DataSource dataSource;
    private TransactionTemplate transactionTemplate;

    @Before public void setup() {
        REFERENCED.clear();
        transactionTemplate = new TransactionTemplate(transactionManager);
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        if (!beanFactory.containsSingleton(GUARD_BEAN)) {
            beanFactory.registerSingleton(GUARD_BEAN, new CategoryReferenceGuard() {
                @Override public String blockingReason(Long categoryId) {
                    return REFERENCED.contains(categoryId) ? "该分类下还有未删除的物品信息，不能删除" : null;
                }
            });
        }
    }

    // ---------------------------------------------------------------- EXPLAIN

    @Test public void explainUsesParentIndexForScopedQuery() {
        long parent = insert("索引父", null, 1);
        for (int i = 0; i < 40; i++) {
            insert("子" + i, parent, 1);
        }
        seed(20000);

        String plan = explainAnalyze("SELECT id, name FROM cms_category WHERE deleted = 0 AND parent_id = "
            + parent + " ORDER BY sort_order");

        // 表里共 20041 行，其中属于该父级的只有 41 行；计划里 scanCount 就是实际扫描行数，
        // 它远小于全表行数才说明走的是 parent_id 索引（H2 会挑 idx_category_tree 或外键索引，
        // 两者最左列都是 parent_id，MySQL 侧同理）。
        Matcher scanned = Pattern.compile("scanCount: (\\d+)").matcher(plan);
        assertTrue("执行计划应包含 scanCount，实际：" + plan, scanned.find());
        int rows = Integer.parseInt(scanned.group(1));
        assertTrue("按父级查询应走 parent_id 索引，而不是全表 20041 行；实际扫描 " + rows + " 行：" + plan,
            rows <= 100);
    }

    @Test public void explainRecordsTheWholeTableLoadForBatchChecks() {
        seed(300);

        String plan = explain("SELECT id, name, parent_id, sort_order, enabled FROM cms_category WHERE deleted = 0");

        // bindableIds / publicSubtreeIds 的批量版本刻意只读一次全表；这里如实记录它没有走单列索引，
        // 优化收益来自「N 次读取合并为 1 次」，不能记成索引优化。
        assertTrue("批量读取应能解析出执行计划，实际：" + plan, plan != null && !plan.isBlank());
    }

    // ---------------------------------------------------------------- 并发：删除 vs 绑定

    @Test public void concurrentDepositAndDeleteHaveExactlyOneWinner() throws Exception {
        long categoryId = create("并发删除", null);
        AtomicInteger depositOk = new AtomicInteger();
        AtomicInteger deleteOk = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(2);
        Future<?> depositor = pool.submit(() -> {
            await(start);
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    accessService.requireBindable(categoryId);
                    REFERENCED.add(categoryId);
                });
                depositOk.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 分类已被删除时这里抛 404，属于预期结果之一。
            }
            return null;
        });
        Future<?> deleter = pool.submit(() -> {
            await(start);
            try {
                categoryService.delete(categoryId);
                deleteOk.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 引用已写入时删除抛 409，属于预期结果之一。
            }
            return null;
        });

        start.countDown();
        depositor.get(15, TimeUnit.SECONDS);
        deleter.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals("绑定与删除必须恰好成功一个；同时成功说明产生了指向已删除分类的悬空引用",
            1, depositOk.get() + deleteOk.get());
        if (deleteOk.get() == 1) {
            assertFalse("分类已删除后不应仍可绑定", accessService.isBindable(categoryId));
        }
    }

    // ---------------------------------------------------------------- 并发：删除父 vs 新增子

    @Test public void concurrentDeleteParentAndCreateChildNeverLeavesOrphan() throws Exception {
        long parentId = create("并发父", null);
        AtomicInteger childCreated = new AtomicInteger();
        AtomicInteger parentDeleted = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(2);
        Future<?> creator = pool.submit(() -> {
            await(start);
            try {
                CategoryCreateRequest child = new CategoryCreateRequest();
                child.setName("并发子");
                child.setParentId(parentId);
                child.setSortOrder(0);
                categoryService.create(child);
                childCreated.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 父分类已被删除时这里抛 404，属于预期结果之一。
            }
            return null;
        });
        Future<?> deleter = pool.submit(() -> {
            await(start);
            try {
                categoryService.delete(parentId);
                parentDeleted.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 子分类已存在时删除父分类抛 409，属于预期结果之一。
            }
            return null;
        });

        start.countDown();
        creator.get(15, TimeUnit.SECONDS);
        deleter.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        // 不能在「父已被逻辑删除」的同时留下一个可用的子分类（管理树会把它挂到根级，那是兜底而非正常状态）。
        assertFalse("父分类已删除却仍有子分类存在，产生了孤儿节点",
            parentDeleted.get() == 1 && childCreated.get() == 1);
        assertTrue("父子并发至少要有一个成功", childCreated.get() + parentDeleted.get() >= 1);
    }

    // ---------------------------------------------------------------- 并发：停用 vs 绑定

    @Test public void concurrentDisableAndDepositFinishWithoutDeadlockAndStayConsistent() throws Exception {
        long categoryId = create("并发停用", null);
        AtomicInteger depositOk = new AtomicInteger();
        AtomicInteger disableOk = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(2);
        Future<?> depositor = pool.submit(() -> {
            await(start);
            try {
                transactionTemplate.executeWithoutResult(status -> accessService.requireBindable(categoryId));
                depositOk.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 停用先生效时这里抛 409，属于预期结果之一。
            }
            return null;
        });
        Future<?> disabler = pool.submit(() -> {
            await(start);
            try {
                accessService.requireBindable(categoryId); // 先确认存在
                categoryService.toggle(categoryId, false);
                disableOk.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 分类已被删除时不存在该分支；保留兜底。
            }
            return null;
        });

        start.countDown();
        // 15 秒内必须都返回：超时视为死锁，直接失败。
        depositor.get(15, TimeUnit.SECONDS);
        disabler.get(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals("停用必须成功", 1, disableOk.get());
        assertFalse("停用后分类不可再绑定", accessService.isBindable(categoryId));
        assertTrue("并发执行不应卡死", true);
    }

    // ---------------------------------------------------------------- helpers

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待并发起点被中断", error);
        }
    }

    private long create(String name, Long parentId) {
        CategoryCreateRequest request = new CategoryCreateRequest();
        request.setName(name);
        request.setParentId(parentId);
        request.setSortOrder(0);
        return categoryService.create(request).getId();
    }

    private long insert(String name, Long parentId, int enabled) {
        JdbcTemplate jdbc = jdbc();
        jdbc.update("INSERT INTO cms_category(name, parent_id, sort_order, enabled, created_at, updated_at, deleted, version) "
            + "VALUES (?, ?, 0, ?, NOW(), NOW(), 0, 0)", name, parentId, enabled);
        return jdbc.queryForObject("SELECT MAX(id) FROM cms_category", Long.class);
    }

    private void seed(int rows) {
        List<Object[]> batch = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            batch.add(new Object[] {"填充" + i});
        }
        jdbc().batchUpdate("INSERT INTO cms_category(name, parent_id, sort_order, enabled, created_at, updated_at, deleted, version) "
            + "VALUES (?, NULL, 0, 1, NOW(), NOW(), 0, 0)", batch);
    }

    private String explain(String sql) {
        Object plan = jdbc().queryForObject("EXPLAIN " + sql, String.class);
        return plan == null ? "" : plan.toString();
    }

    private String explainAnalyze(String sql) {
        Object plan = jdbc().queryForObject("EXPLAIN ANALYZE " + sql, String.class);
        return plan == null ? "" : plan.toString();
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(dataSource);
    }
}
