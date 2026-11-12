package com.team.cms;

import com.team.cms.common.security.PermissionCodes;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 郭金滔 GJT R4 验收测试：身份契约静态审计。
 *
 * <p>行为矩阵由 {@link PermissionMatrixTest} 覆盖，本类从源码层面回答四个问题：</p>
 * <ol>
 *   <li>每个业务端点是否都声明了权限要求，未声明的方法必须逐条登记并说明理由（不按数量放行）；</li>
 *   <li>注解里写的权限码是否真实存在（是 {@link PermissionCodes} 中的规划码，或种子脚本里的已发布码）；</li>
 *   <li>种子脚本里的每个权限码是否仍被使用（遗留码必须显式登记，避免种子无限膨胀）；</li>
 *   <li>登记豁免的方法是否还存在（避免白名单腐化后继续掩盖新增裸端点）。</li>
 * </ol>
 */
public class IdentityContractAuditTest {
    private static final Path MAIN = Paths.get("src/main/java/com/team/cms");
    private static final Path SQL_DIR = Paths.get("sql");

    private static final Pattern MAPPING = Pattern.compile("@(Get|Post|Put|Delete|Patch)Mapping");
    private static final Pattern PERMISSION = Pattern.compile("@RequirePermission\\(\\s*([^)]+?)\\s*\\)");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern CONSTANT =
        Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern METHOD_NAME = Pattern.compile("(\\w+)\\s*\\(");
    private static final Pattern SEED_INSERT =
        Pattern.compile("INSERT\\s+INTO\\s+sys_permission", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEED_CODE = Pattern.compile("'([a-z][a-z0-9:._-]+)'");

    /**
     * 不声明 {@code @RequirePermission} 的端点白名单：控制器名 → 方法名 → 理由。
     *
     * <p>逐条登记而不是按数量放行，是为了让新增的裸端点在编译期之外也能被测试发现；
     * 方法改名或删除时，{@link #allowedMethodsStillExist()} 会提示同步清理。</p>
     */
    private static final Map<String, Map<String, String>> ALLOWED = new LinkedHashMap<>();
    static {
        Map<String, String> auth = new LinkedHashMap<>();
        auth.put("register", "注册必须匿名可访问");
        auth.put("login", "登录必须匿名可访问");
        auth.put("csrf", "取写请求令牌由登录拦截器保护，不带业务权限");
        auth.put("logout", "退出只操作本人会话");
        auth.put("me", "查询本人身份，返回内容由会话决定");
        ALLOWED.put("AuthController.java", auth);

        Map<String, String> health = new LinkedHashMap<>();
        health.put("health", "运维健康检查");
        health.put("readiness", "运维就绪检查");
        ALLOWED.put("SystemHealthController.java", health);

        Map<String, String> publicCategory = new LinkedHashMap<>();
        publicCategory.put("tree", "公开分类树对游客开放");
        ALLOWED.put("PublicCategoryController.java", publicCategory);

        Map<String, String> reviews = new LinkedHashMap<>();
        reviews.put("records", "审核记录按作者本人或审核人员所有权在 Service 校验");
        ALLOWED.put("ReviewController.java", reviews);

        Map<String, String> publicItems = new LinkedHashMap<>();
        publicItems.put("list", "公开检索接口，路径已进登录白名单 /api/public/items");
        publicItems.put("detail", "公开详情接口，路径已进登录白名单 /api/public/items/**");
        ALLOWED.put("PublicArticleController.java", publicItems);

        Map<String, String> management = new LinkedHashMap<>();
        management.put("delete",
            "ItemWorkflowServiceImpl.delete 内按作者本人 + item:delete:own / item:delete:any 判定");
        ALLOWED.put("ArticleManagementController.java", management);

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("mine", "只返回当前用户本人的申请，Service 内用 CurrentUserContext 过滤");
        claims.put("forItem", "ClaimReadPolicy 判定发布者本人 / 已核实申请人 / 处理人员 / 审核人员");
        claims.put("handover", "发布者交接上下文，ClaimReadPolicy 判定且不返回联系方式");
        claims.put("foundContact", "ClaimReadPolicy 判定并记录 FOUND_CONTACT_READ 审计");
        ALLOWED.put("ClaimController.java", claims);
    }

    /**
     * 种子脚本中已发布但仍然无人引用的遗留权限码。
     *
     * <p>团队约定已发布的迁移不回写（docs/r1-total-integration.md:22），第一轮为兼容旧调用保留了
     * article:* / claim:* 等代码；真正生效的是 PermissionCodes 中的 item:* / claim:* 规划码，
     * 待统一命名后由新编号迁移处理。这里显式登记，保证不会再有新的无效种子混进来。</p>
     */
    private static final Set<String> LEGACY_UNUSED = new TreeSet<>(Arrays.asList(
        "article:create", "article:edit", "article:publish", "claim:handle", "claim:resolve",
        "permission:manage"));

    @Test public void everyEndpointDeclaresAPermission() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Path file : controllers()) {
            String name = file.getFileName().toString();
            Map<String, String> allowed = ALLOWED.getOrDefault(name, new LinkedHashMap<>());
            Set<String> documented = new HashSet<>(allowed.keySet());
            for (Endpoint endpoint : endpoints(read(file))) {
                if (endpoint.declared) {
                    continue;
                }
                if (!documented.contains(endpoint.method)) {
                    problems.add(name + " -> " + endpoint.method + "() 未声明 @RequirePermission，且未登记理由");
                }
            }
        }
        assertTrue("存在未声明权限的端点：\n" + String.join("\n", problems), problems.isEmpty());
    }

    @Test public void allowedMethodsStillExist() throws Exception {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : ALLOWED.entrySet()) {
            String name = entry.getKey();
            List<Path> matched = controllers().stream()
                .filter(p -> p.getFileName().toString().equals(name))
                .collect(Collectors.toList());
            if (matched.isEmpty()) {
                problems.add(name + " 已不存在，请清理豁免登记");
                continue;
            }
            Set<String> actual = endpoints(read(matched.get(0))).stream()
                .map(e -> e.method)
                .collect(Collectors.toSet());
            for (String method : entry.getValue().keySet()) {
                if (!actual.contains(method)) {
                    problems.add(name + " -> " + method + "() 不在当前端点列表中，请清理豁免登记");
                }
            }
        }
        assertTrue("豁免登记与源码不一致：\n" + String.join("\n", problems), problems.isEmpty());
    }

    @Test public void annotationPermissionCodesAreReal() throws Exception {
        Set<String> known = new HashSet<>(PermissionCodes.all());
        known.addAll(seededCodes());
        Map<String, String> constants = constants();

        Set<String> problems = new TreeSet<>();
        for (Path file : controllers()) {
            Matcher matcher = PERMISSION.matcher(read(file));
            while (matcher.find()) {
                String argument = matcher.group(1).trim();
                String code = resolve(argument, constants);
                if (code == null) {
                    problems.add(file.getFileName() + " -> " + argument + "（无法解析）");
                } else if (!known.contains(code)) {
                    problems.add(file.getFileName() + " -> " + argument + " = " + code
                        + "（不在规划码或种子中）");
                }
            }
        }
        assertTrue("存在无法解析或不存在的权限码：\n" + String.join("\n", problems), problems.isEmpty());
    }

    @Test public void seededCodesAreAllUsed() throws Exception {
        Set<String> seeds = seededCodes();
        Set<String> dead = new TreeSet<>(seeds);
        dead.removeAll(annotationCodes());
        dead.removeAll(PermissionCodes.all());
        dead.removeAll(LEGACY_UNUSED);

        assertTrue("种子脚本中出现无人使用的权限码，请登记或删除：\n" + String.join("\n", dead),
            dead.isEmpty());
        assertFalse("应能读到权限种子脚本", seeds.isEmpty());

        Set<String> stale = new TreeSet<>(LEGACY_UNUSED);
        stale.removeAll(seeds);
        assertTrue("遗留码清单里有种子中不存在的条目，请清理：" + stale, stale.isEmpty());
    }

    @Test public void seedCoversAllRoles() throws Exception {
        String seed = read(SQL_DIR.resolve("migrations/003-auth-permission-seed.sql"));
        for (String role : new String[] {"ADMIN", "EDITOR", "REVIEWER"}) {
            assertTrue("003 缺少角色 " + role + " 的授权语句", seed.contains("'" + role + "'"));
        }
        assertTrue("VIEWER 应在 003 中说明「无写权限」", seed.contains("VIEWER"));
    }

    // ---------- 辅助方法 ----------

    /** 逐个映射注解解析「该端点是否带 @RequirePermission」，并取出方法名。 */
    private static List<Endpoint> endpoints(String text) {
        List<Endpoint> result = new ArrayList<>();
        Matcher matcher = MAPPING.matcher(text);
        while (matcher.find()) {
            // 跳过一个完整的映射注解（路径里可能自带 {itemId} 这类占位符，不能直接找第一个花括号）。
            int start = skipAnnotationArguments(text, matcher.end());
            int brace = text.indexOf('{', start);
            if (brace < 0) {
                continue;
            }
            String head = text.substring(start, brace);
            Matcher names = METHOD_NAME.matcher(head);
            String method = null;
            while (names.find()) {
                method = names.group(1);
            }
            if (method == null) {
                continue;
            }
            result.add(new Endpoint(method, head.contains("@RequirePermission")));
        }
        return result;
    }

    /** 从注解名之后开始，跳过空白与注解参数括号（含字符串里的括号），返回方法体前的搜索起点。 */
    private static int skipAnnotationArguments(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        if (i >= text.length() || text.charAt(i) != '(') {
            return i;
        }
        int depth = 0;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                i++;
                while (i < text.length() && text.charAt(i) != '"') {
                    if (text.charAt(i) == '\\') {
                        i++;
                    }
                    i++;
                }
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
        }
        return text.length();
    }

    private static Set<String> annotationCodes() throws IOException {
        Map<String, String> constants = constants();
        Set<String> codes = new TreeSet<>();
        for (Path file : controllers()) {
            Matcher matcher = PERMISSION.matcher(read(file));
            while (matcher.find()) {
                String code = resolve(matcher.group(1).trim(), constants);
                if (code != null) {
                    codes.add(code);
                }
            }
        }
        return codes;
    }

    private static Map<String, String> constants() throws IOException {
        Map<String, String> constants = new HashMap<>();
        try (Stream<Path> files = Files.walk(MAIN)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList())) {
                Matcher matcher = CONSTANT.matcher(read(file));
                while (matcher.find()) {
                    constants.put(matcher.group(1), matcher.group(2));
                }
            }
        }
        return constants;
    }

    private static String resolve(String argument, Map<String, String> constants) {
        Matcher quoted = QUOTED.matcher(argument);
        if (quoted.find()) {
            return quoted.group(1);
        }
        String last = argument.contains(".") ? argument.substring(argument.lastIndexOf('.') + 1)
            : argument;
        return constants.get(last);
    }

    /** 只解析 {@code INSERT INTO sys_permission} 语句里的权限码，避免把枚举值当成权限码。 */
    private static Set<String> seededCodes() throws IOException {
        Set<String> result = new TreeSet<>();
        for (Path file : seedFiles()) {
            String text = read(file);
            Matcher insert = SEED_INSERT.matcher(text);
            int from = 0;
            while (insert.find(from)) {
                int end = text.indexOf(';', insert.end());
                String block = end < 0 ? text.substring(insert.end()) : text.substring(insert.end(), end);
                Matcher code = SEED_CODE.matcher(block);
                while (code.find()) {
                    result.add(code.group(1));
                }
                from = end < 0 ? text.length() : end + 1;
            }
        }
        return result;
    }

    private static List<Path> seedFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        Path init = SQL_DIR.resolve("init.sql");
        if (Files.exists(init)) {
            files.add(init);
        }
        Path migrations = SQL_DIR.resolve("migrations");
        if (Files.exists(migrations)) {
            try (Stream<Path> walk = Files.walk(migrations)) {
                files.addAll(walk.filter(p -> p.toString().endsWith(".sql")).sorted()
                    .collect(Collectors.toList()));
            }
        }
        return files;
    }

    private static List<Path> controllers() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                .sorted()
                .collect(Collectors.toList());
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static final class Endpoint {
        final String method;
        final boolean declared;

        Endpoint(String method, boolean declared) {
            this.method = method;
            this.declared = declared;
        }
    }
}
