package com.team.cms;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.team.cms.config.*;
import com.team.cms.support.*;
import java.util.stream.IntStream;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name = "root", classes = SpringConfig.class),
    @ContextConfiguration(name = "mvc", classes = SpringMvcConfig.class)})
@TestPropertySource(properties = {"jdbc.driver=org.h2.Driver", "jdbc.url=jdbc:h2:mem:foundation;MODE=MySQL;DB_CLOSE_DELAY=-1", "jdbc.username=sa", "jdbc.password="})
@Sql("/schema-test.sql")
public class FrameworkIntegrationTest {
    @Autowired private TestRecordMapper mapper;
    @Autowired private TransactionProbeService transaction;
    @Autowired private WebApplicationContext context;
    @Test public void rootAndMvcHaveSeparateResponsibilities() {
        assertFalse(context.getParent().containsLocalBean("systemHealthController"));
        assertTrue(context.containsLocalBean("systemHealthController"));
        assertTrue(context.getParent().containsLocalBean("systemHealthService"));
    }
    @Test public void mybatisPlusInjectsCrudAndFillsAuditFields() {
        TestRecord record = new TestRecord(); record.setName("基础验证");
        assertEquals(1, mapper.insert(record));
        assertNotNull(record.getId()); assertNotNull(record.getCreatedAt()); assertNotNull(record.getUpdatedAt());
        assertEquals(Integer.valueOf(0), record.getVersion());
        assertEquals("基础验证", mapper.selectById(record.getId()).getName());
    }
    @Test public void checkedFailureRollsBackButNormalOperationCommits() throws Exception {
        try { transaction.insertAndFail(); fail("Expected transaction probe to fail"); }
        catch (Exception expected) { assertEquals("expected checked exception", expected.getMessage()); }
        assertEquals(Long.valueOf(0), mapper.selectCount(null));
        transaction.insertNormally(); assertEquals(Long.valueOf(1), mapper.selectCount(null));
    }
    @Test public void paginationAndLogicalDeletionUseRealSql() {
        IntStream.range(0, 3).forEach(i -> { TestRecord row = new TestRecord(); row.setName("row-" + i); mapper.insert(row); });
        Page<TestRecord> page = mapper.selectPage(new Page<>(1, 2), null);
        assertEquals(3L, page.getTotal()); assertEquals(2, page.getRecords().size());
        Long id = page.getRecords().get(0).getId(); mapper.deleteById(id);
        assertNull(mapper.selectById(id)); assertEquals(Long.valueOf(2), mapper.selectCount(null));
    }
    @Test public void staleVersionCannotOverwriteUpdatedRecord() {
        TestRecord row = new TestRecord(); row.setName("original"); mapper.insert(row);
        TestRecord first = mapper.selectById(row.getId()), stale = mapper.selectById(row.getId());
        first.setName("first"); assertEquals(1, mapper.updateById(first));
        stale.setName("stale"); assertEquals(0, mapper.updateById(stale));
        assertEquals("first", mapper.selectById(row.getId()).getName());
    }
}
