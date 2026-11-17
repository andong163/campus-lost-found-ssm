package com.team.cms.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team.cms.auth.security.PasswordHasher;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.PropertyPlaceholderHelper;

/** GMY R3 独立 MySQL 夹具；cleanup 需同时匹配专用库名、目录及库内所有权标记。 */
public final class GmyR3Fixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String OWNER = "cms-ssm:gmy-r3";
    private GmyR3Fixture() { }
    public static void validateSchema(String schema) {
        if (schema == null || !schema.matches("cms_gmy_r3_[0-9a-f]{12}"))
            throw new IllegalArgumentException("拒绝操作非 GMY R3 专用测试库");
    }
    public static Path validateRunDirectory(Path root, Path run) throws Exception {
        Path local = root.toRealPath().resolve(".local").normalize();
        if (Files.exists(local) && !local.toRealPath().equals(local))
            throw new IllegalArgumentException("项目 .local 不能经符号链接指向其他目录");
        Path absolute = run.toAbsolutePath().normalize();
        if (!absolute.startsWith(local) || !absolute.getParent().equals(local)
                || !absolute.getFileName().toString().matches("gmy-r3-[0-9a-f]{12}"))
            throw new IllegalArgumentException("验收目录必须是项目 .local/gmy-r3-十二位编号");
        if (Files.exists(absolute) && (!absolute.toRealPath().equals(absolute) || Files.isSymbolicLink(absolute)))
            throw new IllegalArgumentException("验收目录不能经符号链接指向其他位置");
        return absolute;
    }
    private static Properties config(Path root) throws Exception {
        Properties values = new Properties();
        for (String relative : List.of("src/main/resources/jdbc.properties", "src/main/resources/jdbc-local.properties")) {
            Path path=root.resolve(relative);
            if (Files.exists(path)) try (var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) { values.load(reader); }
        }
        PropertyPlaceholderHelper helper=new PropertyPlaceholderHelper("${","}",":",false);
        for (String name : List.of("jdbc.url","jdbc.username","jdbc.password"))
            values.setProperty(name,helper.replacePlaceholders(values.getProperty(name,""),System::getenv));
        URI uri=URI.create(values.getProperty("jdbc.url").replaceFirst("^jdbc:",""));
        if (!"mysql".equals(uri.getScheme()) || !Set.of("127.0.0.1","localhost").contains(uri.getHost())
                || !"/cms_content".equals(uri.getPath()))
            throw new IllegalArgumentException("测试只复用本机 cms_content 连接凭据，业务库不执行 SQL");
        values.setProperty("server.url","jdbc:"+new URI("mysql",null,uri.getHost(),uri.getPort(),"/",uri.getQuery(),null));
        return values;
    }
    private static Connection connect(Properties values,String url) throws SQLException {
        return DriverManager.getConnection(url,values.getProperty("jdbc.username"),values.getProperty("jdbc.password"));
    }
    private static void script(Connection db,String sql) {
        ScriptUtils.executeSqlScript(db,new EncodedResource(new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)),StandardCharsets.UTF_8));
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=2 || !Set.of("prepare","cleanup").contains(args[0]))
            throw new IllegalArgumentException("Usage: prepare|cleanup .local/gmy-r3-<12 hex>");
        Path root=Path.of("").toRealPath(), run=validateRunDirectory(root,Path.of(args[1]));
        if ("cleanup".equals(args[0]) && Files.exists(run.resolve("cleanup.json"))) {
            System.out.println("该验收环境已清理"); return;
        }
        Properties local=config(root);
        if ("prepare".equals(args[0])) prepare(root,run,local); else cleanup(run,local);
    }
    private static void prepare(Path root,Path run,Properties local) throws Exception {
        if (Files.exists(run.resolve("fixture.json"))) throw new IllegalStateException("已有夹具，拒绝覆盖");
        Files.createDirectories(run);
        String suffix=run.getFileName().toString().substring("gmy-r3-".length());
        String schema="cms_gmy_r3_"+suffix; validateSchema(schema);
        String marker=UUID.randomUUID().toString(), password=UUID.randomUUID().toString();
        URI original=URI.create(local.getProperty("server.url").substring(5));
        String dbUrl="jdbc:"+new URI("mysql",null,original.getHost(),original.getPort(),"/"+schema,original.getQuery(),null);
        try (Connection server=connect(local,local.getProperty("server.url")); Statement sql=server.createStatement()) {
            sql.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        try {
            try (Connection db=connect(local,dbUrl)) {
                String init=Files.readString(root.resolve("sql/init.sql"),StandardCharsets.UTF_8)
                    .replaceFirst("(?m)^CREATE DATABASE IF NOT EXISTS cms_content[^;]*;","")
                    .replaceFirst("(?m)^USE cms_content;","");
                if (init.matches("(?is).*\\b(?:USE|CREATE\\s+DATABASE)\\s+cms_content\\b.*"))
                    throw new IllegalStateException("初始化脚本仍包含业务库切换语句，拒绝执行");
                boolean previousBaseline = Boolean.getBoolean("cms.gmyR3.previousBaseline");
                if (previousBaseline) {
                    int markerIndex = init.indexOf("-- 006-r3-item-claim-permissions.sql：");
                    if (markerIndex < 0) throw new IllegalStateException("缺少第二轮种子边界");
                    init = init.substring(0, markerIndex);
                }
                script(db,init);
                if (previousBaseline) try (Statement sql = db.createStatement()) {
                    sql.executeUpdate("INSERT INTO sys_role(code,name) VALUES('GMY_R3_CUSTOM','迁移保留测试角色')");
                    sql.executeUpdate("INSERT INTO sys_role_permission(role_id,permission_id) SELECT r.id,p.id FROM sys_role r JOIN sys_permission p ON p.code='audit:view' WHERE r.code='GMY_R3_CUSTOM'");
                }
                for (String name : List.of("003-auth-permission-seed.sql","004-item-review-permissions.sql","005-r2-category-permissions.sql","006-r3-item-claim-permissions.sql"))
                    script(db,Files.readString(root.resolve("sql/migrations/"+name),StandardCharsets.UTF_8));
                script(db,Files.readString(root.resolve("sql/migrations/006-r3-item-claim-permissions.sql"),StandardCharsets.UTF_8));
                String codes = "('item:view:any','item:batch','item:offline:any','item:delete:any','item:delete:own','claim:create','claim:cancel:own','claim:handover:own')";
                Map<String,Object> migration = new LinkedHashMap<>();
                migration.put("mode", previousBaseline ? "round2-upgrade" : "fresh-init");
                migration.put("permissionCount", count(db,"SELECT COUNT(*) FROM sys_permission WHERE code IN " + codes));
                for (String role : List.of("ADMIN","EDITOR","REVIEWER","VIEWER")) {
                    long granted = count(db,"SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.code='"+role+"' AND p.code IN " + codes);
                    long expected = role.equals("ADMIN") ? 8 : role.equals("EDITOR") ? 4 : 0;
                    if (granted != expected) throw new IllegalStateException("第三轮默认角色授权不符");
                    migration.put(role + "NewGrants", granted);
                }
                if (!migration.get("permissionCount").equals(8L)) throw new IllegalStateException("第三轮权限登记不完整");
                if (previousBaseline && count(db,"SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.code='GMY_R3_CUSTOM' AND p.code='audit:view'") != 1)
                    throw new IllegalStateException("升级改变了自定义角色原授权");
                migration.put("repeatedMigrationPassed", true);
                migration.put("customGrantPreserved", previousBaseline);
                JSON.writerWithDefaultPrettyPrinter().writeValue(run.resolve("migration-results.json").toFile(),migration);
                try (Statement sql=db.createStatement()) {
                    sql.execute("CREATE TABLE gmy_r3_fixture_guard(owner VARCHAR(64) PRIMARY KEY, token VARCHAR(36) NOT NULL)");
                }
                try (PreparedStatement sql=db.prepareStatement("INSERT INTO gmy_r3_fixture_guard VALUES (?,?)")) {
                    sql.setString(1,OWNER); sql.setString(2,marker); sql.executeUpdate();
                }
                for (String role : List.of("ADMIN","EDITOR","REVIEWER","VIEWER")) {
                    try (PreparedStatement sql=db.prepareStatement("INSERT INTO sys_user(username,password_hash,display_name) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS)) {
                        sql.setString(1,"gmyr3_"+role.toLowerCase(Locale.ROOT)); sql.setString(2,new PasswordHasher().hash(password));
                        sql.setString(3,"验收"+role); sql.executeUpdate();
                        try (ResultSet ids=sql.getGeneratedKeys()) {
                            ids.next();
                            try (PreparedStatement grant=db.prepareStatement("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code=?")) {
                                grant.setLong(1,ids.getLong(1)); grant.setString(2,role); grant.executeUpdate();
                            }
                        }
                    }
                }
            }
            Properties test=new Properties(); test.setProperty("jdbc.driver","com.mysql.cj.jdbc.Driver");
            test.setProperty("jdbc.url",dbUrl); test.setProperty("jdbc.username",local.getProperty("jdbc.username"));
            test.setProperty("jdbc.password",local.getProperty("jdbc.password"));
            Path file=run.resolve("fixture-jdbc.properties");
            try (var writer=Files.newBufferedWriter(file,StandardCharsets.UTF_8)) { test.store(writer,"GMY R3 isolated test only"); }
            if (Files.getFileStore(file).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(file,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            Map<String,Object> manifest=new LinkedHashMap<>();
            manifest.put("owner",OWNER); manifest.put("schema",schema); manifest.put("marker",marker);
            manifest.put("password",password); manifest.put("createdAt",java.time.Instant.now().toString());
            Path manifestFile=run.resolve("fixture.json");
            JSON.writerWithDefaultPrettyPrinter().writeValue(manifestFile.toFile(),manifest);
            if (Files.getFileStore(manifestFile).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(manifestFile,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
            System.out.println("已准备专用 MySQL 测试库及四类账号，凭据仅保存在忽略的验收目录");
        } catch (Exception failure) {
            try (Connection server=connect(local,local.getProperty("server.url")); Statement sql=server.createStatement()) {
                sql.execute("DROP DATABASE `"+schema+"`");
            }
            Files.deleteIfExists(run.resolve("fixture-jdbc.properties"));
            throw failure;
        }
    }
    private static long count(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next(); return rows.getLong(1);
        }
    }
    private static void cleanup(Path run,Properties local) throws Exception {
        var manifest=JSON.readTree(run.resolve("fixture.json").toFile());
        String schema=manifest.path("schema").asText(); validateSchema(schema);
        if (!OWNER.equals(manifest.path("owner").asText())
                || !schema.substring("cms_gmy_r3_".length()).equals(run.getFileName().toString().substring("gmy-r3-".length())))
            throw new IllegalStateException("夹具所有者与目录不匹配，拒绝清理");
        URI server=URI.create(local.getProperty("server.url").substring(5));
        String dbUrl="jdbc:"+new URI("mysql",null,server.getHost(),server.getPort(),"/"+schema,server.getQuery(),null);
        try (Connection db=connect(local,dbUrl); PreparedStatement sql=db.prepareStatement("SELECT token FROM gmy_r3_fixture_guard WHERE owner=?")) {
            sql.setString(1,OWNER);
            try (ResultSet value=sql.executeQuery()) {
                if (!value.next() || !value.getString(1).equals(manifest.path("marker").asText()))
                    throw new IllegalStateException("库内所有权标记不匹配，拒绝删除");
            }
        }
        try (Connection db=connect(local,local.getProperty("server.url")); Statement sql=db.createStatement()) {
            sql.execute("DROP DATABASE `"+schema+"`");
            try (ResultSet remaining=sql.executeQuery("SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='"+schema+"'")) {
                if (remaining.next()) throw new IllegalStateException("测试库未清理");
            }
        }
        Files.deleteIfExists(run.resolve("fixture-jdbc.properties")); Files.deleteIfExists(run.resolve("fixture.json"));
        JSON.writerWithDefaultPrettyPrinter().writeValue(run.resolve("cleanup.json").toFile(),
            Map.of("schema",schema,"databaseDropped",true,"secretsDeleted",true,"completedAt",java.time.Instant.now().toString()));
        System.out.println("测试库、测试账号和临时凭据已清理");
    }
}
