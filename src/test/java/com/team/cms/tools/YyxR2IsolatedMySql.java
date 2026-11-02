package com.team.cms.tools;

import com.team.cms.auth.security.PasswordHasher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Manual R2 integration fixture. Only creates/drops a guarded temporary MySQL schema. */
public final class YyxR2IsolatedMySql {
    private YyxR2IsolatedMySql() { }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || (!"prepare".equals(args[0]) && !"cleanup".equals(args[0])))
            throw new IllegalArgumentException("Usage: prepare | cleanup <schema> <temp-config-path>");
        Properties local = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/jdbc.properties"))) {
            local.load(reader);
        }
        try (var reader = Files.newBufferedReader(Path.of("src/main/resources/jdbc-local.properties"))) {
            local.load(reader);
        }
        org.springframework.util.PropertyPlaceholderHelper placeholders =
            new org.springframework.util.PropertyPlaceholderHelper("${", "}", ":", false);
        String url=placeholders.replacePlaceholders(local.getProperty("jdbc.url"), System::getenv);
        String user=placeholders.replacePlaceholders(local.getProperty("jdbc.username"), System::getenv);
        String password=placeholders.replacePlaceholders(local.getProperty("jdbc.password"), System::getenv);
        URI uri=URI.create(url.substring(5));
        if (!url.startsWith("jdbc:mysql://") || !Set.of("127.0.0.1","localhost").contains(uri.getHost()) ||
                !"/cms_content".equals(uri.getPath()) || user==null || password==null)
            throw new IllegalStateException("Only local cms_content credentials are accepted");
        String root="jdbc:"+new URI("mysql",null,uri.getHost(),uri.getPort(),"/",uri.getQuery(),null);
        if ("cleanup".equals(args[0])) {
            if (args.length!=3 || !args[1].matches("cms_yyx_r2_[0-9a-f]{8}"))
                throw new IllegalArgumentException("Refusing to remove a non-R2 schema");
            try (Connection server=DriverManager.getConnection(root,user,password); Statement statement=server.createStatement()) {
                statement.execute("DROP DATABASE `"+args[1]+"`");
            }
            Files.deleteIfExists(Path.of(args[2]));
            System.out.println("Cleaned isolated schema "+args[1]);
            return;
        }
        String schema="cms_yyx_r2_"+UUID.randomUUID().toString().substring(0,8);
        String dbUrl="jdbc:"+new URI("mysql",null,uri.getHost(),uri.getPort(),"/"+schema,uri.getQuery(),null);
        try (Connection server=DriverManager.getConnection(root,user,password); Statement statement=server.createStatement()) {
            statement.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try {
            try (Connection db=DriverManager.getConnection(dbUrl,user,password)) {
                String init=Files.readString(Path.of("sql/init.sql"));
                init=init.replaceFirst("(?m)^CREATE DATABASE IF NOT EXISTS cms_content[^;]*;","")
                         .replaceFirst("(?m)^USE cms_content;","");
                script(db,init);
                script(db,Files.readString(Path.of("sql/migrations/003-auth-permission-seed.sql")));
                script(db,Files.readString(Path.of("sql/migrations/004-item-review-permissions.sql")));
                script(db,Files.readString(Path.of("sql/migrations/005-r2-category-permissions.sql")));
                try (Statement statement=db.createStatement()) {
                    statement.executeUpdate("INSERT INTO cms_category(name,sort_order,enabled) VALUES('测试分类',0,1)");
                }
                createUser(db,"r2student","EDITOR");
                createUser(db,"r2reviewer","REVIEWER");
                createUser(db,"r2admin","ADMIN");
            }
            Properties testConfig=new Properties();
            testConfig.setProperty("jdbc.driver","com.mysql.cj.jdbc.Driver");
            testConfig.setProperty("jdbc.url",dbUrl);
            testConfig.setProperty("jdbc.username",user);
            testConfig.setProperty("jdbc.password",password);
            Path config=Files.createTempFile("yyx-r2-mysql-",".properties");
            // Windows Temp inherits the current user's ACL and has no POSIX permission view.
            if (Files.getFileStore(config).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(config,PosixFilePermissions.fromString("rw-------"));
            try (var writer=Files.newBufferedWriter(config)) { testConfig.store(writer,"R2 isolated integration only"); }
            System.out.println("SCHEMA="+schema);
            System.out.println("CONFIG="+config);
        } catch (Exception error) {
            try (Connection server=DriverManager.getConnection(root,user,password); Statement statement=server.createStatement()) {
                statement.execute("DROP DATABASE `"+schema+"`");
            }
            throw error;
        }
    }
    private static void script(Connection db, String sql) {
        ScriptUtils.executeSqlScript(db,new EncodedResource(
            new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));
    }
    private static void createUser(Connection db,String username,String role) throws Exception {
        try (PreparedStatement insert=db.prepareStatement(
                "INSERT INTO sys_user(username,password_hash,display_name,enabled) VALUES(?,?,?,1)",
                Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1,username); insert.setString(2,new PasswordHasher().hash("password123"));
            insert.setString(3,username); insert.executeUpdate();
            try (ResultSet keys=insert.getGeneratedKeys()) {
                keys.next();
                try (PreparedStatement grant=db.prepareStatement(
                        "INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code=?")) {
                    grant.setLong(1,keys.getLong(1)); grant.setString(2,role); grant.executeUpdate();
                }
            }
        }
    }
}
