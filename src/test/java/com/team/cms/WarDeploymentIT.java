package com.team.cms;

import com.team.cms.tools.LocalServer;
import java.nio.file.*;
import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import org.apache.catalina.startup.Tomcat;
import org.junit.Test;
import static org.junit.Assert.*;

/** Run after packaging: catches missing container dependencies hidden by the test classpath. */
public class WarDeploymentIT {
    @Test public void builtWarRunsWithOnlyTomcatAndAnnotationsInParentClasspath() throws Exception {
        Path tools = Path.of("target/war-smoke-tools");
        Path destination = tools.resolve("com/team/cms/tools/LocalServer.class");
        Files.createDirectories(destination.getParent());
        Files.copy(Path.of("target/test-classes/com/team/cms/tools/LocalServer.class"), destination, StandardCopyOption.REPLACE_EXISTING);
        String classpath = tools.toAbsolutePath() + File.pathSeparator +
            Path.of(Tomcat.class.getProtectionDomain().getCodeSource().getLocation().toURI()) + File.pathSeparator +
            Path.of(PostConstruct.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path output = Path.of("target/war-smoke.log");
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Dfile.encoding=UTF-8", "-Dcms.port=0", "-Dcms.smoke=true", "-Djdbc.username=smoke-only", "-Djdbc.password=not-a-real-secret",
            "-cp", classpath, LocalServer.class.getName()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        try {
            assertTrue("WAR process timeout; see target/war-smoke.log", process.waitFor(90, TimeUnit.SECONDS));
            assertEquals("WAR deployment failed; see target/war-smoke.log", 0, process.exitValue());
            assertTrue(Files.readString(output).contains("WAR smoke passed"));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
