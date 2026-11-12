package org.randombits.intellij.gwt.run;

import com.intellij.execution.ExecutionManager;
import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.ParametersList;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.randombits.intellij.gwt.jps.build.common.DelegatingOutputLineReader;
import org.randombits.intellij.gwt.jps.build.common.OutputLineReader;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** Concrete code-server origins. Unknown external/proxied servers use native debugger URL mappings. */
final class GwtCodeServerUrls {
  private static final Key<Set<String>> RUNNING = Key.create("gwt.code.server.urls");
  private static final String READY = "The code server is ready at ";

  private GwtCodeServerUrls() {}

  static Set<String> get(Project project, String pageAuthority) {
    Set<String> result = new LinkedHashSet<>(fromParameters(List.of(), null, pageAuthority));
    var manager = RunManager.getInstanceIfCreated(project);
    if (manager != null) {
      for (var configuration : manager.getAllConfigurationsList()) {
        if (configuration instanceof GwtRunConfiguration gwt) {
          var parameters = new ParametersList();
          parameters.addParametersString(gwt.getGwtState().SHELL_PARAMETERS);
          result.addAll(fromParameters(parameters.getParameters(), gwt.getGwtState().RUN_PAGE, pageAuthority));
        }
      }
    }
    for (var process : ExecutionManager.getInstance(project).getRunningProcesses()) {
      result.addAll(forProcess(process));
    }
    return result;
  }

  static Set<String> fromParameters(List<String> parameters, String page, String pageAuthority) {
    String host = "127.0.0.1";
    String portText = null;
    for (int i = 0; i < parameters.size(); i++) {
      String parameter = parameters.get(i);
      if (parameter.startsWith("-bindAddress=")) host = parameter.substring("-bindAddress=".length());
      else if (parameter.equals("-bindAddress") && i + 1 < parameters.size()) host = parameters.get(++i);
      else if (parameter.startsWith("-codeServerPort=")) portText = parameter.substring("-codeServerPort=".length());
      else if (parameter.equals("-codeServerPort") && i + 1 < parameters.size()) portText = parameters.get(++i);
    }
    // DevMode's web-server -port is independent; its SDM legacy/default code port maps to 9876.
    int port;
    try { port = portText == null ? 9876 : Integer.parseInt(portText); }
    catch (NumberFormatException ignored) { return Set.of(); }
    if (port < 0 || port == 9997) port = 9876;
    if (port == 0 || port > 65535) return Set.of(); // The ready message supplies auto-assigned ports.
    Set<String> result = new LinkedHashSet<>();
    for (String local : List.of("localhost", "127.0.0.1", "::1")) add(result, local, port);
    if (!host.equals("0.0.0.0") && !host.equals("::") && !host.equals("[::]")) add(result, host, port);
    addPageHost(result, page, port);
    addPageHost(result, pageAuthority == null ? null : "http://" + pageAuthority, port);
    return result;
  }

  private static void addPageHost(Set<String> result, String page, int port) {
    if (page == null) return;
    try { add(result, URI.create(page).getHost(), port); }
    catch (IllegalArgumentException ignored) { }
  }

  private static void add(Set<String> result, String host, int port) {
    if (host == null || host.isBlank()) return;
    try { result.add(new URI("http", null, host, port, null, null, null).toASCIIString()); }
    catch (URISyntaxException ignored) { }
  }

  static Set<String> forProcess(ProcessHandler process) {
    var endpoints = process.getUserData(RUNNING);
    return endpoints == null ? Set.of() : Set.copyOf(endpoints);
  }

  static void attach(ProcessHandler process, List<String> parameters, String page) {
    process.putUserData(RUNNING, new CopyOnWriteArraySet<>(fromParameters(parameters, page, null)));
    process.addProcessListener(new ProcessListener() {
      private final OutputLineReader stdout = new DelegatingOutputLineReader(line -> recordReadyLine(process, line));
      private final OutputLineReader stderr = new DelegatingOutputLineReader(line -> recordReadyLine(process, line));
      @Override
      public synchronized void onTextAvailable(ProcessEvent event, Key outputType) {
        (ProcessOutputType.isStderr(outputType) ? stderr : stdout).parseOutput(event.getText());
      }
    });
  }

  static void recordReadyLine(ProcessHandler process, String line) {
    int index = line.indexOf(READY);
    if (index < 0) return;
    String value = line.substring(index + READY.length()).trim().split("\\s+", 2)[0];
    try {
      URI uri = URI.create(value);
      if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null ||
          uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null ||
          !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) return;
      int port = uri.getPort();
      if (port < 1 || port > 65535) return;
      var endpoints = process.getUserData(RUNNING);
      if (endpoints != null) endpoints.add(new URI(uri.getScheme(), null, uri.getHost(), port, null, null, null).toASCIIString());
    }
    catch (IllegalArgumentException | URISyntaxException ignored) { }
  }
}
