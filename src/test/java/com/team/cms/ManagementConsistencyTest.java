package com.team.cms;

import com.team.cms.auth.dto.UpdateRoleRequest;
import com.team.cms.auth.mapper.*;
import com.team.cms.auth.service.*;
import com.team.cms.category.dto.CategoryCreateRequest;
import com.team.cms.category.mapper.CategoryMapper;
import com.team.cms.category.service.*;
import com.team.cms.common.exception.BusinessException;
import com.team.cms.config.SpringConfig;
import java.lang.reflect.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.Assert.*;

/** 回归真实检查发现的分类环、管理员清空和乐观锁假成功。 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes=SpringConfig.class)
@TestPropertySource(properties={"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:management-consistency;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "jdbc.username=sa","jdbc.password="})
@Sql({"/auth-schema-test.sql","/article-schema-test.sql","/schema-category.sql"})
public class ManagementConsistencyTest {
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource source;
    @Autowired CategoryMapper categories;
    @Autowired CategoryService categoryService;
    @Autowired AuthService auth;
    @Autowired UserAdminService userAdmin;
    @Autowired SysUserMapper users;
    @Autowired SysRoleMapper roles;
    @Autowired SysPermissionMapper permissions;
    @Autowired SysUserRoleMapper userRoles;
    @Autowired SysRolePermissionMapper rolePermissions;

    @Test public void reciprocalMovesCannotCreateCycle() throws Exception {
        long a=category("A"), b=category("B");
        CountDownLatch firstRead=new CountDownLatch(1),release=new CountDownLatch(1),
            secondStarted=new CountDownLatch(1),secondRead=new CountDownLatch(1),committed=new CountDownLatch(1);
        AtomicInteger reads=new AtomicInteger();
        CategoryMapper controlled=wrap(CategoryMapper.class,(method,args)->{
            Object result=call(categories,method,args);
            if(method.getName().equals("selectList")) {
                if(reads.incrementAndGet()==1) {firstRead.countDown();await(release);}
                else {secondRead.countDown();await(committed);}
            }
            return result;
        });
        CategoryServiceImpl service=new CategoryServiceImpl(controlled,transactions,
            context.getBeanProvider(CategoryReferenceGuard.class));
        ExecutorService pool=Executors.newFixedThreadPool(2);
        try {
            Future<?> first=pool.submit(()->{try{service.move(a,b);}finally{committed.countDown();}});
            await(firstRead);
            Future<?> second=pool.submit(()->{secondStarted.countDown();expectConflict(()->service.move(b,a));});
            await(secondStarted);
            // 给并发请求读取旧快照的机会；修复后它必须等第一笔结构修改提交。
            secondRead.await(200,TimeUnit.MILLISECONDS);
            release.countDown();
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertEquals(Long.valueOf(b),categories.selectById(a).getParentId());
            assertNull(categories.selectById(b).getParentId());
        } finally {release.countDown();pool.shutdownNow();}
    }

    @Test public void concurrentDisablesKeepOneEnabledAdmin() throws Exception {
        long[] ids=admins();
        concurrent(()->userAdmin.updateStatus(ids[0],false),()->userAdmin.updateStatus(ids[1],false));
        assertEquals(1,users.countEnabledAdmins());
    }

    @Test public void disablingAndRemovingRoleShareLastAdminProtection() throws Exception {
        long[] ids=admins();
        concurrent(()->userAdmin.updateStatus(ids[0],false),()->userAdmin.assignRoles(ids[1],List.of("EDITOR")));
        assertEquals(1,users.countEnabledAdmins());
    }

    @Test public void staleUserUpdateReturnsConflict() {
        jdbc().update("INSERT INTO sys_user(id,username,password_hash,display_name) VALUES(903,'version_user','unused','测试用户')");
        SysUserMapper controlled=wrap(SysUserMapper.class,(method,args)->{
            if(method.getName().equals("updateById")) committedChange("UPDATE sys_user SET version=version+1 WHERE id=903");
            return call(users,method,args);
        });
        UserAdminService service=new UserAdminService(controlled,roles,permissions,userRoles);
        expectConflict(()->new TransactionTemplate(transactions).execute(status->{service.updateStatus(903L,false);return null;}));
        assertEquals(Integer.valueOf(1),users.selectById(903L).getEnabled());
    }

    @Test public void staleRoleRenameReturnsConflict() {
        jdbc().update("INSERT INTO sys_role(id,code,name) VALUES(903,'VERSION_ROLE','原角色名称')");
        SysRoleMapper controlled=wrap(SysRoleMapper.class,(method,args)->{
            if(method.getName().equals("updateById")) committedChange("UPDATE sys_role SET version=version+1 WHERE id=903");
            return call(roles,method,args);
        });
        RoleAdminService service=new RoleAdminService(controlled,permissions,rolePermissions);
        UpdateRoleRequest request=new UpdateRoleRequest();request.setName("修改后的角色");
        expectConflict(()->new TransactionTemplate(transactions).execute(status->service.update(903L,request)));
        assertEquals("原角色名称",roles.selectById(903L).getName());
    }

    private long category(String name) {
        CategoryCreateRequest request=new CategoryCreateRequest();request.setName(name);
        return categoryService.create(request).getId();
    }
    private long[] admins() {
        auth.ensureAdmin("consistency_a","consistency-password");auth.ensureAdmin("consistency_b","consistency-password");
        return new long[]{jdbc().queryForObject("SELECT id FROM sys_user WHERE username='consistency_a'",Long.class),
            jdbc().queryForObject("SELECT id FROM sys_user WHERE username='consistency_b'",Long.class)};
    }
    private void concurrent(Runnable a,Runnable b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        AtomicInteger ok=new AtomicInteger(),conflicts=new AtomicInteger();
        java.util.function.Consumer<Runnable> run=action->{await(start);try{action.run();ok.incrementAndGet();}
            catch(BusinessException e){assertEquals(HttpStatus.CONFLICT,e.getStatus());conflicts.incrementAndGet();}};
        try {Future<?> first=pool.submit(()->run.accept(a)),second=pool.submit(()->run.accept(b));start.countDown();
            first.get(10,TimeUnit.SECONDS);second.get(10,TimeUnit.SECONDS);
            assertEquals(1,ok.get());assertEquals(1,conflicts.get());
        } finally {pool.shutdownNow();}
    }
    private void committedChange(String sql) {
        TransactionTemplate separate=new TransactionTemplate(transactions);
        separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        separate.execute(status->jdbc().update(sql));
    }
    private JdbcTemplate jdbc(){return new JdbcTemplate(source);}
    private static void expectConflict(Runnable action){try{action.run();fail("应返回409冲突");}
        catch(BusinessException error){assertEquals(HttpStatus.CONFLICT,error.getStatus());}}
    private static void await(CountDownLatch latch){try{assertTrue(latch.await(5,TimeUnit.SECONDS));}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    interface Hook {Object invoke(Method method,Object[] args) throws Throwable;}
    @SuppressWarnings("unchecked") private static <T>T wrap(Class<T> type,Hook hook){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(proxy,method,args)->hook.invoke(method,args));}
    private static Object call(Object target,Method method,Object[] args) throws Throwable {
        try{return method.invoke(target,args);}catch(InvocationTargetException e){throw e.getCause();}
    }
}
