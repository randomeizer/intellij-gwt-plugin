package org.randombits.intellij.gwt.jps.build;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.junit.Assume;
import org.junit.runner.RunWith;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit38AssumeSupportRunner.class)
public class GwtJpsSdkSmokeTest extends LightPlatformTestCase {
  public void testRealCompilerIncrementalReportsCleanRebuildAndArtifactLifecycle() throws Exception {
    String repository = System.getenv("GWT_JPS_SMOKE_REPOSITORY");
    Assume.assumeTrue("Set GWT_JPS_SMOKE_REPOSITORY to a complete local GWT 2.13.1 Maven repository",
                      repository != null && !repository.isBlank());
    assertTrue(Files.isRegularFile(Path.of(repository, "org/gwtproject/gwt-dev/2.13.1/gwt-dev-2.13.1.jar")));
    Path directory = FileUtil.createTempDirectory("gwt-jps-smoke", "").toPath();
    Path log = directory.resolve("smoke.log");
    Process process = null;
    try {
      var classpath = new LinkedHashSet<>(ClasspathBootstrap.getBuildProcessApplicationClasspath());
      classpath.add(ClasspathBootstrap.getResourcePath(GwtBuilder.class));
      var command = new ArrayList<String>();
      command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
      ClasspathBootstrap.configureReflectionOpenPackages(command::add);
      command.addAll(java.util.List.of("-Djava.awt.headless=true", "-cp", String.join(File.pathSeparator, classpath),
        Path.of(System.getProperty("gwt.test.data.path"), "jps", "GwtJpsSmoke.java").toString(), repository, directory.toString()));
      process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
      Process child = process;
      PlatformTestUtil.waitWithEventsDispatching("Real JPS/GWT smoke timed out", () -> !child.isAlive(), 480);
      String output = output(log);
      assertEquals(output, 0, child.exitValue());
      assertTrue(output, output.contains("DONE"));
    }
    finally {
      if (process != null && process.isAlive()) {
        process.destroyForcibly();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS));
      }
      FileUtil.delete(directory.toFile());
    }
  }

  private static String output(Path log) throws Exception {
    try (var input = Files.newInputStream(log)) {
      return new String(input.readNBytes(6000), StandardCharsets.UTF_8);
    }
  }
}
