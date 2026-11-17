package com.team.cms.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** 在受归属保护的专用库还原九表旧结构，调用真实升级逻辑，绝不改动业务库。 */
public final class GmyR4UpgradeProbe {
    private static long count(Connection db, String sql) throws SQLException { return LocalDatabaseSupport.scalar(db, sql); }
    private static void expect(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void refused(Connection db, String message) throws Exception {
        try { MySqlCampusUpgrade.upgrade(db); throw new AssertionError("升级应拒绝不安全的旧库"); }
        catch (IllegalStateException expected) { expect(expected.getMessage().contains(message), "升级拒绝原因不符"); }
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: isolatedRunDirectory");
        Path run = GmyR3Fixture.validateRunDirectory(Path.of(""), Path.of(args[0]));
        Map<String,Object> report = new LinkedHashMap<>(); report.put("passed", false);
        try (Connection db = GmyR4Database.connect(run); Statement sql = db.createStatement()) {
            expect(count(db,"SELECT COUNT(*) FROM cms_article") == 0, "旧库结构探针必须从空物品表开始");
            long users = count(db,"SELECT COUNT(*) FROM sys_user");
            sql.execute("DROP TABLE cms_claim_request");
            sql.execute("ALTER TABLE cms_article DROP CHECK ck_item_type, DROP CHECK ck_item_resolution, DROP CHECK ck_item_resolved_at, DROP INDEX idx_item_filter, DROP COLUMN item_type, DROP COLUMN event_time, DROP COLUMN location, DROP COLUMN contact_info, DROP COLUMN resolution_status, DROP COLUMN resolved_at");
            sql.executeUpdate("INSERT INTO sys_role(code,name) VALUES('GMY_R4_LEGACY','验收保留角色')");
            sql.executeUpdate("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code='audit:view' WHERE r.code='GMY_R4_LEGACY'");
            sql.executeUpdate("INSERT INTO cms_category(name) VALUES('旧库保留分类')");
            long category = count(db,"SELECT MAX(id) FROM cms_category");
            sql.executeUpdate("INSERT INTO cms_article(title,content,author_id,category_id,status) VALUES('旧内容不能推断物品资料','保留原始内容进行拒绝自动回填测试',1,"+category+",'DRAFT')");
            refused(db,"reviewed data conversion");
            expect(!LocalDatabaseSupport.columns(db,"cms_article").contains("item_type") && count(db,"SELECT COUNT(*) FROM cms_article") == 1, "非空旧内容被错误修改");
            report.put("nonemptyLegacyRefusedWithoutDataLoss", true);
            sql.executeUpdate("DELETE FROM cms_article"); // 仅此专用库中刚创建的一条旧内容探针。
            sql.execute("ALTER TABLE cms_article ADD COLUMN item_type VARCHAR(10) NOT NULL");
            refused(db,"Partial item schema");
            sql.execute("ALTER TABLE cms_article DROP COLUMN item_type");
            report.put("partialSchemaRefused", true);
            MySqlCampusUpgrade.upgrade(db);
            MySqlCampusUpgrade.upgrade(db);
            LocalDatabaseSupport.verifyCampusSchema(db);
            expect(count(db,"SELECT COUNT(*) FROM sys_user") == users, "事务探针遗留测试用户");
            expect(count(db,"SELECT COUNT(*) FROM cms_article") == 0, "事务探针遗留内容");
            expect(count(db,"SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.code='GMY_R4_LEGACY' AND p.code='audit:view'") == 1, "旧角色授权丢失");
            expect(count(db,"SELECT COUNT(*) FROM cms_category WHERE name='旧库保留分类'") == 1, "旧分类丢失");
            report.put("legacyNineTableUpgrade",true); report.put("repeatUpgrade",true);
            report.put("existingUsersCategoryAndCustomGrantPreserved",true);
            report.put("uniquenessAndProbeRollback",true); report.put("passed",true);
        } finally {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(run.resolve("r4-upgrade-results.json").toFile(),report);
        }
        System.out.println("GMY R4 legacy upgrade checks passed.");
    }
}
