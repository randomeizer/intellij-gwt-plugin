package org.randombits.intellij.gwt.run;

import com.intellij.execution.RunManager;
import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.javascript.debugger.execution.JavaScriptDebugConfiguration;
import com.intellij.javascript.debugger.execution.JavascriptDebugConfigurationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GwtJavaScriptDebugSupportTest extends LightJavaCodeInsightFixtureTestCase {
  private GwtRunConfiguration configuration;
  private WebBrowser browser;
  private final List<String> opened = new ArrayList<>();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    configuration = new GwtRunConfiguration("Fixture", getProject(), GwtRunConfigurationType.getFactory());
    browser = WebBrowserManager.getInstance().getBrowsers().getFirst();
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), BrowserLauncher.class, new BrowserLauncher() {
      @Override public void open(String path) { fail("Expected selected-browser overload"); }
      @Override public void browse(Path path) { fail("Expected selected-browser overload"); }
      @Override public void browse(String url, WebBrowser selected, Project project) {
        assertSame(browser, selected);
        assertSame(getProject(), project);
        opened.add(url);
      }
    }, getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      var manager = RunManager.getInstance(getProject());
      for (var settings : manager.getConfigurationSettingsList(JavascriptDebugConfigurationType.getTypeInstance())) {
        if (settings.getName().startsWith("Fixture")) manager.removeConfiguration(settings);
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testPlainBrowserPreservesSelectionAndDoesNotStartDebugger() {
    replaceSupport(false, false);
    GwtJavaScriptDebugSupport.openBrowser(configuration, "http://localhost:8888/plain", browser, false);
    assertEquals(List.of("http://localhost:8888/plain"), opened);
  }

  public void testDebuggerOwnsLaunchWhenAvailable() {
    replaceSupport(true, true);
    assertTrue(GwtJavaScriptDebugSupport.isAvailable(getProject()));
    GwtJavaScriptDebugSupport.openBrowser(configuration, "http://localhost:8888/debug", browser, true);
    assertEmpty(opened);
  }

  public void testUnavailableRunnerFallsBackToSelectedBrowser() {
    replaceSupport(false, true);
    assertFalse(GwtJavaScriptDebugSupport.isAvailable(getProject()));
    GwtJavaScriptDebugSupport.openBrowser(configuration, "http://localhost:8888/debug", browser, true);
    assertEquals(List.of("http://localhost:8888/debug"), opened);
  }

  public void testRemoteUiOpensSelectedBrowserOnlyOnce() {
    replaceSupport(false, false);
    var connection = new org.randombits.intellij.gwt.run.remoteUi.RemoteUiConnection(configuration,
      org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_12, true, false, browser);
    try {
      connection.openBrowserOnce("http://localhost:8888/first");
      connection.openBrowserOnce("http://localhost:8888/duplicate");
      assertEquals(List.of("http://localhost:8888/first"), opened);
    }
    finally {
      connection.close();
    }
  }

  public void testDisabledBrowserDoesNotLaunch() {
    replaceSupport(false, false);
    var connection = new org.randombits.intellij.gwt.run.remoteUi.RemoteUiConnection(configuration,
      org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_12, false, true, browser);
    try {
      connection.openBrowserOnce("http://localhost:8888/disabled");
      assertEmpty(opened);
    }
    finally {
      connection.close();
    }
  }

  public void testSavedConfigurationIsReusedWithoutLosingCustomizations() {
    String id = browser.getId().toString();
    var first = GwtJavaScriptDebugSupportImpl.getOrCreateSettings(configuration, "http://localhost:8888/app", id);
    var debug = (JavaScriptDebugConfiguration)first.getConfiguration();
    assertEquals("http://localhost:8888/app", debug.getUri());
    assertEquals(id, debug.getEngineId());
    debug.setUseTemporaryUserDataDir(true);
    debug.setUseFirstLineBreakpoints(true);
    var second = GwtJavaScriptDebugSupportImpl.getOrCreateSettings(configuration, debug.getUri(), id);
    assertSame(first, second);
    assertTrue(((JavaScriptDebugConfiguration)second.getConfiguration()).isUseTemporaryUserDataDir());
    assertTrue(((JavaScriptDebugConfiguration)second.getConfiguration()).isUseFirstLineBreakpoints());
    assertNotSame(first, GwtJavaScriptDebugSupportImpl.getOrCreateSettings(configuration, debug.getUri(), "other-browser"));
    assertNotSame(first, GwtJavaScriptDebugSupportImpl.getOrCreateSettings(configuration, "http://localhost:8888/other", id));
  }

  public void testNativeDebuggerRunnerIsAvailable() {
    assertNotNull(GwtJavaScriptDebugSupport.getInstance());
    assertTrue(new GwtJavaScriptDebugSupportImpl().canDebug(getProject()));
  }

  private void replaceSupport(boolean accepts, boolean expectStart) {
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), GwtJavaScriptDebugSupport.class,
      new GwtJavaScriptDebugSupport() {
        @Override public boolean canDebug(Project project) { return accepts; }
        @Override public boolean start(GwtRunConfiguration selected, String url, WebBrowser selectedBrowser) {
          assertTrue("Plain launch must not call debugger", expectStart);
          assertSame(configuration, selected);
          assertSame(browser, selectedBrowser);
          assertEquals("http://localhost:8888/debug", url);
          return accepts;
        }
      }, getTestRootDisposable());
  }
}
