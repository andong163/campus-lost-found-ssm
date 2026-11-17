package com.team.cms.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** 仅访问带专用目录、库名及库内随机归属标记的测试数据库。不会连接 cms_content。 */
public final class GmyR4Database {
    private GmyR4Database() { }
    public static void validateUrl(String url, String schema) {
        GmyR3Fixture.validateSchema(schema);
        URI uri = URI.create(url.replaceFirst("^jdbc:", ""));
        if (!url.startsWith("jdbc:mysql://") || !"mysql".equals(uri.getScheme())
                || !Set.of("127.0.0.1", "localhost").contains(uri.getHost())
                || !("/" + schema).equals(uri.getPath()) || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getRawQuery() != null &&
                    uri.getRawQuery().toLowerCase(Locale.ROOT).matches(".*(?:allowloadlocalinfile|allowmultiqueries)=true.*"))
            throw new IllegalArgumentException("只允许本次本机隔离测试库");
    }
    public static Connection connect(Path directory) throws Exception {
        Path run = GmyR3Fixture.validateRunDirectory(Path.of(""), directory);
        var manifest = new ObjectMapper().readTree(run.resolve("fixture.json").toFile());
        String schema = manifest.path("schema").asText();
        if (!"cms-ssm:gmy-r3".equals(manifest.path("owner").asText())
                || !schema.equals("cms_gmy_r3_" + run.getFileName().toString().substring(7)))
            throw new IllegalArgumentException("测试目录与所有者不符");
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(run.resolve("fixture-jdbc.properties"), StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        validateUrl(values.getProperty("jdbc.url"), schema);
        Connection db = DriverManager.getConnection(values.getProperty("jdbc.url"),
            values.getProperty("jdbc.username"), values.getProperty("jdbc.password"));
        try (PreparedStatement sql = db.prepareStatement("SELECT token FROM gmy_r3_fixture_guard WHERE owner=?")) {
            sql.setString(1, "cms-ssm:gmy-r3");
            try (ResultSet rows = sql.executeQuery()) {
                if (!rows.next() || !rows.getString(1).equals(manifest.path("marker").asText()))
                    throw new IllegalStateException("数据库测试归属标记不匹配");
            }
            return db;
        } catch (Exception error) { db.close(); throw error; }
    }
}
