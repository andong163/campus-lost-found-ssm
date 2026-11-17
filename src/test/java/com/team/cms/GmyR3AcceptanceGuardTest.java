package com.team.cms;

import com.team.cms.tools.GmyR3Fixture;
import com.team.cms.tools.GmyR3HttpSuite;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** 验收工具不得把业务库或远程站点当成可写夹具。 */
public class GmyR3AcceptanceGuardTest {
    @Test public void businessAndInjectedSchemaNamesAreRejected() {
        for (String name:new String[]{"cms_content","mysql","cms_gmy_r3_123456789abc;DROP DATABASE cms_content","cms_gmy_r3_1234",null}) {
            try { GmyR3Fixture.validateSchema(name); fail("must reject non-owned schema"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void remoteBasesAndCredentialUrlsAreRejected() {
        for (String url:new String[]{"https://example.com/cms","http://127.0.0.1:8095/","http://user@localhost:8095/cms","http://localhost:8095/cms?x=1"}) {
            try { GmyR3HttpSuite.validateBase(url); fail("must reject unsafe destination"); }
            catch (IllegalArgumentException expected) { }
        }
    }
    @Test public void escapedRunDirectoriesAreRejected() throws Exception {
        for (String dir:new String[]{".local/../gmy-r3-123456789abc",".local/gmy-r3-123456789abc/../other","src/main/resources"}) {
            try { GmyR3Fixture.validateRunDirectory(Path.of(""),Path.of(dir)); fail("must reject unsafe path"); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
