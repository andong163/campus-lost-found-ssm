package com.team.cms.category.service;

import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.dto.CategoryCreateResponse;
import com.team.cms.category.dto.CategoryOption;
import com.team.cms.category.dto.CategoryTreeNode;
import com.team.cms.category.entity.Category;
import com.team.cms.category.mapper.CategoryMapper;
import com.team.cms.common.exception.BusinessException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 分类服务实现（R1）。
 * <ul>
 *   <li>新增要求父级存在、未删除且父级及全部祖先启用，否则 404/409；</li>
 *   <li>同级有效重名拒绝。比较在 Java 层 trim + equalsIgnoreCase：
 *       H2 默认大小写敏感而 MySQL utf8mb4_0900_ai_ci 不敏感，交给数据库会导致测试与生产结论不一致；</li>
 *   <li>公开树只包含自身与全部祖先均启用的节点，父停用整棵子树隐藏；</li>
 *   <li>新增用"按父级分段的 ReentrantLock 包住整个事务"防并发同名同时通过，
 *       锁在事务提交后才释放（若在 @Transactional 方法内加锁会在提交前释放）。
 *       该方案只保证单实例；多实例需唯一索引或分布式锁，属 R4 复验项；</li>
 *   <li>祖先校验走单次全表查询 + 内存遍历，避免逐级查询；分类表规模小，
 *       R3 再按 SQL 次数证据决定是否引入递归 CTE/闭包表。</li>
 * </ul>
 */
@Service
public class CategoryServiceImpl implements CategoryService, CategoryAccessService {
    private static final Logger LOG = LoggerFactory.getLogger(CategoryServiceImpl.class);
    /** parentId 为 null（根级新增）时使用的锁键；ConcurrentHashMap 不接受 null 键。 */
    private static final long ROOT_LOCK_KEY = 0L;
    /** 树内稳定排序：sortOrder 升序，再按 id 升序兜底。 */
    private static final Comparator<Category> ORDER = Comparator
            .comparingInt((Category node) -> node.getSortOrder() == null ? 0 : node.getSortOrder())
            .thenComparingLong(node -> node.getId() == null ? 0L : node.getId());

    private final CategoryMapper mapper;
    private final TransactionTemplate transactionTemplate;
    /** 按父级分段的写锁；条目不清理（分类数量级小，R2/R4 收敛锁策略时再评估）。 */
    private final ConcurrentHashMap<Long, ReentrantLock> parentLocks = new ConcurrentHashMap<>();
    /** 内容模块登记的分类引用检查；尚无实现时为空集合，删除只校验子分类。 */
    private final ObjectProvider<CategoryReferenceGuard> referenceGuards;

    public CategoryServiceImpl(CategoryMapper mapper, PlatformTransactionManager transactionManager,
            ObjectProvider<CategoryReferenceGuard> referenceGuards) {
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.referenceGuards = referenceGuards;
    }

    @Override
    public CategoryCreateResponse create(CategoryCreateRequest request) {
        return inParentLock(request.getParentId(), () -> doCreate(request));
    }

    private CategoryCreateResponse doCreate(CategoryCreateRequest request) {
        String name = request.getName().trim();
        Long parentId = request.getParentId();
        int sortOrder = request.getSortOrder() == null ? 0 : request.getSortOrder();

        List<Category> all = mapper.selectList(null);
        Map<Long, Category> byId = indexById(all);
        if (parentId != null) {
            Category parent = byId.get(parentId);
            if (parent == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "父分类不存在");
            }
            if (!chainEnabled(parent, byId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "父分类已停用");
            }
        }
        if (hasSiblingName(all, parentId, name, null)) {
            throw new BusinessException(HttpStatus.CONFLICT, "同级已存在同名分类");
        }

        Category entity = new Category();
        entity.setName(name);
        entity.setParentId(parentId);
        entity.setSortOrder(sortOrder);
        entity.setEnabled(1);
        if (mapper.insert(entity) != 1) {
            throw new IllegalStateException("分类新增失败");
        }
        LOG.info("新增分类 id={} name={} parentId={}", entity.getId(), entity.getName(), entity.getParentId());
        return toResponse(entity);
    }

    @Override
    public CategoryCreateResponse rename(Long id, String name) {
        // 与"同父级下新增/改名"共用一把锁，同级重名判定才不会被并发穿透。
        return inParentLock(requireCategory(id).getParentId(), () -> doRename(id, name));
    }

    private CategoryCreateResponse doRename(Long id, String name) {
        String trimmed = name.trim();
        List<Category> all = mapper.selectList(null);
        Map<Long, Category> byId = indexById(all);
        Category target = byId.get(id);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        if (hasSiblingName(all, target.getParentId(), trimmed, id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "同级已存在同名分类");
        }
        target.setName(trimmed);
        updateOrConflict(target);
        LOG.info("分类改名 id={} name={}", id, trimmed);
        return toResponse(target);
    }

    @Override
    public CategoryCreateResponse move(Long id, Long parentId) {
        return inParentLock(parentId, () -> doMove(id, parentId));
    }

    private CategoryCreateResponse doMove(Long id, Long parentId) {
        List<Category> all = mapper.selectList(null);
        Map<Long, Category> byId = indexById(all);
        Category target = byId.get(id);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        if (parentId != null) {
            Category parent = byId.get(parentId);
            if (parent == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "父分类不存在");
            }
            if (isSelfOrDescendant(byId, parentId, id)) {
                throw new BusinessException(HttpStatus.CONFLICT, "不能移动到自身或其子分类下");
            }
            if (!chainEnabled(parent, byId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "父分类已停用");
            }
        }
        if (hasSiblingName(all, parentId, target.getName(), id)) {
            throw new BusinessException(HttpStatus.CONFLICT, "同级已存在同名分类");
        }
        target.setParentId(parentId);
        updateOrConflict(target);
        LOG.info("分类移动 id={} parentId={}", id, parentId);
        return toResponse(target);
    }

    @Override
    public CategoryCreateResponse sort(Long id, Integer sortOrder) {
        return inParentLock(requireCategory(id).getParentId(), () -> doSort(id, sortOrder));
    }

    private CategoryCreateResponse doSort(Long id, Integer sortOrder) {
        Category target = mapper.selectById(id);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        target.setSortOrder(sortOrder);
        updateOrConflict(target);
        LOG.info("分类排序 id={} sortOrder={}", id, sortOrder);
        return toResponse(target);
    }

    @Override
    public CategoryCreateResponse toggle(Long id, boolean enabled) {
        // 锁目标分类自身：与"往该分类下新增子分类"互斥，避免刚停用又挂上子节点。
        return inParentLock(id, () -> doToggle(id, enabled));
    }

    private CategoryCreateResponse doToggle(Long id, boolean enabled) {
        Category target = mapper.selectById(id);
        if (target == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        target.setEnabled(enabled ? 1 : 0);
        updateOrConflict(target);
        LOG.info("分类启停 id={} enabled={}", id, enabled);
        return toResponse(target);
    }

    @Override
    public void delete(Long id) {
        // 同样锁目标分类自身：与"新增子分类到该分类下"共用锁键，避免删出孤儿。
        inParentLock(id, () -> {
            doDelete(id);
            return null;
        });
    }

    private void doDelete(Long id) {
        List<Category> all = mapper.selectList(null);
        String name = null;
        for (Category node : all) {
            if (node.getId().equals(id)) {
                name = node.getName();
            } else if (id.equals(node.getParentId())) {
                throw new BusinessException(HttpStatus.CONFLICT, "该分类下还有子分类，不能删除");
            }
        }
        if (name == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        for (CategoryReferenceGuard guard : referenceGuards) {
            String reason = guard.blockingReason(id);
            if (reason != null) {
                throw new BusinessException(HttpStatus.CONFLICT, reason);
            }
        }
        if (mapper.deleteById(id) != 1) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        LOG.info("逻辑删除分类 id={} name={}", id, name);
    }

    @Override
    public List<CategoryTreeNode> tree() {
        List<Category> all = orderedAll();
        Map<Long, List<Category>> children = groupByParent(all);
        Map<Long, Category> byId = indexById(all);
        List<CategoryTreeNode> result = new ArrayList<>();
        for (Category node : all) {
            if (node.getParentId() == null) {
                result.add(buildAdminNode(node, children));
            } else if (!byId.containsKey(node.getParentId())) {
                // 父分类被逻辑删除等异常数据：暂挂根级便于后台发现；R2 删除接口会阻止产生孤儿。
                LOG.warn("分类 id={} 的父分类 id={} 不可见，管理树暂挂根级", node.getId(), node.getParentId());
                result.add(buildAdminNode(node, children));
            }
        }
        return result;
    }

    @Override
    public void requireBindable(Long categoryId) {
        if (categoryId == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "分类不能为空");
        }
        List<Category> all = mapper.selectList(null);
        Map<Long, Category> byId = indexById(all);
        Long currentId = categoryId;
        while (currentId != null) {
            Category current = byId.get(currentId);
            if (current == null) {
                // 自身不存在/已删除，或祖先链断裂（父被逻辑删除）都视为不存在。
                throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
            }
            if (!enabled(current)) {
                throw new BusinessException(HttpStatus.CONFLICT, "分类已停用");
            }
            currentId = current.getParentId();
        }
    }

    @Override
    public boolean isBindable(Long categoryId) {
        try {
            requireBindable(categoryId);
            return true;
        } catch (BusinessException error) {
            return false;
        }
    }

    @Override
    public List<CategoryOption> publicTree() {
        List<Category> all = orderedAll();
        Map<Long, List<Category>> children = groupByParent(all);
        List<CategoryOption> result = new ArrayList<>();
        for (Category node : all) {
            if (node.getParentId() == null && enabled(node)) {
                result.add(buildPublicOption(node, children));
            }
        }
        return result;
    }

    private List<Category> orderedAll() {
        List<Category> all = mapper.selectList(null);
        all.sort(ORDER);
        return all;
    }

    private Map<Long, Category> indexById(List<Category> all) {
        Map<Long, Category> map = new HashMap<>();
        for (Category node : all) {
            map.put(node.getId(), node);
        }
        return map;
    }

    /** 以 parentId 分组；all 已按 (sortOrder,id) 排序，分组列表天然有序。 */
    private Map<Long, List<Category>> groupByParent(List<Category> all) {
        Map<Long, List<Category>> map = new HashMap<>();
        for (Category node : all) {
            map.computeIfAbsent(node.getParentId(), key -> new ArrayList<>()).add(node);
        }
        return map;
    }

    /** 从 start 开始逐级向上，自身与全部祖先均启用才返回 true。 */
    private boolean chainEnabled(Category start, Map<Long, Category> byId) {
        Long currentId = start.getId();
        while (currentId != null) {
            Category current = byId.get(currentId);
            if (current == null || !enabled(current)) {
                return false;
            }
            currentId = current.getParentId();
        }
        return true;
    }

    /** 同一父级下是否存在同名分类（trim 后忽略大小写）；改名/移动时用 excludeId 跳过自身。 */
    private boolean hasSiblingName(List<Category> all, Long parentId, String name, Long excludeId) {
        for (Category node : all) {
            if (excludeId != null && excludeId.equals(node.getId())) {
                continue;
            }
            if (Objects.equals(node.getParentId(), parentId)
                    && node.getName() != null && node.getName().trim().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean enabled(Category node) {
        return node.getEnabled() != null && node.getEnabled() == 1;
    }

    private CategoryTreeNode buildAdminNode(Category node, Map<Long, List<Category>> children) {
        CategoryTreeNode treeNode = new CategoryTreeNode(node.getId(), node.getName(), node.getParentId(),
                node.getSortOrder(), enabled(node));
        List<Category> kids = children.get(node.getId());
        if (kids != null) {
            for (Category kid : kids) {
                treeNode.getChildren().add(buildAdminNode(kid, children));
            }
        }
        return treeNode;
    }

    private CategoryOption buildPublicOption(Category node, Map<Long, List<Category>> children) {
        CategoryOption option = new CategoryOption(node.getId(), node.getName());
        List<Category> kids = children.get(node.getId());
        if (kids != null) {
            for (Category kid : kids) {
                // 调用方已保证 node 启用；子节点停用则连同其整棵子树一并剔除。
                if (enabled(kid)) {
                    option.getChildren().add(buildPublicOption(kid, children));
                }
            }
        }
        return option;
    }

    /**
     * 在指定锁键的写锁内执行事务。不用 @Transactional 的原因：注解方式在方法返回时即释放锁，
     * 而事务在方法返回后才提交，锁会提前放掉。这里的顺序是"加锁 → 开启事务 → 提交 → 解锁"。
     */
    private <T> T inParentLock(Long lockKey, Supplier<T> action) {
        ReentrantLock lock = parentLocks.computeIfAbsent(lockKey == null ? ROOT_LOCK_KEY : lockKey,
                key -> new ReentrantLock());
        lock.lock();
        try {
            return transactionTemplate.execute(status -> action.get());
        } finally {
            lock.unlock();
        }
    }

    /** 读取未删除的分类；不存在抛 404。只用于取锁键或修改单行字段的场景。 */
    private Category requireCategory(Long id) {
        Category found = id == null ? null : mapper.selectById(id);
        if (found == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        return found;
    }

    /** 乐观锁写回：BaseEntity.version 由 MyBatis-Plus 自动递增，影响 0 行说明记录已被并发修改。 */
    private void updateOrConflict(Category entity) {
        if (mapper.updateById(entity) != 1) {
            throw new BusinessException(HttpStatus.CONFLICT, "分类已被其他操作修改，请刷新后重试");
        }
    }

    private CategoryCreateResponse toResponse(Category node) {
        return new CategoryCreateResponse(node.getId(), node.getName(), node.getParentId(),
                node.getSortOrder(), enabled(node));
    }

    /**
     * 判断 candidateParentId 是否为 targetId 自身或其后代：沿父链向上回溯，命中 targetId 即成环。
     * 用 byId.size() 兜底，防止脏数据里的环路导致死循环。
     */
    private boolean isSelfOrDescendant(Map<Long, Category> byId, Long candidateParentId, Long targetId) {
        Long cursor = candidateParentId;
        int steps = 0;
        while (cursor != null) {
            if (cursor.equals(targetId)) {
                return true;
            }
            if (++steps > byId.size()) {
                return true;
            }
            Category node = byId.get(cursor);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
    }
}
