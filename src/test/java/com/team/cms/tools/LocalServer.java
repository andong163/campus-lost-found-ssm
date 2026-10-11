package com.team.cms.tools;

import java.nio.file.*;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import java.net.URI;
import java.net.http.*;

/** Local Tomcat 9 runner serving the built WAR (production classes only). */
public final class LocalServer {
    public static void main(String[] args) throws Exception {
        Path war = Path.of("target/cms.war").toAbsolutePath();
        if (!Files.exists(war)) throw new IllegalStateException("Run Maven verify first");
        int port = Integer.parseInt(System.getProperty("cms.port", "8080"));
        Tomcat tomcat = new Tomcat();
        Path base = Path.of("target/local-tomcat").toAbsolutePath();
        Files.createDirectories(base.resolve("webapps"));
        tomcat.setBaseDir(base.toString());
        tomcat.setAddDefaultWebXmlToWebapp(false);
        tomcat.setPort(port);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        // Standard WAR scanning discovers WebAppInitializer from WEB-INF/classes.
        StandardContext app = (StandardContext) tomcat.addWebapp("/cms", war.toString());
        app.setUnpackWAR(false);
        app.setDefaultWebXml(tomcat.noDefaultWebXmlPath());
        ((StandardJarScanner) app.getJarScanner()).setScanClassPath(false);
        tomcat.start();
        if (!app.getState().isAvailable()) {
            tomcat.stop(); tomcat.destroy();
            throw new IllegalStateException("CMS WAR context failed to start");
        }
        if (Boolean.getBoolean("cms.smoke")) {
            try {
                HttpClient client = HttpClient.newHttpClient();
                String baseUrl = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/cms";
                HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/system/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
                HttpResponse<String> missing = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/not-found")).GET().build(), HttpResponse.BodyHandlers.ofString());
                if (health.statusCode() != 200 || missing.statusCode() != 404 || !missing.body().contains("\"code\":404")) {
                    throw new IllegalStateException("WAR smoke HTTP check failed");
                }
                System.out.println("WAR smoke passed: health=200, missing=404, isolated container classpath");
            } finally { tomcat.stop(); tomcat.destroy(); }
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { tomcat.stop(); tomcat.destroy(); } catch (Exception ignored) { }
        }));
        System.out.println("Local CMS foundation: http://127.0.0.1:" + port + "/cms/api/system/health");
        tomcat.getServer().await();
    }
}
