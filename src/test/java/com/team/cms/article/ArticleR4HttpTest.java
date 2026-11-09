package com.team.cms.article;

import com.fasterxml.jackson.databind.*;
import com.team.cms.article.dto.CreateDraftRequest;
import com.team.cms.article.entity.Article;
import com.team.cms.article.mapper.ArticleMapper;
import com.team.cms.auth.AuthConstants;
import com.team.cms.auth.security.CurrentUser;
import com.team.cms.category.entity.Category;
import com.team.cms.category.mapper.CategoryMapper;
import com.team.cms.common.domain.*;
import com.team.cms.config.*;
import java.time.LocalDateTime;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.*;
import org.springframework.test.context.*;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** 独立 H2、真实注册登录与即时权限重载；测试数据和约束均不接触业务库。 */
@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextHierarchy({@ContextConfiguration(name="root",classes=SpringConfig.class),
    @ContextConfiguration(name="mvc",classes=SpringMvcConfig.class)})
@TestPropertySource(properties={"jdbc.driver=org.h2.Driver",
    "jdbc.url=jdbc:h2:mem:article_r4;MODE=MySQL;DB_CLOSE_DELAY=-1","jdbc.username=sa","jdbc.password="})
@Sql({"/auth-schema-test.sql","/article-schema-test.sql","/schema-category.sql"})
public class ArticleR4HttpTest {
    @Autowired WebApplicationContext context;
    @Autowired DataSource dataSource;
    @Autowired ArticleMapper articles;
    @Autowired CategoryMapper categories;
    MockMvc mvc; JdbcTemplate jdbc; ObjectMapper json=new ObjectMapper();
    MockHttpSession owner,student,reviewer,other; long category;
    @Before public void setup() throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).build(); jdbc=new JdbcTemplate(dataSource);
        jdbc.update("DELETE FROM sys_operation_log");
        // 复用生产唯一约束的 H2 等价物，未修改已合并夹具。
        jdbc.execute("ALTER TABLE cms_claim_request ADD COLUMN IF NOT EXISTS active_applicant_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','APPROVED') THEN applicant_id ELSE NULL END)");
        jdbc.execute("ALTER TABLE cms_claim_request ADD COLUMN IF NOT EXISTS approved_article_id BIGINT GENERATED ALWAYS AS (CASE WHEN status='APPROVED' THEN article_id ELSE NULL END)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_r4_active ON cms_claim_request(article_id,active_applicant_id)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_r4_approved ON cms_claim_request(approved_article_id)");
        try(var conn=dataSource.getConnection()) {
            var script=new EncodedResource(new FileSystemResource("sql/migrations/006-yyx-r4-contact-claim-permissions.sql"),"UTF-8");
            ScriptUtils.executeSqlScript(conn,script); ScriptUtils.executeSqlScript(conn,script);
        }
        owner=login("r4owner"); student=login("r4student"); reviewer=login("r4reviewer"); other=login("r4other");
        jdbc.update("DELETE FROM sys_user_role WHERE user_id=?",uid(reviewer));
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE code='REVIEWER'",uid(reviewer));
        Category c=new Category();c.setName("第四轮分类");c.setEnabled(1);c.setSortOrder(0);categories.insert(c);category=c.getId();
    }
    MockHttpSession login(String name) throws Exception {
        String body=json.writeValueAsString(Map.of("username",name,"displayName","隔离测试","password","password123"));
        assertEquals(200,mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus());
        MvcResult result=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertEquals(200,result.getResponse().getStatus());return (MockHttpSession)result.getRequest().getSession(false);
    }
    long uid(MockHttpSession session){return ((CurrentUser)session.getAttribute(AuthConstants.SESSION_USER)).getId();}
    long item(ItemType type) {
        CreateDraftRequest r=new CreateDraftRequest();r.setTitle("第四轮 <img src=x onerror=alert(1)>");
        r.setContent("物品文字说明，页面必须显示为纯文本。<script>alert(1)</script>");r.setItemType(type);
        r.setCategoryId(category);r.setEventTime(LocalDateTime.now().minusHours(1));r.setLocation("图书馆 <b>一层</b>");r.setContactInfo("PRIVATE_CONTACT_MARKER");
        Article a=r.toNewArticle(uid(owner));a.setStatus(ArticleStatus.PUBLISHED);a.setPublishedAt(LocalDateTime.now());articles.insert(a);return a.getId();
    }
    MockHttpServletResponse read(String path,MockHttpSession session) throws Exception {
        var request=get(path);if(session!=null)request.session(session);return mvc.perform(request).andReturn().getResponse();
    }
    JsonNode data(MockHttpServletResponse response) throws Exception {return json.readTree(response.getContentAsByteArray()).get("data");}
    MockHttpServletResponse write(String path,Object body,MockHttpSession session,boolean csrf) throws Exception {
        var req=post(path).session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if(csrf)req.header(AuthConstants.CSRF_HEADER,data(read("/api/auth/csrf",session)).asText());
        return mvc.perform(req).andReturn().getResponse();
    }
    Map<String,Object> application(){return Map.of("claimDescription","PRIVATE_EVIDENCE_MARKER 私有核实证据","contactInfo","PRIVATE_APPLICANT_CONTACT");}
    long apply(long id,MockHttpSession who) throws Exception {
        var response=write("/api/items/"+id+"/claims",application(),who,true);assertEquals(200,response.getStatus());return data(response).get("id").asLong();
    }
    void approve(long claim) throws Exception {assertEquals(200,write("/api/claims/"+claim+"/decision",Map.of("version",0,"decision","APPROVED","comment","核实特征吻合"),reviewer,true).getStatus());}

    @Test public void lostContactIsSingleReadScopedAuditedAndNeverCached() throws Exception {
        long id=item(ItemType.LOST);String path="/api/items/"+id+"/contact";
        var summary=read("/api/items/"+id+"/r4-detail",owner);assertEquals(200,summary.getStatus());
        assertFalse(summary.getContentAsString().contains("contactInfo"));
        assertEquals(404,read("/api/items/"+id+"/r4-detail",student).getStatus());
        var anonymous=read(path,null);assertEquals(401,anonymous.getStatus());assertTrue(anonymous.getHeader("Cache-Control").contains("no-store"));
        var allowed=read(path,student);assertEquals(200,allowed.getStatus());assertEquals("PRIVATE_CONTACT_MARKER",data(allowed).get("contactInfo").asText());
        assertTrue(allowed.getHeader("Cache-Control").contains("no-store"));
        jdbc.update("UPDATE cms_article SET resolution_status='RESOLVED',resolved_at=CURRENT_TIMESTAMP WHERE id=?",id);
        assertEquals(403,read(path,student).getStatus());assertEquals(200,read(path,owner).getStatus());
        jdbc.update("UPDATE cms_article SET status='OFFLINE' WHERE id=?",id);assertEquals(404,read(path,student).getStatus());
        assertEquals(5,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE action='CONTACT_READ' AND target_id=?",Integer.class,id).intValue());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sys_operation_log WHERE action='CONTACT_READ' AND result='SUCCESS'",Integer.class).intValue());
        String audit=json.writeValueAsString(jdbc.queryForList("SELECT * FROM sys_operation_log"));
        assertFalse(audit.contains("PRIVATE_CONTACT"));assertFalse(audit.contains("PRIVATE_EVIDENCE"));
    }
    @Test public void foundContactUsesPolicyAndCurrentPermissions() throws Exception {
        long id=item(ItemType.FOUND);String path="/api/items/"+id+"/contact";
        assertEquals(403,read(path,student).getStatus());assertEquals(200,read(path,owner).getStatus());assertEquals(200,read(path,reviewer).getStatus());
        long claim=apply(id,student);assertEquals(403,read(path,student).getStatus());approve(claim);
        assertEquals(200,read(path,student).getStatus());assertEquals(403,read(path,other).getStatus());
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=(SELECT id FROM sys_role WHERE code='REVIEWER')");
        assertEquals(403,read(path,reviewer).getStatus());
        assertEquals(200,write("/api/items/"+id+"/resolve",Map.of("version",0,"claimId",claim),owner,true).getStatus());
        assertEquals(200,read(path,student).getStatus()); // completed claimant retains authorized history
        assertEquals("FOUND_HANDOVER",data(read("/api/public/items/"+id+"/resolution",null)).get("result").asText());
    }
    @Test public void lostVisibilityAndPermissionChangesApplyImmediately() throws Exception {
        long id=item(ItemType.LOST);String path="/api/items/"+id+"/contact";
        jdbc.update("UPDATE cms_category SET enabled=0 WHERE id=?",category);assertEquals(404,read(path,student).getStatus());
        jdbc.update("UPDATE cms_category SET enabled=1 WHERE id=?",category);
        jdbc.update("DELETE FROM sys_role_permission WHERE permission_id=(SELECT id FROM sys_permission WHERE code='item:contact:lost')");
        assertEquals(403,read(path,student).getStatus());assertEquals(200,read(path,owner).getStatus());
        jdbc.update("UPDATE sys_user SET enabled=0 WHERE id=?",uid(student));assertEquals(401,read(path,student).getStatus());
    }
    @Test public void publicBoundariesAndResolvedHistoricalVisibility() throws Exception {
        long id=item(ItemType.FOUND);long claim=apply(id,student);approve(claim);
        JsonNode open=data(read("/api/public/items/"+id+"/resolution",null));assertEquals("OPEN",open.get("result").asText());assertTrue(open.get("resolvedAt").isNull());
        assertEquals(200,write("/api/items/"+id+"/resolve",Map.of("version",0,"claimId",claim),owner,true).getStatus());
        for(String path:new String[]{"/api/public/items","/api/public/items/"+id,"/api/public/items/"+id+"/resolution"}) {
            var response=read(path,null);assertEquals(200,response.getStatus());String body=response.getContentAsString();
            for(String privateField:new String[]{"contactInfo","claimDescription","applicantId","reviewComment","PRIVATE_CONTACT","PRIVATE_EVIDENCE"})assertFalse(path+":"+privateField,body.contains(privateField));
        }
        var result=data(read("/api/public/items/"+id+"/resolution",null));assertFalse(result.get("resolvedAt").isNull());
        assertEquals(409,write("/api/items/"+id+"/claims",application(),other,true).getStatus());
        assertEquals("RESOLVED",data(read("/api/items/"+id+"/claim-eligibility",other)).get("reason").asText());
        assertEquals(409,write("/api/items/"+id+"/resolve",Map.of("version",1,"claimId",claim),owner,true).getStatus());
        jdbc.update("UPDATE cms_article SET status='OFFLINE' WHERE id=?",id);assertEquals(404,read("/api/public/items/"+id+"/resolution",null).getStatus());
        assertEquals(200,read("/api/items/"+id+"/resolution",owner).getStatus());
    }
    @Test public void claimWritesRejectCsrfOwnItemDuplicatesAndStaleVersion() throws Exception {
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.code IN ('ADMIN','REVIEWER','VIEWER') AND p.code IN ('claim:create','claim:view:own','claim:cancel:own','claim:handover:own')",Integer.class).intValue());
        long id=item(ItemType.FOUND);
        assertEquals(403,write("/api/items/"+id+"/claims",application(),student,false).getStatus());
        assertEquals(409,write("/api/items/"+id+"/claims",application(),owner,true).getStatus());
        long claim=apply(id,student);assertEquals(409,write("/api/items/"+id+"/claims",application(),student,true).getStatus());
        assertEquals(409,write("/api/claims/"+claim+"/decision",Map.of("version",3,"decision","APPROVED"),reviewer,true).getStatus());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM cms_claim_request WHERE id=?",String.class,claim));
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=(SELECT id FROM sys_role WHERE code='EDITOR') AND permission_id=(SELECT id FROM sys_permission WHERE code='claim:create')");
        assertEquals(403,write("/api/items/"+id+"/claims",application(),other,true).getStatus());
    }
    @Test public void resolveChecksOwnershipVersionAndRollsBackPartialHandover() throws Exception {
        long id=item(ItemType.FOUND);long claim=apply(id,student);long pending=apply(id,other);approve(claim);
        assertEquals(403,write("/api/items/"+id+"/resolve",Map.of("version",0,"claimId",claim),other,true).getStatus());
        assertEquals(409,write("/api/items/"+id+"/resolve",Map.of("version",9,"claimId",claim),owner,true).getStatus());
        jdbc.execute("ALTER TABLE cms_article ADD CONSTRAINT r4_fail_resolve CHECK (resolution_status<>'RESOLVED')");
        try {assertEquals(409,write("/api/items/"+id+"/resolve",Map.of("version",0,"claimId",claim),owner,true).getStatus());}
        finally {jdbc.execute("ALTER TABLE cms_article DROP CONSTRAINT r4_fail_resolve");}
        assertEquals("APPROVED",jdbc.queryForObject("SELECT status FROM cms_claim_request WHERE id=?",String.class,claim));
        assertEquals("OPEN",articles.selectById(id).getResolutionStatus().name());assertEquals(Integer.valueOf(0),articles.selectById(id).getVersion());
        assertEquals("PENDING",jdbc.queryForObject("SELECT status FROM cms_claim_request WHERE id=?",String.class,pending));
        assertEquals(200,write("/api/items/"+id+"/resolve",Map.of("version",0,"claimId",claim),owner,true).getStatus());
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM cms_claim_request WHERE id=?",String.class,claim));
        assertEquals("CANCELLED",jdbc.queryForObject("SELECT status FROM cms_claim_request WHERE id=?",String.class,pending));
    }
    @Test public void lostRecoveryUsesExistingWorkflowAndDoesNotAcceptClaim() throws Exception {
        long id=item(ItemType.LOST);
        assertEquals(403,write("/api/items/"+id+"/resolve",Map.of("version",0),student,true).getStatus());
        assertEquals(409,write("/api/items/"+id+"/resolve",Map.of("version",0,"claimId",1),owner,true).getStatus());
        assertEquals(200,write("/api/items/"+id+"/resolve",Map.of("version",0),owner,true).getStatus());
        assertEquals("LOST_RECOVERED",data(read("/api/public/items/"+id+"/resolution",null)).get("result").asText());
        assertEquals(409,write("/api/items/"+id+"/claims",application(),student,true).getStatus());
    }
}
