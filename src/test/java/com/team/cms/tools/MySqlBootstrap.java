package com.team.cms.tools;

import java.nio.file.*;
import java.sql.*;
import java.util.Properties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.support.EncodedResource;

/** Explicit local developer command, never executed as part of automatic tests. */
public final class MySqlBootstrap {
    public static void main(String[] args) throws Exception {
        Properties local = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/jdbc-local.properties"))) { local.load(reader); }
        // Local bootstrap only. Remote databases must be provisioned manually by the owner.
        try (Connection connection = DriverManager.getConnection(
                "jdbc:mysql://127.0.0.1:3306/?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
                local.getProperty("jdbc.username"), local.getProperty("jdbc.password"))) {
            int existing;
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='cms_content'")) { rows.next(); existing = rows.getInt(1); }
            if (existing != 0) {
                throw new IllegalStateException("cms_content already contains tables; bootstrap refuses to modify an existing database");
            }
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new FileSystemResource("sql/init.sql"), "UTF-8"));
            try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='cms_content'")) {
                rows.next(); if (rows.getInt(1) != 9) throw new IllegalStateException("Expected nine baseline tables");
            }
            connection.setCatalog("cms_content");
            connection.setAutoCommit(false);
            String code = "TX_" + java.util.UUID.randomUUID().toString().substring(0, 20);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sys_role(code,name) VALUES (?,?)")) {
                insert.setString(1, code); insert.setString(2, "transaction probe"); insert.executeUpdate();
            }
            connection.rollback();
            try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) FROM sys_role WHERE code=?")) {
                check.setString(1, code);
                try (ResultSet rows = check.executeQuery()) { rows.next(); if (rows.getInt(1) != 0) throw new IllegalStateException("Rollback failed"); }
            }
            System.out.println("MySQL baseline verified: nine tables, initialization script executed, transaction rollback passed. No credential output.");
        }
    }
}
