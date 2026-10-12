package com.team.cms.tools;

import java.net.URI;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Developer tools only. Credentials stay in memory; remote URLs are never accepted. */
public final class LocalDatabaseSupport {
    private static final String DEFAULT_URL = "jdbc:mysql://127.0.0.1:3306/cms_content?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
    private final String url, username, password;
    private final URI target;
    private LocalDatabaseSupport(String url, String username, String password, URI target) {
        this.url = url; this.username = username; this.password = password; this.target = target;
    }
    public static LocalDatabaseSupport load() throws Exception {
        Properties local = new Properties();
        Path file = Path.of(System.getProperty("cms.config.file", "src/main/resources/jdbc-local.properties"));
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file)) { local.load(reader); }
        }
        return from(local, System.getenv());
    }
    public static LocalDatabaseSupport from(Properties local, Map<String, String> environment) {
        String url = local.getProperty("jdbc.url", environment.getOrDefault("CMS_DB_URL", DEFAULT_URL));
        String username = local.getProperty("jdbc.username", environment.getOrDefault("CMS_DB_USERNAME", ""));
        String password = local.getProperty("jdbc.password", environment.getOrDefault("CMS_DB_PASSWORD", ""));
        if (username.isBlank() || password.isBlank() || username.startsWith("YOUR_") || password.startsWith("YOUR_")) {
            throw new IllegalStateException("Configure local database credentials before running this tool");
        }
        URI target;
        try {
            if (!url.startsWith("jdbc:mysql://")) throw new IllegalArgumentException();
            target = URI.create(url.substring(5));
            if (!Set.of("127.0.0.1", "localhost", "::1", "[::1]").contains(target.getHost()) ||
                    !"/cms_content".equals(target.getPath()) || target.getUserInfo() != null || target.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IllegalStateException("Local tools require a loopback MySQL URL targeting cms_content");
        }
        return new LocalDatabaseSupport(url, username, password, target);
    }
    public Connection connect(boolean serverOnly) throws Exception {
        String connectionUrl = serverOnly ? "jdbc:" + new URI(target.getScheme(), null, target.getHost(),
            target.getPort(), "/", target.getQuery(), null) : url;
        Properties options = new Properties();
        options.setProperty("user", username); options.setProperty("password", password);
        options.setProperty("connectTimeout", "3000"); options.setProperty("socketTimeout", "5000");
        return DriverManager.getConnection(connectionUrl, options);
    }
    public static Set<String> columns(Connection connection, String table) throws SQLException {
        Set<String> result = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) { while (rows.next()) result.add(rows.getString(1)); }
        }
        return result;
    }
    public static long scalar(Connection connection, String sql) throws SQLException {
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(sql)) {
            if (!rows.next()) throw new SQLException("No scalar result");
            return rows.getLong(1);
        }
    }
    public static void script(Connection connection, String file) {
        ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource(file), "UTF-8"));
    }
    public static void verifyCampusSchema(Connection connection) throws SQLException {
        verifyBaseline(connection);
        if (!columns(connection, "cms_article").containsAll(Set.of("item_type", "event_time", "location",
                "contact_info", "resolution_status", "resolved_at")) ||
                !columns(connection, "cms_claim_request").containsAll(Set.of("article_id", "applicant_id",
                "claim_description", "contact_info", "status", "reviewer_id", "review_comment", "cancel_reason",
                "reviewed_at", "completed_at", "version", "active_applicant_id", "approved_article_id"))) {
            throw new IllegalStateException("Campus schema is incomplete; review the migration before continuing");
        }
        if (scalar(connection, "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics " +
                "WHERE table_schema=DATABASE() AND table_name='cms_claim_request' AND non_unique=0 " +
                "AND index_name IN ('uk_claim_active_applicant','uk_claim_approved_item')") != 2) {
            throw new IllegalStateException("Claim uniqueness indexes are missing");
        }
    }
    public static void verifyBaseline(Connection connection) throws SQLException {
        if (scalar(connection, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() " +
                "AND table_name IN ('sys_user','sys_role','sys_permission','sys_user_role','sys_role_permission'," +
                "'cms_category','cms_article','cms_review_record','sys_operation_log')") != 9) {
            throw new IllegalStateException("The complete nine-table CMS baseline is required");
        }
        if (scalar(connection, "SELECT COUNT(*) FROM sys_role WHERE deleted=0 " +
                "AND code IN ('ADMIN','EDITOR','REVIEWER','VIEWER')") != 4) {
            throw new IllegalStateException("Required role metadata is missing");
        }
    }
    public static void safeFailure(Exception error) {
        System.err.println(error instanceof IllegalStateException ? error.getMessage() :
            "Local database operation failed: " + error.getClass().getSimpleName());
    }
}
