package com.team.cms.tools;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 对正式 WAR 验证当前交接实现；只读写专用 MySQL 夹具，不代替成员第四轮提交。 */
public final class GmyR4HttpSuite {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final String base, password;
    private final Path run;
    private final List<Map<String,Object>> checks = new ArrayList<>();
    private static final class Session {
        final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(5)).build();
        String token;
    }
    private GmyR4HttpSuite(String base, Path run) throws Exception {
        GmyR3HttpSuite.validateBase(base); this.base=base;
        this.run=GmyR3Fixture.validateRunDirectory(Path.of(""),run);
        try (Connection ignored=GmyR4Database.connect(this.run)) { }
        password=JSON.readTree(this.run.resolve("fixture.json").toFile()).path("password").asText();
    }
    private void expect(String name, boolean value) {
        checks.add(Map.of("name",name,"passed",value));
        if (!value) throw new AssertionError(name);
    }
    private JsonNode request(String name,Session session,String method,String path,Object body,int expected,boolean csrf) throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
            .header("Content-Type","application/json");
        if (csrf && session.token!=null) builder.header("X-CSRF-Token",session.token);
        builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
            HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body),StandardCharsets.UTF_8));
        HttpResponse<String> response=session.client.send(builder.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonNode value=JSON.readTree(response.body());
        boolean passed=response.statusCode()==expected && value.path("code").asInt()==expected && value.has("data") && value.has("message");
        checks.add(Map.of("name",name,"method",method,"path",path,"expected",expected,"actual",response.statusCode(),"passed",passed));
        if (!passed) throw new AssertionError(name+": expected "+expected+", got "+response.statusCode());
        if (expected>=400) expect(name+" 安全错误正文",!response.body().contains("java.sql.")
            && !response.body().contains("jdbc:mysql:") && !response.body().contains("R4 controlled rollback"));
        return value.get("data");
    }
    private JsonNode get(String name,Session s,String path,int status) throws Exception { return request(name,s,"GET",path,null,status,false); }
    private JsonNode post(String name,Session s,String path,Object body,int status) throws Exception { return request(name,s,"POST",path,body,status,true); }
    private Session login(String username) throws Exception {
        Session session=new Session();
        post("登录测试身份",session,"/api/auth/login",Map.of("username",username,"password",password),200);
        session.token=get("读取同会话令牌",session,"/api/auth/csrf",200).asText(); return session;
    }
    private Session register(String suffix) throws Exception {
        String name="gmyr4_"+suffix+"_"+UUID.randomUUID().toString().substring(0,8);
        post("注册独立学生",new Session(),"/api/auth/register",Map.of("username",name,"password",password,"displayName","第四轮测试学生"),200);
        return login(name);
    }
    private JsonNode publish(Session owner,Session reviewer,long category,String type) throws Exception {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("title","第四轮"+type+"交接验收"); body.put("content","用于隔离验收的物品说明，不含真实个人信息。");
        body.put("categoryId",category); body.put("itemType",type); body.put("location","隔离测试交接点");
        body.put("contactInfo","R4_TEST_OWNER_CONTACT"); body.put("eventTime",LocalDateTime.now().minusDays(1).withNano(0).toString());
        JsonNode item=post("创建待验收物品",owner,"/api/items",body,200); long id=item.path("id").asLong();
        item=post("提交审核",owner,"/api/items/"+id+"/submit",Map.of("version",item.path("version").asInt()),200);
        item=post("审核批准",reviewer,"/api/reviews/"+id+"/approve",Map.of("version",item.path("version").asInt()),200);
        post("授权发布",reviewer,"/api/items/"+id+"/publish",Map.of("version",item.path("version").asInt()),200);
        return get("发布后私有详情",owner,"/api/items/"+id+"/mine",200);
    }
    private JsonNode apply(Session student,long id) throws Exception {
        return post("申请认领",student,"/api/items/"+id+"/claims",
            Map.of("claimDescription","测试物品有可核对的唯一标记，仅用于隔离测试。","contactInfo","R4_TEST_CLAIM_CONTACT"),200);
    }
    private JsonNode claim(Session student,long id) throws Exception {
        for (JsonNode c:get("本人申请状态",student,"/api/claims/mine",200).path("records"))
            if (c.path("id").asLong()==id) return c;
        throw new AssertionError("申请状态记录不存在");
    }
    private void privateFieldsAbsent(JsonNode value) {
        expect("公开响应排除私密字段",!value.has("contactInfo") && !value.has("authorId")
            && !value.has("claimDescription") && !value.toString().contains("R4_TEST_OWNER_CONTACT"));
    }
    private void run() throws Exception {
        Session guest=new Session(),admin=login("gmyr3_admin"),owner=login("gmyr3_editor"),reviewer=login("gmyr3_reviewer"),viewer=login("gmyr3_viewer");
        Session alice=register("alice"),bob=register("bob");
        long root=post("新建父分类",admin,"/api/categories",Map.of("name","GMY_R4_"+UUID.randomUUID().toString().substring(0,8),"sortOrder",0),200).path("id").asLong();
        long category=post("新建子分类",admin,"/api/categories",Map.of("name","验收子类","parentId",root,"sortOrder",0),200).path("id").asLong();
        JsonNode found=publish(owner,reviewer,category,"FOUND"); long id=found.path("id").asLong(); int version=found.path("version").asInt();
        privateFieldsAbsent(get("公开招领详情",guest,"/api/public/items/"+id,200));
        JsonNode a=apply(alice,id), b=apply(bob,id); long aId=a.path("id").asLong(),bId=b.path("id").asLong();
        post("核实通过但尚未交接",reviewer,"/api/claims/"+aId+"/decision",Map.of("version",0,"decision","APPROVED"),200);
        expect("核实不等于交接",claim(alice,aId).path("status").asText().equals("APPROVED") && claim(alice,aId).path("completedAt").isNull());
        get("核实者读取联系方式",alice,"/api/items/"+id+"/found-contact",200);
        get("无关申请人不可读联系方式",bob,"/api/items/"+id+"/found-contact",403);
        Map<String,Object> resolve=Map.of("version",version,"claimId",aId);
        post("非发布者不能交接",bob,"/api/items/"+id+"/resolve",resolve,403);
        post("只读账号不能交接",viewer,"/api/items/"+id+"/resolve",resolve,403);
        post("错误物品版本拒绝",owner,"/api/items/"+id+"/resolve",Map.of("version",version+10,"claimId",aId),409);
        post("指定其他申请拒绝",owner,"/api/items/"+id+"/resolve",Map.of("version",version,"claimId",bId),409);
        request("无令牌不能交接",owner,"POST","/api/items/"+id+"/resolve",resolve,403,false);
        // 只在已验证归属的隔离库对一个测试物品注入数据库失败，检验真实事务回滚。
        try (Connection db=GmyR4Database.connect(run); Statement sql=db.createStatement()) {
            sql.execute("CREATE TRIGGER gmy_r4_fail_resolution BEFORE UPDATE ON cms_article FOR EACH ROW BEGIN IF NEW.id="+id+" AND NEW.resolution_status='RESOLVED' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='R4 controlled rollback'; END IF; END");
            try { post("交接第二步注入失败",owner,"/api/items/"+id+"/resolve",resolve,500); }
            finally { sql.execute("DROP TRIGGER gmy_r4_fail_resolution"); }
        }
        JsonNode unchanged=get("回滚后的物品",owner,"/api/items/"+id+"/mine",200);
        expect("物品状态时间版本全部回滚",unchanged.path("resolutionStatus").asText().equals("OPEN")
            && unchanged.path("resolvedAt").isNull() && unchanged.path("version").asInt()==version);
        expect("已通过申请未被提前完成",claim(alice,aId).path("status").asText().equals("APPROVED") && claim(alice,aId).path("completedAt").isNull());
        expect("其他待申请未被提前关闭",claim(bob,bId).path("status").asText().equals("PENDING"));
        post("发布者确认真实交接",owner,"/api/items/"+id+"/resolve",resolve,200);
        JsonNode completed=get("交接后物品",owner,"/api/items/"+id+"/mine",200);
        expect("物品结案写入时间和版本",completed.path("resolutionStatus").asText().equals("RESOLVED")
            && !completed.path("resolvedAt").isNull() && completed.path("version").asInt()==version+1);
        JsonNode completedClaim=claim(alice,aId);
        expect("申请完成并写入完成时间",completedClaim.path("status").asText().equals("COMPLETED") && !completedClaim.path("completedAt").isNull());
        expect("其他待申请同事务关闭",claim(bob,bId).path("status").asText().equals("CANCELLED"));
        post("重复交接拒绝",owner,"/api/items/"+id+"/resolve",Map.of("version",version+1,"claimId",aId),409);
        post("结案后禁止新认领",bob,"/api/items/"+id+"/claims",Map.of("claimDescription","结案后再次尝试，仅用于测试。","contactInfo","TEST"),409);
        privateFieldsAbsent(get("公开结案历史",guest,"/api/public/items/"+id,200));
        JsonNode lost=publish(owner,reviewer,category,"LOST"); long lostId=lost.path("id").asLong();
        post("他人不能确认寻物找回",alice,"/api/items/"+lostId+"/resolve",Map.of("version",lost.path("version").asInt()),403);
        post("寻物作者确认找回",owner,"/api/items/"+lostId+"/resolve",Map.of("version",lost.path("version").asInt()),200);
        JsonNode resolvedLost=get("寻物找回后详情",owner,"/api/items/"+lostId+"/mine",200);
        expect("寻物结案及时间落库",resolvedLost.path("resolutionStatus").asText().equals("RESOLVED") && !resolvedLost.path("resolvedAt").isNull());
        post("寻物重复找回拒绝",owner,"/api/items/"+lostId+"/resolve",Map.of("version",resolvedLost.path("version").asInt()),409);
        JsonNode hidden=publish(owner,reviewer,category,"FOUND"); long hiddenId=hidden.path("id").asLong(); JsonNode oldClaim=apply(alice,hiddenId);
        request("停用祖先分类",admin,"PUT","/api/categories/"+root+"/enabled",Map.of("enabled",false),200,true);
        get("停用后公开详情隐藏",guest,"/api/public/items/"+hiddenId,404);
        expect("停用后公开检索隐藏",get("停用子树查询",guest,"/api/public/items?categoryId="+root,200).path("total").asLong()==0);
        post("停用分类拒绝新申请",bob,"/api/items/"+hiddenId+"/claims",Map.of("claimDescription","分类停用后的新申请测试。","contactInfo","TEST"),409);
        post("停用后原申请仍可核实",reviewer,"/api/claims/"+oldClaim.path("id").asLong()+"/decision",Map.of("version",0,"decision","APPROVED"),200);
        post("原申请可继续交接",owner,"/api/items/"+hiddenId+"/resolve",Map.of("version",hidden.path("version").asInt(),"claimId",oldClaim.path("id").asLong()),200);
        JsonNode logs=get("授权查询交接审计",admin,"/api/operation-logs?action=CLAIM_RESOLVE&size=100",200);
        expect("交接审计含成功与失败",logs.path("records").toString().contains("SUCCESS") && logs.path("records").toString().contains("FAILURE"));
        expect("交接日志不含联系方式证据口令",!logs.toString().contains("R4_TEST_") && !logs.toString().contains(password) && !logs.toString().contains("claimDescription"));
        get("学生无权读取全站审计",alice,"/api/operation-logs",403);
    }
    public static void main(String[] args) throws Exception {
        if (args.length!=2) throw new IllegalArgumentException("Usage: localBaseUrl isolatedRunDirectory");
        GmyR4HttpSuite suite=new GmyR4HttpSuite(args[0],Path.of(args[1]));
        Map<String,Object> report=new LinkedHashMap<>(); report.put("task","GMY R4"); report.put("passed",false);
        report.put("startedAt",Instant.now().toString());
        try { suite.run(); report.put("passed",true); }
        catch (Exception | AssertionError error) { report.put("failure",error instanceof AssertionError?error.getMessage():error.getClass().getSimpleName()); throw error; }
        finally {
            report.put("checks",suite.checks); report.put("completedAt",Instant.now().toString());
            JSON.writerWithDefaultPrettyPrinter().writeValue(suite.run.resolve("r4-http-results.json").toFile(),report);
            System.out.println("GMY R4 HTTP checks: "+suite.checks.size()+", passed="+report.get("passed"));
        }
    }
}
