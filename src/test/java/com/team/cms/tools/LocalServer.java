package com.team.cms.tools;

import java.nio.file.*;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.apache.tomcat.util.scan.StandardJarScanFilter;
import java.net.URI;
import java.net.http.*;

/** Local Tomcat 9 runner serving the built WAR (production classes only). */
public final class LocalServer {
    public static void main(String[] args) throws Exception {
        long startupStart = System.nanoTime();
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
        // Let Tomcat expand the built WAR; repeated reads of nested compressed JARs are slow on Windows.
        app.setUnpackWAR(true);
        app.setDefaultWebXml(tomcat.noDefaultWebXmlPath());
        StandardJarScanner scanner = (StandardJarScanner) app.getJarScanner();
        scanner.setScanClassPath(false);
        // This runner uses Spring's SCI and no JSP/TLDs. Keep WEB-INF/classes discovery enabled.
        StandardJarScanFilter scanFilter = new StandardJarScanFilter();
        scanFilter.setDefaultPluggabilityScan(false);
        scanFilter.setPluggabilitySkip("");
        String extraScan = System.getProperty("cms.scan.extra", "").trim();
        scanFilter.setPluggabilityScan("spring-web-*.jar" + (extraScan.isEmpty() ? "" : "," + extraScan));
        scanFilter.setDefaultTldScan(false);
        scanFilter.setTldScan("");
        scanner.setJarScanFilter(scanFilter);
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
                System.out.println("WAR smoke passed: health=200, missing=404, isolated container classpath, startupMs=" +
                    (System.nanoTime() - startupStart) / 1_000_000);
            } finally { tomcat.stop(); tomcat.destroy(); }
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { tomcat.stop(); tomcat.destroy(); } catch (Exception ignored) { }
        }));
        System.out.println("Campus lost-and-found foundation: http://127.0.0.1:" + port + "/cms/api/system/health");
        tomcat.getServer().await();
    }
}
