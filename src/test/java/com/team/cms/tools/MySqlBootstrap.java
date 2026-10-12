package com.team.cms.tools;

import java.sql.*;

/** Explicit local developer command, never executed as part of automatic tests. */
public final class MySqlBootstrap {
    public static void main(String[] args) {
        try (Connection connection = LocalDatabaseSupport.load().connect(true)) {
            if (LocalDatabaseSupport.scalar(connection,
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='cms_content'") != 0) {
                throw new IllegalStateException("cms_content already contains tables; use upgrade-db.ps1 instead");
            }
            LocalDatabaseSupport.script(connection, "sql/init.sql");
            connection.setCatalog("cms_content");
            LocalDatabaseSupport.verifyCampusSchema(connection);
            MySqlCampusUpgrade.verifyClaimConstraints(connection);
            System.out.println("MySQL campus baseline verified: ten business tables, role metadata, uniqueness and rollback.");
        } catch (Exception error) {
            LocalDatabaseSupport.safeFailure(error); System.exit(1);
        }
    }
}
