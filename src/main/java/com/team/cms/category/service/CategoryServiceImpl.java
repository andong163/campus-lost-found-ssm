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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public CategoryServiceImpl(CategoryMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public CategoryCreateResponse create(CategoryCreateRequest request) {
        long lockKey = request.getParentId() == null ? ROOT_LOCK_KEY : request.getParentId();
        ReentrantLock lock = parentLocks.computeIfAbsent(lockKey, key -> new ReentrantLock());
        lock.lock();
        try {
            // 锁必须覆盖事务提交：事务提交在 execute 返回前完成，之后才 unlock。
            return transactionTemplate.execute(status -> doCreate(request));
        } finally {
            lock.unlock();
        }
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
        if (hasSiblingName(all, parentId, name)) {
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
        return new CategoryCreateResponse(entity.getId(), entity.getName(), entity.getParentId(),
                entity.getSortOrder(), enabled(entity));
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

    private boolean hasSiblingName(List<Category> all, Long parentId, String name) {
        for (Category node : all) {
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
}
