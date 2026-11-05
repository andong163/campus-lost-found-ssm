package com.team.cms.support;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.Configuration;

/**
 * 统计 MyBatis 实际下发的语句条数，用于给出「分类查询优化」的量化证据（R3）。
 * <p>
 * 只放在测试源集：主代码与打出的 WAR 都不包含它，也不会影响生产行为。
 * 计数点在 {@code StatementHandler#prepare}，每真正执行一条语句计一次，
 * 因此连接池初始化、Spring 启动等不计入。
 */
@Intercepts(@Signature(type = StatementHandler.class, method = "prepare",
        args = {Connection.class, Integer.class}))
public final class SqlCounter implements Interceptor {

    private final AtomicInteger count = new AtomicInteger();

    /** 先 reset 再操作；计数器与 Spring 上下文里的 SqlSessionFactory 共享。 */
    public static SqlCounter attach(Configuration configuration) {
        SqlCounter counter = new SqlCounter();
        configuration.addInterceptor(counter);
        return counter;
    }

    public int count() {
        return count.get();
    }

    public void reset() {
        count.set(0);
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        count.incrementAndGet();
        return invocation.proceed();
    }
}
