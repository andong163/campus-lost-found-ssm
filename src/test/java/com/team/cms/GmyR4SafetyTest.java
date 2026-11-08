package com.team.cms;

import com.team.cms.tools.GmyR4Database;
import com.team.cms.tools.GmyR4Delivery;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class GmyR4SafetyTest {
    @Test public void upgradeProbesRejectBusinessAndRemoteDatabaseTargets() {
        String schema="cms_gmy_r3_0123456789ab";
        GmyR4Database.validateUrl("jdbc:mysql://127.0.0.1:3306/"+schema+"?characterEncoding=UTF-8",schema);
        for (String url:List.of("jdbc:mysql://127.0.0.1/cms_content","jdbc:mysql://remote.invalid/"+schema,
                "jdbc:mysql://root:secret@localhost/"+schema,"jdbc:mysql://localhost/another_fixture",
                "jdbc:mysql://localhost/"+schema+"#fragment")) {
            try { GmyR4Database.validateUrl(url,schema); fail("Unsafe target accepted"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void sourceArchiveRejectsPrivateFilesCachesAndTraversal() {
        for (String name:List.of("../secret","C:/secret","/root/secret","src/../secret",
                "src\\..\\secret","src/main/resources/jdbc-local.properties",".env.local",
                ".git/config",".local/fixture.json","target/classes/A.class","config/key.pem")) {
            try { GmyR4Delivery.validateEntry(name); fail("Private archive entry accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        GmyR4Delivery.validateEntry("config/jdbc-local.properties.example");
        GmyR4Delivery.validateEntry("src/main/resources/jdbc.properties");
        GmyR4Delivery.validateEntry("docs/diagrams/architecture.svg");
    }
}
