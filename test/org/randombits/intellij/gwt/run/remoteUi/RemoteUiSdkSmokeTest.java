package org.randombits.intellij.gwt.run.remoteUi;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Assume;
import org.randombits.intellij.gwt.run.GwtRunConfiguration;
import org.randombits.intellij.gwt.run.GwtRunConfigurationType;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class RemoteUiSdkSmokeTest extends LightJavaCodeInsightFixtureTestCase {
  public void testRealSdkHandshakeLogsSessionsRestartAndShutdown() throws Exception {
    String jars = System.getenv("GWT_REMOTE_UI_SMOKE_JARS");
    Assume.assumeTrue("Set GWT_REMOTE_UI_SMOKE_JARS to real gwt-dev JARs, separated by the platform path separator",
                      jars != null && !jars.isBlank());
    for (String jar : jars.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
      smoke(Path.of(jar));
    }
  }

  private void smoke(Path sdkJar) throws Exception {
    assertTrue("Missing SDK JAR: " + sdkJar, Files.isRegularFile(sdkJar));
    var configuration = (GwtRunConfiguration)GwtRunConfigurationType.getFactory().createTemplateConfiguration(getProject());
    var connection = new RemoteUiConnection(configuration, GwtVersionImpl.VERSION_2_12, false, false, null);
    var disposable = Disposer.newDisposable();
    var console = TextConsoleBuilderFactory.getInstance().createBuilder(getProject()).getConsole();
    var view = new RemoteUiView(getProject(), console, connection, false);
    view.getComponent();
    Disposer.register(disposable, view);
    Disposer.register(disposable, connection.getConnection());
    Path log = Files.createTempFile("gwt-remote-ui-sdk-", ".log");
    Process child = null;
    try {
      int port = connection.open();
      Path source = Path.of(System.getProperty("gwt.test.data.path"), "remoteUi", "RemoteUiSmokeClient.java");
      child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", sdkJar.toString(), source.toString(), Integer.toString(port))
        .redirectErrorStream(true).redirectOutput(log.toFile()).start();
      Process process = child;
      awaitMarker(process, log, "READY");
      var moduleConsole = (ConsoleViewImpl)view.getModuleConsole("example.Smoke");
      PlatformTestUtil.waitWithEventsDispatching("SDK module logs missing: " + output(log), () ->
        moduleConsole.getText().contains("  ERROR: SDK smoke error\n") &&
        moduleConsole.getText().contains("INFO: SDK smoke reconnected\n"), 10);
      String text = moduleConsole.getText();
      assertTrue(text.indexOf("SDK smoke error") < text.indexOf("SDK smoke reconnected"));
      assertEquals(2, text.split("Loading module: example.Smoke", -1).length - 1);
      PlatformTestUtil.waitWithEventsDispatching("Startup URL missing", () ->
        ((ConsoleViewImpl)view.getMainConsole()).getText().contains("http://localhost:8888/smoke"), 10);
      connection.sendRestartServerRequest();
      awaitMarker(process, log, "RESTARTED");
      connection.close();
      awaitMarker(process, log, "DONE");
      PlatformTestUtil.waitWithEventsDispatching("SDK did not terminate", () -> !process.isAlive(), 10);
      assertEquals(output(log), 0, process.exitValue());
      assertTrue(connection.getConnection().awaitTermination(5, TimeUnit.SECONDS));
    }
    finally {
      Disposer.dispose(disposable);
      if (child != null && child.isAlive()) {
        child.destroyForcibly();
        assertTrue("SDK child did not terminate", child.waitFor(5, TimeUnit.SECONDS));
      }
      Files.deleteIfExists(log);
    }
  }

  private static void awaitMarker(Process child, Path log, String marker) {
    PlatformTestUtil.waitWithEventsDispatching("SDK did not reach " + marker, () -> {
      String output = output(log);
      if (output.contains(marker + "\n")) return true;
      if (!child.isAlive()) throw new AssertionError("SDK exited before " + marker + ": " + output);
      return false;
    }, 20);
  }

  private static String output(Path log) {
    try (var input = Files.newInputStream(log)) {
      return new String(input.readNBytes(6000), StandardCharsets.UTF_8);
    }
    catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }
}
