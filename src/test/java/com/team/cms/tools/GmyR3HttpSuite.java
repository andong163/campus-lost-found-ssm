package com.team.cms.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** 对实际部署 WAR 发请求，不使用 MockMvc，不启动嵌入式服务器。报告不保存响应正文或凭据。 */
public final class GmyR3HttpSuite {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final String base;
    private final List<Map<String,Object>> checks=new ArrayList<>();
    private final String password;
    private static final class Session {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();
        String token;
    }
    public static void validateBase(String value) {
        URI uri=URI.create(value);
        if (!"http".equals(uri.getScheme()) || !Set.of("127.0.0.1","localhost").contains(uri.getHost())
                || !"/cms".equals(uri.getPath()) || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null)
            throw new IllegalArgumentException("验收地址只能是 http://本机:端口/cms");
    }
    private GmyR3HttpSuite(String base,String password) { validateBase(base); this.base=base; this.password=password; }
    private void expect(String name,boolean condition) {
        if (!condition) throw new AssertionError(name);
        checks.add(Map.of("name",name,"passed",true));
    }
    private JsonNode request(String name,Session session,String method,String path,Object body,int expected,boolean csrf) throws Exception {
        long start=System.nanoTime();
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type","application/json");
        if (csrf && session.token!=null) builder.header("X-CSRF-Token",session.token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),StandardCharsets.UTF_8));
        HttpResponse<String> response=session.client.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode()!=expected) throw new AssertionError(name+": expected HTTP "+expected+", got "+response.statusCode());
        JsonNode value=JSON.readTree(response.body());
        if (value.path("code").asInt()!=expected || !value.has("data") || !value.has("message"))
            throw new AssertionError(name+": Result contract mismatch");
        String requestId=response.headers().firstValue("X-Request-Id").orElse("");
        if (!requestId.matches("[A-Za-z0-9_-]{8,80}")) throw new AssertionError(name+": requestId missing");
        if (!path.equals("/api/auth/csrf") && (response.body().contains(password)
                || response.body().contains("passwordHash") || response.body().contains("password_hash")))
            throw new AssertionError(name+": sensitive response field");
        if (expected>=400 && (response.body().contains("java.sql.") || response.body().contains("SELECT ")
                || response.body().contains("jdbc:mysql:") || response.body().contains("<html")))
            throw new AssertionError(name+": unsafe error response");
        if (path.equals("/api/auth/csrf") && !response.headers().firstValue("Cache-Control").orElse("").contains("no-store"))
            throw new AssertionError(name+": token must be no-store");
        Map<String,Object> item=new LinkedHashMap<>(); item.put("name",name); item.put("passed",true);
        item.put("method",method); item.put("path",path); item.put("status",expected); item.put("requestId",requestId);
        item.put("elapsedMs",(System.nanoTime()-start)/1_000_000); checks.add(item);
        return value.get("data");
    }
    private JsonNode get(String name,Session session,String path,int status) throws Exception {
        return request(name,session,"GET",path,null,status,false);
    }
    private JsonNode write(String name,Session session,String method,String path,Object body,int status) throws Exception {
        return request(name,session,method,path,body,status,true);
    }
    private Session login(String role) throws Exception {
        Session session=new Session();
        write("登录夹具 "+role,session,"POST","/api/auth/login",Map.of("username","gmyr3_"+role,"password",password),200);
        session.token=get("读取会话令牌 "+role,session,"/api/auth/csrf",200).asText();
        return session;
    }
    private Map<String,Object> draft(long category) {
        Map<String,Object> body=new LinkedHashMap<>(); body.put("title","第三轮审核链路测试");
        body.put("content","这是高鸣远第三轮独立部署的专用测试信息，用于复验前两轮流程。");
        body.put("categoryId",category); body.put("itemType","LOST");
        body.put("eventTime",LocalDateTime.now().minusDays(1).withNano(0).toString());
        body.put("location","测试图书馆"); body.put("contactInfo","仅供隔离测试的联系方式"); return body;
    }
    private Map<String,Object> edit(Map<String,Object> draft,int version) {
        Map<String,Object> copy=new LinkedHashMap<>(draft); copy.put("version",version); return copy;
    }
    private void run() throws Exception {
        Session guest=new Session();
        get("Web 健康",guest,"/api/system/health",200);
        get("真实 MySQL 就绪",guest,"/api/system/readiness",200);
        get("匿名读取分类树",guest,"/api/public/categories",200);
        get("匿名私有请求拒绝",guest,"/api/items/mine",401);
        get("未知 API JSON 404",guest,"/api/not-found",404);
        request("不支持方法 JSON 405",guest,"POST","/api/system/health",null,405,false);
        Session admin=login("admin"), student=login("editor"), reviewer=login("reviewer"), viewer=login("viewer");
        JsonNode identity=get("学生身份",student,"/api/auth/me",200);
        expect("学生不拥有 ADMIN",identity.path("roles").toString().contains("EDITOR") && !identity.path("roles").toString().contains("ADMIN"));
        String username="gmy_new_"+UUID.randomUUID().toString().substring(0,8);
        Map<String,Object> registration=Map.of("username",username,"displayName","验收新生","password",password,"roles",List.of("ADMIN"));
        JsonNode registered=write("真实注册忽略伪造角色",guest,"POST","/api/auth/register",registration,200);
        expect("新生只分配学生角色",registered.path("roles").toString().contains("EDITOR") && !registered.path("roles").toString().contains("ADMIN"));
        write("重复注册拒绝",guest,"POST","/api/auth/register",registration,409);
        Session other=new Session();
        write("新生真实登录",other,"POST","/api/auth/login",Map.of("username",username,"password",password),200);
        other.token=get("新生令牌",other,"/api/auth/csrf",200).asText();
        get("学生不能管理用户",student,"/api/users",403);
        get("只读不能查看本人内容",viewer,"/api/items/mine",403);
        get("管理员用户分页",admin,"/api/users?page=1&size=5",200);
        get("管理员权限清单",admin,"/api/permissions",200);
        long category=write("管理员新建分类",admin,"POST","/api/categories",Map.of("name","GMY_R3_分类","sortOrder",0),200).path("id").asLong();
        write("学生不能新增分类",student,"POST","/api/categories",Map.of("name","无权限分类"),403);
        write("分类重名拒绝",admin,"POST","/api/categories",Map.of("name","GMY_R3_分类"),409);
        get("分类管理树",admin,"/api/categories/tree",200);
        Map<String,Object> body=draft(category);
        request("草稿缺 CSRF 拒绝",student,"POST","/api/items",body,403,false);
        long id=write("学生创建本人草稿",student,"POST","/api/items",body,200).path("id").asLong();
        get("他人草稿隐藏",other,"/api/items/"+id+"/mine",404);
        get("本人列表",student,"/api/items/mine?page=1&size=10",200);
        get("分页超限拒绝",student,"/api/items/mine?size=101",400);
        write("草稿修改",student,"PUT","/api/items/"+id,edit(body,0),200);
        write("旧版本修改拒绝",student,"PUT","/api/items/"+id,edit(body,0),409);
        write("真实物品引用拒绝删分类",admin,"DELETE","/api/categories/"+category,null,409);
        write("学生提审",student,"POST","/api/items/"+id+"/submit",Map.of("version",1),200);
        write("待审不能直接编辑",student,"PUT","/api/items/"+id,edit(body,2),409);
        get("审核员新队列",reviewer,"/api/reviews/pending",200);
        get("审核员原工作台队列",reviewer,"/api/reviews",200);
        write("学生不能审核",student,"POST","/api/reviews/"+id+"/approve",Map.of("version",2),403);
        write("驳回必须有意见",reviewer,"POST","/api/reviews/"+id+"/reject",Map.of("version",2),400);
        write("审核员驳回",reviewer,"POST","/api/reviews/"+id+"/reject",Map.of("version",2,"comment","请补充地点"),200);
        expect("作者读取真实意见",get("作者查看意见",student,"/api/items/"+id+"/reviews",200).size()==1);
        write("驳回后修改",student,"PUT","/api/items/"+id,edit(body,3),200);
        write("修改后重新提审",student,"POST","/api/items/"+id+"/submit",Map.of("version",4),200);
        request("旧审核路由缺令牌拒绝",reviewer,"POST","/api/reviews/"+id+"/decision",Map.of("version",5,"decision","APPROVED"),403,false);
        write("旧审核路由批准",reviewer,"POST","/api/reviews/"+id+"/decision",Map.of("version",5,"decision","APPROVED"),200);
        expect("状态为批准而非发布",get("批准后作者详情",student,"/api/items/"+id+"/mine",200).path("status").asText().equals("APPROVED"));
        expect("两次审核记录保留",get("审核历史一致",student,"/api/items/"+id+"/reviews",200).size()==2);
        get("历史查询",reviewer,"/api/reviews/history?articleId="+id,200);
        get("管理员查询业务审计",admin,"/api/operation-logs?page=1&size=10",200);
        get("学生无审计权限",student,"/api/operation-logs",403);
        long withdrawn=write("撤回场景草稿",student,"POST","/api/items",body,200).path("id").asLong();
        write("撤回场景提审",student,"POST","/api/items/"+withdrawn+"/submit",Map.of("version",0),200);
        write("撤回成功",student,"POST","/api/items/"+withdrawn+"/withdraw",Map.of("version",1),200);
        expect("撤回回到草稿",get("撤回后详情",student,"/api/items/"+withdrawn+"/mine",200).path("status").asText().equals("DRAFT"));
        // 只在夹具库经真实管理 API 改角色，验证自审回避与撤权立即生效。
        long reviewerId=get("审核员身份",reviewer,"/api/auth/me",200).path("id").asLong();
        write("为审核员加入学生测试角色",admin,"PUT","/api/users/"+reviewerId+"/roles",Map.of("roleCodes",List.of("REVIEWER","EDITOR")),200);
        long self=write("审核员本人草稿",reviewer,"POST","/api/items",body,200).path("id").asLong();
        write("审核员本人提审",reviewer,"POST","/api/items/"+self+"/submit",Map.of("version",0),200);
        write("审核员不能自审",reviewer,"POST","/api/reviews/"+self+"/approve",Map.of("version",1),403);
        JsonNode roles=get("读取角色授权",admin,"/api/roles",200); JsonNode reviewRole=null;
        for (JsonNode role:roles) if (role.path("code").asText().equals("REVIEWER")) reviewRole=role;
        if (reviewRole==null) throw new AssertionError("审核角色不存在");
        List<String> permissions=new ArrayList<>(); for (JsonNode permission:reviewRole.path("permissions")) permissions.add(permission.asText());
        List<String> readonly=new ArrayList<>(permissions); readonly.remove("review:decide");
        long roleId=reviewRole.path("id").asLong();
        write("撤销审核决定权限",admin,"PUT","/api/roles/"+roleId+"/permissions",Map.of("permissionCodes",readonly),200);
        write("旧会话撤权立即拒绝",reviewer,"POST","/api/reviews/"+withdrawn+"/approve",Map.of("version",2),403);
        write("恢复隔离角色授权",admin,"PUT","/api/roles/"+roleId+"/permissions",Map.of("permissionCodes",permissions),200);
        long otherId=registered.path("id").asLong();
        write("管理员停用测试学生",admin,"PUT","/api/users/"+otherId+"/status",Map.of("enabled",false),200);
        get("停用使旧会话失效",other,"/api/auth/me",401);
        write("退出登录",student,"POST","/api/auth/logout",null,200);
        get("退出后旧会话失效",student,"/api/auth/me",401);
        HttpResponse<String> page=guest.client.send(HttpRequest.newBuilder(URI.create(base+"/pages/index.html")).GET().build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        expect("正式 WAR 首页静态资源可用",page.statusCode()==200 && page.body().contains("校园失物招领") && page.body().contains("home-title"));
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=2) throw new IllegalArgumentException("Usage: baseUrl runDirectory");
        Path run=GmyR3Fixture.validateRunDirectory(Path.of(""),Path.of(args[1]));
        JsonNode fixture=JSON.readTree(run.resolve("fixture.json").toFile());
        GmyR3HttpSuite suite=new GmyR3HttpSuite(args[0],fixture.path("password").asText());
        Map<String,Object> report=new LinkedHashMap<>(); report.put("baseUrl",args[0]);
        report.put("schema",fixture.path("schema").asText()); report.put("startedAt",Instant.now().toString());
        try { suite.run(); report.put("passed",true); }
        catch (Exception | AssertionError failure) { report.put("passed",false); report.put("failure",failure instanceof AssertionError?failure.getMessage():failure.getClass().getSimpleName()); throw failure; }
        finally {
            report.put("completedAt",Instant.now().toString()); report.put("checks",suite.checks); report.put("passedChecks",suite.checks.size());
            JSON.writerWithDefaultPrettyPrinter().writeValue(run.resolve("http-results.json").toFile(),report);
            System.out.println("GMY R3 HTTP checks completed: "+suite.checks.size()+", passed="+report.get("passed"));
        }
    }
}
