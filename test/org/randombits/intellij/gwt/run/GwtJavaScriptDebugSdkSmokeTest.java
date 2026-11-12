package org.randombits.intellij.gwt.run;

import com.google.gson.Gson;
import com.intellij.debugger.ui.breakpoints.JavaLineBreakpointType;
import com.intellij.execution.RunManager;
import com.intellij.ide.browsers.BrowserFamily;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.ide.browsers.chrome.ChromeSettings;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.randombits.intellij.gwt.GwtTestCase;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Real native debugger and isolated headless Chrome; map data is a synthetic GWT-format fixture. */
@RunWith(JUnit38AssumeSupportRunner.class)
public class GwtJavaScriptDebugSdkSmokeTest extends JavaCodeInsightFixtureTestCase {
  public void testNativeChromeStopsAtJavaSourceMapBreakpoint() throws Exception {
    runBreakpoint(false, false);
  }

  public void testInheritedSuperSourceAndConfiguredCodeServerPort() throws Exception {
    runBreakpoint(true, false);
  }

  public void testUnknownExternalCodeServerUsesExplicitNativeMapping() throws Exception {
    runBreakpoint(false, true);
  }

  private void runBreakpoint(boolean superSource, boolean external) throws Exception {
    String executable = System.getenv("GWT_JS_DEBUG_SMOKE_BROWSER");
    Assume.assumeTrue("Set GWT_JS_DEBUG_SMOKE_BROWSER to a Chrome executable", executable != null && !executable.isBlank());
    assertTrue(Files.isExecutable(Path.of(executable)));
    GwtTestCase.addGwtFacet(getModule(), null);
    Path sourceDirectory = FileUtil.createTempDirectory("gwt-debug-sources", "").toPath();
    com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(getTestRootDisposable(), sourceDirectory.toString());
    String relativeSource = superSource ? "library/super/probe/Shared.java" : "smoke/client/Probe.java";
    String javaSource = superSource ? "package probe;\npublic class Shared {\n  public static void hit() {}\n}\n" :
      "package smoke.client;\npublic class Probe {\n  public static void hit() {}\n}\n";
    if (superSource) {
      write(sourceDirectory, "library/Library.gwt.xml", "<module><super-source path='super'/></module>");
      write(sourceDirectory, "smoke/App.gwt.xml", "<module rename-to='app'><inherits name='library.Library'/></module>");
    }
    else {
      write(sourceDirectory, "smoke/App.gwt.xml", "<module rename-to='app'><source path='client'/><entry-point class='smoke.client.Probe'/></module>");
    }
    write(sourceDirectory, relativeSource, javaSource);
    var root = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(sourceDirectory);
    assertNotNull(root);
    WriteAction.run(() -> {
      var model = com.intellij.openapi.roots.ModuleRootManager.getInstance(getModule()).getModifiableModel();
      model.addContentEntry(root).addSourceFolder(root, false);
      model.commit();
    });
    com.intellij.testFramework.VfsTestUtil.syncRefresh();
    var javaFile = root.findFileByRelativePath(relativeSource);
    assertNotNull(javaFile);
    String origin = external ? "http://127.0.0.2:44567" : superSource ? "http://127.0.0.2:9017" : "http://localhost:9876";
    String sourceUrl = origin + "/sourcemaps/app/" + relativeSource;
    String script = "function tick() {\n  globalThis.gwtDebuggerProbe = 42;\n}\nsetInterval(tick, 100);\n//# sourceMappingURL=app.js.map\n";
    String sourceMap = new Gson().toJson(Map.of("version", 3, "file", "app.js",
      "sources", List.of(sourceUrl),
      "sourcesContent", List.of(javaSource), "names", List.of(), "mappings", ";AAEA"));
    var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      String body = switch (path) {
        case "/" -> "<!doctype html><html><script src='/app.js'></script></html>";
        case "/app.js" -> script;
        case "/app.js.map" -> sourceMap;
        default -> "Not found";
      };
      exchange.getResponseHeaders().add("Content-Type", path.endsWith(".map") ? "application/json" :
        path.endsWith(".js") ? "application/javascript" : "text/html");
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(path.equals("/favicon.ico") ? 404 : 200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    Path profile = FileUtil.createTempDirectory("gwt-chrome-debug", "").toPath();
    var browserManager = WebBrowserManager.getInstance();
    var savedBrowsers = browserManager.getState().clone();
    Map<ProcessHandle, Optional<Instant>> owned = new LinkedHashMap<>();
    var debugger = XDebuggerManager.getInstance(getProject());
    var type = XDebuggerUtil.getInstance().findBreakpointType(JavaLineBreakpointType.class);
    assertNotNull(type);
    var breakpoint = WriteAction.compute(() -> debugger.getBreakpointManager().addLineBreakpoint(
      type, javaFile.getUrl(), 2, type.createBreakpointProperties(javaFile, 2)));
    XDebugSession[] started = {null};
    String url = "http://localhost:" + server.getAddress().getPort() + "/";
    try {
      var chrome = new ChromeSettings();
      chrome.setUseCustomProfile(true);
      chrome.setUserDataDirectoryPath(profile.toString());
      chrome.setCommandLineOptions("--headless=new --remote-debugging-port=0 --no-first-run --no-default-browser-check");
      var browser = browserManager.addBrowser(UUID.randomUUID(), BrowserFamily.CHROME, "GWT debugger smoke", executable, true, chrome);
      server.start();
      var runManager = RunManager.getInstance(getProject());
      var gwtSettings = runManager.createConfiguration("GWT debugger smoke", GwtRunConfigurationType.getFactory());
      var configuration = (GwtRunConfiguration)gwtSettings.getConfiguration();
      if (superSource) configuration.getGwtState().SHELL_PARAMETERS = "-codeServerPort 9017 -bindAddress 127.0.0.2 -port 8880";
      runManager.addConfiguration(gwtSettings);
      var aliases = new GwtFileUrlMapper().getUrls(javaFile, getProject(), "localhost:" + server.getAddress().getPort())
        .stream().map(com.intellij.util.Url::toExternalForm).toList();
      if (external) {
        assertFalse("Unknown authority must require the approved explicit mapping", aliases.contains(sourceUrl));
        var settings = GwtJavaScriptDebugSupportImpl.getOrCreateSettings(configuration, url, browser.getId().toString());
        var debug = (com.intellij.javascript.debugger.execution.JavaScriptDebugConfiguration)settings.getConfiguration();
        debug.setMappings(List.of(new com.intellij.javascript.debugger.execution.RemoteUrlMappingBean(
          root.getPath(), origin + "/sourcemaps/app")));
      }
      else assertContainsElements(aliases, sourceUrl);
      var support = GwtJavaScriptDebugSupport.getInstance();
      assertNotNull(support);
      assertTrue(support.start(configuration, url, browser));
      PlatformTestUtil.waitWithEventsDispatching("Native JavaScript debugger did not start", () -> {
        captureOwned(profile, owned);
        for (var session : debugger.getDebugSessions()) {
          if (session.getRunProfile() instanceof com.intellij.javascript.debugger.execution.JavaScriptDebugConfiguration js &&
              url.equals(js.getUri())) started[0] = session;
        }
        return started[0] != null;
      }, 60);
      PlatformTestUtil.waitWithEventsDispatching("Native Chrome did not hit the Java source-map breakpoint", () -> {
        captureOwned(profile, owned);
        return started[0].isSuspended();
      }, 60);
      var position = started[0].getCurrentPosition();
      assertNotNull(position);
      assertEquals(javaFile, position.getFile());
      assertEquals(2, position.getLine());
      PlatformTestUtil.waitWithEventsDispatching("Debugger source navigation did not finish", () ->
        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject()).isFileOpen(javaFile), 30);
      int sessions = debugger.getDebugSessions().length;
      assertTrue(support.start(configuration, url, browser));
      com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents();
      assertEquals("Repeated startup must reuse the active session", sessions, debugger.getDebugSessions().length);
    }
    finally {
      captureOwned(profile, owned);
      WriteAction.run(() -> debugger.getBreakpointManager().removeBreakpoint(breakpoint));
      for (var session : debugger.getDebugSessions()) {
        if (session.getRunProfile() instanceof com.intellij.javascript.debugger.execution.JavaScriptDebugConfiguration js &&
            url.equals(js.getUri())) {
          // The public session descriptor is a backend placeholder; close the actual frontend tab owner.
          var tab = ((com.intellij.xdebugger.impl.XDebugSessionImpl)session).getSessionTabIfInitialized();
          var descriptor = tab == null ? session.getRunContentDescriptor() : tab.getRunContentDescriptor();
          var backend = (com.intellij.openapi.Disposable)session.getDebugProcess();
          session.stop();
          com.intellij.execution.ui.RunContentManager.getInstance(getProject()).removeRunContent(
            com.intellij.execution.executors.DefaultDebugExecutor.getDebugExecutorInstance(), descriptor);
          if (!com.intellij.openapi.util.Disposer.isDisposed(descriptor)) com.intellij.openapi.util.Disposer.dispose(descriptor);
          if (!com.intellij.openapi.util.Disposer.isDisposed(backend)) com.intellij.openapi.util.Disposer.dispose(backend);
        }
      }
      for (var entry : owned.entrySet()) {
        var process = entry.getKey();
        if (process.isAlive() && entry.getValue().isPresent() && process.info().startInstant().equals(entry.getValue())) {
          process.destroy();
          try { process.onExit().get(5, TimeUnit.SECONDS); }
          catch (Exception ignored) { if (process.isAlive()) process.destroyForcibly(); }
        }
      }
      browserManager.loadState(savedBrowsers);
      var runManager = RunManager.getInstance(getProject());
      for (var settings : runManager.getAllSettings()) {
        if (settings.getName().startsWith("GWT debugger smoke")) runManager.removeConfiguration(settings);
      }
      server.stop(0);
      FileUtil.delete(profile.toFile());
      WriteAction.run(() -> {
        var model = com.intellij.openapi.roots.ModuleRootManager.getInstance(getModule()).getModifiableModel();
        for (var entry : model.getContentEntries()) if (entry.getUrl().equals(root.getUrl())) model.removeContentEntry(entry);
        model.commit();
      });
      FileUtil.delete(sourceDirectory.toFile());
      // A paused frame navigates asynchronously; drain that work before closing its source editors.
      com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents();
      var editors = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(getProject());
      for (var file : editors.getOpenFiles()) editors.closeFile(file);
    }
  }

  private static void write(Path root, String relative, String contents) throws Exception {
    Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, contents);
  }

  private static void captureOwned(Path profile, Map<ProcessHandle, Optional<Instant>> processes) {
    ProcessHandle.current().descendants().forEach(process -> {
      String[] arguments = process.info().arguments().orElse(new String[0]);
      for (int i = 0; i < arguments.length; i++) {
        if (arguments[i].equals("--user-data-dir=" + profile) ||
            arguments[i].equals("--user-data-dir") && i + 1 < arguments.length && arguments[i + 1].equals(profile.toString())) {
          processes.putIfAbsent(process, process.info().startInstant());
        }
      }
    });
  }
}
