package com.team.cms;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * GJT R3 页面契约：入口清单、页面文件与服务端实际权限必须一致。
 * <p>纯文件级校验，不启动 Spring 容器，因此不拖慢构建：</p>
 * <ul>
 *   <li>pages/auth/module.json 是公共 shell 读取的唯一入口清单，其 permission 必须真的被某个
 *       {@code @RequirePermission} 使用且存在于 SQL 权限种子中，避免页面显示服务端不认识的入口；</li>
 *   <li>入口格式必须满足 shell.js 的校验规则（本模块目录、HTML 文件）；</li>
 *   <li>页面必须接入高鸣远公共组件（base.css / api.js / shell.js / data-cms-shell），
 *       不得自带第二套请求层或直接调用 fetch；</li>
 *   <li>受保护页面必须做客户端权限判定，与服务端校验形成双保险。</li>
 * </ul>
 */
public class AuthPageContractTest {
    private static final Path WEBAPP = Path.of("src/main/webapp");
    private static final Path MANIFEST = WEBAPP.resolve("pages/auth/module.json");
    /** 与 shell.js 中入口过滤规则保持一致。 */
    private static final Pattern PAGE_PATTERN = Pattern.compile("^pages/[a-z]+/[a-zA-Z0-9_/-]+\\.html$");
    private static final Pattern ANNOTATION = Pattern.compile("@RequirePermission\\(\\s*\"([^\"]+)\"\\s*\\)");

    private JsonNode manifest() throws IOException {
        return new ObjectMapper().readTree(Files.readString(MANIFEST));
    }

    private Set<String> enforcedPermissions() throws IOException {
        Set<String> codes = new HashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList())) {
                Matcher matcher = ANNOTATION.matcher(Files.readString(file));
                while (matcher.find()) {
                    codes.add(matcher.group(1));
                }
            }
        }
        return codes;
    }

    private String allSql() throws IOException {
        StringBuilder text = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("sql"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".sql"))
                    .collect(Collectors.toList())) {
                text.append(Files.readString(file)).append('\n');
            }
        }
        return text.toString();
    }

    @Test public void manifestDeclaresModuleAndValidEntries() throws Exception {
        JsonNode root = manifest();
        assertEquals("清单的 module 必须与目录名一致", "auth", root.path("module").asText());
        JsonNode entries = root.get("entries");
        assertNotNull("module.json 必须包含 entries 数组", entries);
        assertTrue(entries.isArray());
        assertTrue("身份模块至少应声明一个入口", entries.size() > 0);

        Set<String> labels = new HashSet<>();
        for (JsonNode entry : entries) {
            String label = entry.path("label").asText();
            assertFalse("入口缺少 label", label.isEmpty());
            assertTrue("入口 label 超过 shell 限定的 30 字: " + label, label.length() <= 30);
            assertTrue("入口 label 重复: " + label, labels.add(label));

            String page = entry.path("page").asText();
            assertTrue("入口 page 必须满足 shell.js 的路径规则: " + page, PAGE_PATTERN.matcher(page).matches());
            assertTrue("入口只能指向 auth 模块目录: " + page, page.startsWith("pages/auth/"));
            assertTrue("入口指向的页面不存在: " + page, Files.isRegularFile(WEBAPP.resolve(page)));
        }
    }

    @Test public void manifestPermissionsAreRealAndEnforced() throws Exception {
        Set<String> enforced = enforcedPermissions();
        String sql = allSql();
        assertFalse("服务器端应至少声明一个 @RequirePermission", enforced.isEmpty());

        for (JsonNode entry : manifest().get("entries")) {
            JsonNode permission = entry.get("permission");
            if (permission == null || permission.isNull() || permission.asText().isEmpty()) {
                fail("受保护入口必须声明 permission: " + entry.path("page").asText());
            }
            String code = permission.asText();
            assertTrue("入口声明的权限没有被任何 @RequirePermission 使用: " + code,
                enforced.contains(code));
            assertTrue("入口声明的权限不在 SQL 种子中: " + code, sql.contains(code));
        }
    }

    @Test public void pagesUseSharedComponentsAndGuardPermissions() throws Exception {
        Pattern moduleScript = Pattern.compile("assets/js/auth/([a-zA-Z0-9_]+\\.js)");
        for (JsonNode entry : manifest().get("entries")) {
            String page = entry.get("page").asText();
            String html = Files.readString(WEBAPP.resolve(page));
            assertTrue("页面必须引入公共请求封装: " + page, html.contains("assets/js/api.js"));
            assertTrue("页面必须引入公共布局: " + page, html.contains("assets/js/shell.js"));
            assertTrue("页面必须引入公共样式: " + page, html.contains("assets/css/base.css"));
            assertTrue("页面必须提供 shell 挂载点: " + page, html.contains("data-cms-shell"));
            assertFalse("页面不得直接调用 fetch，应使用 Cms.api: " + page, html.contains("fetch("));
            assertFalse("页面不得自带第二套请求层: " + page, html.contains("assets/js/auth/api.js"));

            Matcher matcher = moduleScript.matcher(html);
            assertTrue("页面必须引入本模块脚本: " + page, matcher.find());
            String script = Files.readString(WEBAPP.resolve("assets/js/auth/" + matcher.group(1)));
            assertTrue("模块脚本必须使用公共请求层: " + page, script.contains("Cms.api.request("));
            assertTrue("受保护页面缺少客户端权限判定: " + page, script.contains("Cms.shell.hasPermission("));
        }
    }

    @Test public void loginPageStaysPubliclyReachable() throws Exception {
        Path login = WEBAPP.resolve("pages/auth/login.html");
        assertTrue("登录页必须存在", Files.isRegularFile(login));
        String html = Files.readString(login);
        assertTrue("登录页必须走统一请求封装", html.contains("assets/js/api.js"));
        assertTrue("登录页必须引入登录脚本", html.contains("assets/js/auth/login.js"));
        Path script = WEBAPP.resolve("assets/js/auth/login.js");
        assertTrue("登录脚本必须使用 Cms.api 而不是自造请求", Files.readString(script).contains("Cms.api.request("));
    }
}
