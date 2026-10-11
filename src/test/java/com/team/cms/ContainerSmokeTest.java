package com.team.cms;

import com.team.cms.config.WebAppInitializer;
import java.nio.file.Files;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.Test;
import org.springframework.web.SpringServletContainerInitializer;
import static org.junit.Assert.*;

/** Real Servlet container smoke test, no MySQL credentials or test business endpoints. */
public class ContainerSmokeTest {
    @Test public void initializerDeploysOnTomcat9AndRespondsOverHttp() throws Exception {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("jdbc.driver", "org.h2.Driver"); properties.put("jdbc.url", "jdbc:h2:mem:container;MODE=MySQL");
        properties.put("jdbc.username", "sa"); properties.put("jdbc.password", "");
        Map<String, String> previous = new HashMap<>();
        properties.forEach((key, value) -> { previous.put(key, System.getProperty(key)); System.setProperty(key, value); });
        Tomcat tomcat = new Tomcat();
        try {
            tomcat.setBaseDir(Files.createTempDirectory("cms-tomcat-test").toString());
            tomcat.setPort(0); tomcat.getConnector();
            Context app = tomcat.addContext("/cms", Files.createTempDirectory("cms-web-test").toString());
            app.addServletContainerInitializer(new SpringServletContainerInitializer(), Set.of(WebAppInitializer.class));
            tomcat.start();
            int port = tomcat.getConnector().getLocalPort();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/cms/api/system/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode()); assertTrue(response.body().contains("\"status\":\"UP\""));
            assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
        } finally {
            tomcat.stop(); tomcat.destroy();
            previous.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        }
    }
}
