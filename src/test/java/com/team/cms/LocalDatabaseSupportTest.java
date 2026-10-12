package com.team.cms;

import com.team.cms.tools.LocalDatabaseSupport;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalDatabaseSupportTest {
    private Properties config(String url) {
        Properties p = new Properties(); p.setProperty("jdbc.url", url);
        p.setProperty("jdbc.username", "test-user"); p.setProperty("jdbc.password", "test-password"); return p;
    }
    @Test public void loopbackAndEnvironmentConfigurationAreAccepted() {
        assertNotNull(LocalDatabaseSupport.from(config("jdbc:mysql://localhost:3307/cms_content"), Map.of()));
        assertNotNull(LocalDatabaseSupport.from(new Properties(), Map.of("CMS_DB_USERNAME", "test-user", "CMS_DB_PASSWORD", "test-password")));
    }
    @Test public void remoteOrUnexpectedSchemasAreRejectedWithoutPrintingTheUrl() {
        for (String url : new String[]{"jdbc:mysql://example.org/cms_content", "jdbc:mysql://127.0.0.1/other_db",
                "jdbc:mysql://127.0.0.1/cms_content?password=test-password#fragment", "jdbc:h2:mem:test"}) {
            try { LocalDatabaseSupport.from(config(url), Map.of()); fail("Expected target rejection"); }
            catch (IllegalStateException expected) {
                assertFalse(expected.getMessage().contains(url));
                assertFalse(expected.getMessage().contains("test-password"));
            }
        }
    }
    @Test public void placeholdersAndMissingCredentialsAreRejected() {
        Properties p = config("jdbc:mysql://localhost/cms_content");
        p.setProperty("jdbc.username", "YOUR_LOCAL_DATABASE_USER");
        assertThrows(IllegalStateException.class, () -> LocalDatabaseSupport.from(p, Map.of()));
        assertThrows(IllegalStateException.class, () -> LocalDatabaseSupport.from(new Properties(), Map.of()));
    }
}
