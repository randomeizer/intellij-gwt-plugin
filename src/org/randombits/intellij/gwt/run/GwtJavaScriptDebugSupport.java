package org.randombits.intellij.gwt.run;

import com.intellij.ide.browsers.BrowserLauncher;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/** Optional native JavaScript debugger integration without debugger types in the base launcher. */
public interface GwtJavaScriptDebugSupport {
  static @Nullable GwtJavaScriptDebugSupport getInstance() {
    return ApplicationManager.getApplication().getService(GwtJavaScriptDebugSupport.class);
  }

  static boolean isAvailable(Project project) {
    var support = getInstance();
    return support != null && support.canDebug(project);
  }

  static void openBrowser(GwtRunConfiguration configuration, String url, WebBrowser browser, boolean debug) {
    var support = getInstance();
    if (debug && support != null && support.start(configuration, url, browser)) return;
    BrowserLauncher.getInstance().browse(url, browser, configuration.getProject());
  }

  boolean canDebug(Project project);

  /** Return false to fall back to ordinary browser launch. */
  boolean start(GwtRunConfiguration configuration, String url, WebBrowser browser);
}
