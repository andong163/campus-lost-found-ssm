package com.team.cms.tools;

import java.sql.*;
import java.util.*;

/** Additive local upgrade. MySQL DDL commits each stage independently. */
public final class MySqlCampusUpgrade {
    public static void main(String[] args) {
        try (Connection connection = LocalDatabaseSupport.load().connect(false)) {
            if (LocalDatabaseSupport.scalar(connection, "SELECT GET_LOCK('cms_content:campus-upgrade',10)") != 1) {
                throw new IllegalStateException("Another schema upgrade is running");
            }
            try {
                LocalDatabaseSupport.verifyBaseline(connection);
                Set<String> expected = Set.of("item_type", "event_time", "location", "contact_info", "resolution_status", "resolved_at");
                Set<String> existing = LocalDatabaseSupport.columns(connection, "cms_article");
                if (!existing.containsAll(Set.of("id", "title", "content", "author_id", "category_id", "status"))) {
                    throw new IllegalStateException("The original CMS baseline must be installed first");
                }
                long present = expected.stream().filter(existing::contains).count();
                if (present != 0 && present != expected.size()) {
                    throw new IllegalStateException("Partial item schema detected; review it manually before migration");
                }
                if (present == 0) {
                    if (LocalDatabaseSupport.scalar(connection, "SELECT COUNT(*) FROM cms_article") != 0) {
                        throw new IllegalStateException("Existing articles need a reviewed data conversion; no automatic backfill is performed");
                    }
                    LocalDatabaseSupport.script(connection, "sql/migrations/001-campus-item-columns.sql");
                    System.out.println("Item columns and constraints added to empty CMS table.");
                } else { System.out.println("Item columns already present; ALTER skipped."); }
                LocalDatabaseSupport.script(connection, "sql/migrations/002-claim-request.sql");
                try (Statement update = connection.createStatement()) {
                    update.executeUpdate("UPDATE sys_role SET name='学生',updated_at=CURRENT_TIMESTAMP,version=version+1 " +
                        "WHERE code='EDITOR' AND name='编辑人员'");
                    update.executeUpdate("UPDATE sys_role SET name='只读用户',updated_at=CURRENT_TIMESTAMP,version=version+1 " +
                        "WHERE code='VIEWER' AND name='普通用户'");
                }
                LocalDatabaseSupport.verifyCampusSchema(connection);
                verifyClaimConstraints(connection);
                System.out.println("Campus upgrade verified: schema, duplicate claim rejection, exclusive approval and rollback.");
            } finally { LocalDatabaseSupport.scalar(connection, "SELECT RELEASE_LOCK('cms_content:campus-upgrade')"); }
        } catch (Exception error) {
            LocalDatabaseSupport.safeFailure(error); System.exit(1);
        }
    }
    public static void verifyClaimConstraints(Connection connection) throws SQLException {
        String marker = "probe_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            long author = user(connection, marker + "_author"), reviewer = user(connection, marker + "_reviewer");
            long firstUser = user(connection, marker + "_a"), secondUser = user(connection, marker + "_b");
            long category = insert(connection, "INSERT INTO cms_category(name) VALUES (?)", marker);
            long item = insert(connection, "INSERT INTO cms_article(title,content,author_id,category_id,status," +
                "item_type,event_time,location,contact_info) VALUES (?, ?, ?, ?, 'PUBLISHED', 'FOUND', CURRENT_TIMESTAMP, ?, ?)",
                marker, "Temporary schema constraint probe", author, category, "test location", "test contact");
            long first = claim(connection, item, firstUser);
            expectDuplicate(connection, "INSERT INTO cms_claim_request(article_id,applicant_id,claim_description,contact_info) VALUES (?,?,?,?)",
                item, firstUser, "Duplicate application probe", "test contact");
            long second = claim(connection, item, secondUser);
            update(connection, "UPDATE cms_claim_request SET status='APPROVED',reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?", reviewer, first);
            expectDuplicate(connection, "UPDATE cms_claim_request SET status='APPROVED',reviewer_id=?,reviewed_at=CURRENT_TIMESTAMP WHERE id=?", reviewer, second);
            update(connection, "UPDATE cms_claim_request SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP WHERE id=?", first);
            update(connection, "UPDATE cms_article SET resolution_status='RESOLVED',resolved_at=CURRENT_TIMESTAMP WHERE id=?", item);
        } finally {
            connection.rollback(); connection.setAutoCommit(previousAutoCommit);
        }
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM sys_user WHERE username IN (?,?,?,?)")) {
            query.setString(1, marker + "_author"); query.setString(2, marker + "_reviewer");
            query.setString(3, marker + "_a"); query.setString(4, marker + "_b");
            try (ResultSet rows = query.executeQuery()) {
                rows.next(); if (rows.getLong(1) != 0) throw new IllegalStateException("Constraint probe rollback failed");
            }
        }
    }
    private static long user(Connection c, String marker) throws SQLException {
        return insert(c, "INSERT INTO sys_user(username,password_hash,display_name) VALUES (?, ?, ?)",
            marker, "temporary-probe-hash", "schema verification");
    }
    private static long claim(Connection c, long item, long user) throws SQLException {
        return insert(c, "INSERT INTO cms_claim_request(article_id,applicant_id,claim_description,contact_info) VALUES (?,?,?,?)",
            item, user, "Temporary claim verification", "test contact");
    }
    private static long insert(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(statement, values); statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Insert returned no generated key");
                return keys.getLong(1);
            }
        }
    }
    private static void update(Connection c, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) { bind(statement, values); statement.executeUpdate(); }
    }
    private static void expectDuplicate(Connection c, String sql, Object... values) throws SQLException {
        try { update(c, sql, values); }
        catch (SQLException error) {
            if (error.getSQLState() != null && error.getSQLState().startsWith("23")) return;
            throw error;
        }
        throw new IllegalStateException("Claim uniqueness constraint did not reject the invalid operation");
    }
    private static void bind(PreparedStatement statement, Object[] values) throws SQLException {
        for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
    }
}
