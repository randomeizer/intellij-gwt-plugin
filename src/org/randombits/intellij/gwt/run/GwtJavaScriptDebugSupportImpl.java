package org.randombits.intellij.gwt.run;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.ProgramRunnerUtil;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.javascript.debugger.execution.JavaScriptDebugConfiguration;
import com.intellij.javascript.debugger.execution.JavascriptDebugConfigurationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebuggerManager;

import java.util.Objects;

/** Loaded only with the optional JavaScript debugger descriptor. */
public final class GwtJavaScriptDebugSupportImpl implements GwtJavaScriptDebugSupport {
  @Override
  public boolean canDebug(Project project) {
    var template = RunManager.getInstance(project).getConfigurationTemplate(JavascriptDebugConfigurationType.getTypeInstance());
    return ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, template.getConfiguration()) != null;
  }

  @Override
  public boolean start(GwtRunConfiguration configuration, String url, WebBrowser browser) {
    Project project = configuration.getProject();
    if (!canDebug(project)) return false;
    String browserId = browser.getId().toString();
    ApplicationManager.getApplication().invokeLater(() -> {
      for (var session : XDebuggerManager.getInstance(project).getDebugSessions()) {
        if (!session.isStopped() && session.getRunProfile() instanceof JavaScriptDebugConfiguration debug &&
            matches(debug, url, browserId)) return;
      }
      var settings = getOrCreateSettings(configuration, url, browserId);
      ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance());
    }, project.getDisposed());
    return true;
  }

  static RunnerAndConfigurationSettings getOrCreateSettings(GwtRunConfiguration configuration, String url, String browserId) {
    var manager = RunManager.getInstance(configuration.getProject());
    var type = JavascriptDebugConfigurationType.getTypeInstance();
    for (var settings : manager.getConfigurationSettingsList(type)) {
      if (settings.getConfiguration() instanceof JavaScriptDebugConfiguration debug && matches(debug, url, browserId)) return settings;
    }
    var settings = manager.createConfiguration(configuration.getName() + " JavaScript", type);
    var debug = (JavaScriptDebugConfiguration)settings.getConfiguration();
    debug.setUri(url);
    debug.setEngineId(browserId);
    manager.addConfiguration(settings);
    return settings;
  }

  private static boolean matches(JavaScriptDebugConfiguration configuration, String url, String browserId) {
    return Objects.equals(configuration.getUri(), url) && Objects.equals(configuration.getEngineId(), browserId);
  }
}
