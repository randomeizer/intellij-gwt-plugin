package org.randombits.intellij.gwt.run.remoteUi;

import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LoggedErrorProcessor;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import org.randombits.intellij.gwt.run.GwtRunConfiguration;
import org.randombits.intellij.gwt.run.GwtRunConfigurationType;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;
import org.randombits.intellij.gwt.shell.remoteUi.generated.RemoteMessageProto.Message;
import org.randombits.intellij.gwt.shell.remoteUi.generated.RemoteMessageProto.Message.Request.ViewerRequest;

import javax.swing.JComboBox;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;

public class RemoteUiLogManagerTest extends LightJavaCodeInsightFixtureTestCase {
  private RemoteUiConnection myConnection;
  private RemoteUiView myView;
  private JComboBox<?> myModules;
  private Socket myClient;
  private ExecutorService myClientExecutor;
  private Disposable myUiDisposable;
  private AccessToken myErrorProcessor;
  private final AtomicReference<Throwable> myAsyncError = new AtomicReference<>();
  private int myMessageId;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myUiDisposable = Disposer.newDisposable();
    Disposer.register(getTestRootDisposable(), myUiDisposable);
    var configuration = (GwtRunConfiguration)GwtRunConfigurationType.getFactory().createTemplateConfiguration(getProject());
    myConnection = new RemoteUiConnection(configuration, GwtVersionImpl.VERSION_2_12, false, false, null);
    var mainConsole = TextConsoleBuilderFactory.getInstance().createBuilder(getProject()).getConsole();
    myView = new RemoteUiView(getProject(), mainConsole, myConnection, false);
    Disposer.register(myUiDisposable, myView);
    var layout = RunnerLayoutUi.Factory.getInstance(getProject()).create("GwtRemoteUiTest", "GWT", getName(), myUiDisposable);
    myView.buildUi(layout);
    myModules = UIUtil.findComponentOfType(layout.findContent("GWT_REMOTE_UI_MODULES_LOG").getComponent(), JComboBox.class);
    assertNotNull(myModules);
    assertEquals(0, myModules.getItemCount());
    // Cancel queued work before disposing the view and its console editors.
    Disposer.register(myUiDisposable, myConnection.getConnection());
    myErrorProcessor = LoggedErrorProcessor.executeWith(new LoggedErrorProcessor() {
      @Override
      public Set<Action> processError(String category, String message, String[] details, Throwable error) {
        myAsyncError.compareAndSet(null, error != null ? error : new AssertionError(message));
        return Action.ALL;
      }
    });
    myClientExecutor = Executors.newSingleThreadExecutor();
    int port = myConnection.open();
    myClient = new Socket();
    myClient.setSoTimeout(5000);
    onClient(() -> {
      myClient.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5000);
      return null;
    });
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myClient != null) myClient.close();
    }
    finally {
      try {
        try {
          if (myUiDisposable != null) Disposer.dispose(myUiDisposable);
        }
        finally {
          if (myClientExecutor != null) {
            myClientExecutor.shutdownNow();
            assertTrue("RemoteUI client did not terminate", myClientExecutor.awaitTermination(5, TimeUnit.SECONDS));
          }
        }
      }
      finally {
        myClient = null;
        myClientExecutor = null;
        myConnection = null;
        myView = null;
        myModules = null;
        myUiDisposable = null;
        if (myErrorProcessor != null) myErrorProcessor.finish();
        myErrorProcessor = null;
        super.tearDown();
      }
    }
  }

  public void testModuleRegistrationCreatesConsoleAndDisplaysLoadingMessage() throws Exception {
    addModule("example.App");
    var console = awaitModuleConsole("example.App", 1);
    awaitText(console, "Loading module: example.App\n");
    assertEquals("example.App", myModules.getSelectedItem());
    assertTrue(console.getText().startsWith("Loading module: example.App\n"));
  }

  public void testModuleErrorsDisplayDetailsNestedIndentationAndErrorFormatting() throws Exception {
    int module = addModule("example.App");
    var console = awaitModuleConsole("example.App", 1);
    int branch = addBranch(module, "Resolving deferred binding");
    int nested = addBranch(branch, "Rebinding example.Service");
    addEntry(nested, "ERROR", "Failed to resolve deferred binding", "No suitable implementation found");
    addEntry(branch, "INFO", "Finished resolving", "");

    String expected = "INFO: Resolving deferred binding\n" +
                      "  INFO: Rebinding example.Service\n" +
                      "    ERROR: Failed to resolve deferred binding\nNo suitable implementation found\n" +
                      "  INFO: Finished resolving\n";
    awaitText(console, expected);
    assertOutputType(console, "ERROR: Failed to resolve deferred binding", ConsoleViewContentType.ERROR_OUTPUT);
    assertOutputType(console, "No suitable implementation found", ConsoleViewContentType.ERROR_OUTPUT);
    assertOutputType(console, "INFO: Finished resolving", ConsoleViewContentType.NORMAL_OUTPUT);
  }

  public void testModuleConsolesAreReusedAndKeepDifferentModulesSeparate() throws Exception {
    int app = addModule("example.App");
    var appConsole = awaitModuleConsole("example.App", 1);
    addEntry(app, "INFO", "First app message", "");
    awaitText(appConsole, "INFO: First app message\n");

    int other = addModule("example.Other");
    var otherConsole = awaitModuleConsole("example.Other", 2);
    addEntry(other, "INFO", "Other module message", "");
    awaitText(otherConsole, "INFO: Other module message\n");
    assertNotSame(appConsole, otherConsole);

    int reconnectedApp = addModule("example.App");
    addEntry(reconnectedApp, "INFO", "Reconnected app message", "");
    awaitText(appConsole, "INFO: Reconnected app message\n");
    assertEquals(2, myModules.getItemCount());
    assertSame(appConsole, myView.getModuleConsole("example.App"));
    assertFalse(appConsole.getText().contains("Other module message"));
    assertFalse(otherConsole.getText().contains("app message"));
  }

  public void testMainAndServerLogsReachTheirOwnConsoles() throws Exception {
    int main = addLog(ViewerRequest.AddLog.newBuilder().setType(ViewerRequest.AddLog.LogType.MAIN)
      .setMainLog(ViewerRequest.AddLog.MainLog.newBuilder()));
    int server = addLog(ViewerRequest.AddLog.newBuilder().setType(ViewerRequest.AddLog.LogType.WEB_SERVER)
      .setServerLog(ViewerRequest.AddLog.ServerLog.newBuilder().setName("Web server")));
    addEntry(main, "INFO", "Main log message", "");
    addEntry(server, "WARN", "Server log message", "");

    var mainConsole = (ConsoleViewImpl)myView.getMainConsole();
    var serverConsole = (ConsoleViewImpl)myView.getServerConsole();
    awaitText(mainConsole, "INFO: Main log message\n");
    awaitText(serverConsole, "WARN: Server log message\n");
    assertEquals("INFO: Main log message\n", mainConsole.getText());
    assertEquals("WARN: Server log message\n", serverConsole.getText());
    assertEquals(0, myModules.getItemCount());
  }

  public void testInitializationCapabilitiesAndRemoteFailureReachMainConsole() throws Exception {
    exchange(ViewerRequest.newBuilder().setRequestType(ViewerRequest.RequestType.INITIALIZE)
      .setInitialize(ViewerRequest.Initialize.newBuilder().addStartupURLs("http://localhost:8888/App.html")));
    var response = exchange(ViewerRequest.newBuilder().setRequestType(ViewerRequest.RequestType.CAPABILITY_EXCHANGE)
      .setCapabilityExchange(ViewerRequest.CapabilityExchange.newBuilder()));
    assertEquals(Set.of(ViewerRequest.RequestType.values()), response.getResponse().getViewerResponse()
      .getCapabilityExchange().getCapabilitiesList().stream().map(capability -> capability.getCapability())
      .collect(java.util.stream.Collectors.toSet()));
    onClient(() -> {
      Message.newBuilder().setMessageType(Message.MessageType.FAILURE).setMessageId(1000)
        .setFailure(Message.Failure.newBuilder().setMessage("Remote SDK failure").setStackTrace("remote trace"))
        .build().writeDelimitedTo(myClient.getOutputStream());
      myClient.getOutputStream().flush();
      return null;
    });
    var console = (ConsoleViewImpl)myView.getMainConsole();
    awaitText(console, "http://localhost:8888/App.html");
    awaitText(console, "Remote SDK failure");
    assertOutputType(console, "Remote SDK failure", ConsoleViewContentType.ERROR_OUTPUT);
  }

  public void testPeerDisconnectLetsFinalQueuedLogReachConsole() throws Exception {
    int module = addModule("example.App");
    var console = awaitModuleConsole("example.App", 1);
    addEntry(module, "INFO", "Last message before EOF", "");
    myClient.close();
    awaitText(console, "INFO: Last message before EOF\n");
    assertTrue(myConnection.getConnection().awaitTermination(5, TimeUnit.SECONDS));
  }

  private int addModule(String name) throws Exception {
    var module = ViewerRequest.AddLog.ModuleLog.newBuilder().setName(name)
      .setUrl("http://localhost/App.html?gwt.codesvr=127.0.0.1:9997")
      .setUserAgent("RemoteUI regression client").setRemoteHost("127.0.0.1");
    return addLog(ViewerRequest.AddLog.newBuilder().setType(ViewerRequest.AddLog.LogType.MODULE).setModuleLog(module));
  }

  private int addLog(ViewerRequest.AddLog.Builder log) throws Exception {
    var response = exchange(ViewerRequest.newBuilder().setRequestType(ViewerRequest.RequestType.ADD_LOG).setAddLog(log));
    assertTrue(response.getResponse().getViewerResponse().hasAddLog());
    return response.getResponse().getViewerResponse().getAddLog().getLogHandle();
  }

  private int addBranch(int parent, String summary) throws Exception {
    var response = exchange(ViewerRequest.newBuilder().setRequestType(ViewerRequest.RequestType.ADD_LOG_BRANCH)
      .setAddLogBranch(ViewerRequest.AddLogBranch.newBuilder().setParentLogHandle(parent).setIndexInParent(0)
        .setLogData(ViewerRequest.LogData.newBuilder().setLevel("INFO").setSummary(summary))));
    assertTrue(response.getResponse().getViewerResponse().hasAddLogBranch());
    return response.getResponse().getViewerResponse().getAddLogBranch().getLogHandle();
  }

  private void addEntry(int handle, String level, String summary, String details) throws Exception {
    exchange(ViewerRequest.newBuilder().setRequestType(ViewerRequest.RequestType.ADD_LOG_ENTRY)
      .setAddLogEntry(ViewerRequest.AddLogEntry.newBuilder().setLogHandle(handle).setIndexInLog(0)
        .setLogData(ViewerRequest.LogData.newBuilder().setLevel(level).setSummary(summary).setDetails(details))));
  }

  private Message exchange(ViewerRequest.Builder request) throws Exception {
    int id = ++myMessageId;
    var message = Message.newBuilder().setMessageType(Message.MessageType.REQUEST).setMessageId(id)
      .setRequest(Message.Request.newBuilder().setServiceType(Message.Request.ServiceType.VIEWER).setViewerRequest(request)).build();
    return onClient(() -> {
      message.writeDelimitedTo(myClient.getOutputStream());
      myClient.getOutputStream().flush();
      var response = Message.parseDelimitedFrom(myClient.getInputStream());
      assertNotNull("RemoteUI closed before replying", response);
      assertEquals(id, response.getMessageId());
      assertEquals(Message.MessageType.RESPONSE, response.getMessageType());
      return response;
    });
  }

  private <T> T onClient(Callable<T> operation) throws Exception {
    var future = myClientExecutor.submit(operation);
    await("RemoteUI client timed out", future::isDone);
    return future.get();
  }

  private ConsoleViewImpl awaitModuleConsole(String name, int moduleCount) {
    await("Module console was not created for " + name, () -> myModules.getItemCount() == moduleCount);
    // Only retrieve a console after its selector item proves the queued handler created it.
    assertTrue(IntStream.range(0, myModules.getItemCount()).anyMatch(i -> name.equals(myModules.getItemAt(i))));
    return (ConsoleViewImpl)myView.getModuleConsole(name);
  }

  private void awaitText(ConsoleViewImpl console, String text) {
    await("Console did not display: " + text, () -> console.getEditor() != null && console.getText().contains(text));
  }

  private void assertOutputType(ConsoleViewImpl console, String text, ConsoleViewContentType type) {
    int start = console.getText().indexOf(text);
    assertTrue("Missing console text: " + text, start >= 0);
    var markup = DocumentMarkupModel.forDocument(console.getEditor().getDocument(), getProject(), false);
    assertNotNull(markup);
    assertTrue("Wrong output formatting for: " + text, Arrays.stream(markup.getAllHighlighters()).anyMatch(highlighter ->
      highlighter.getStartOffset() <= start && highlighter.getEndOffset() >= start + text.length() &&
      type.getAttributesKey().equals(highlighter.getTextAttributesKey())));
  }

  private void await(String message, BooleanSupplier condition) {
    PlatformTestUtil.waitWithEventsDispatching(message, () -> {
      var error = myAsyncError.get();
      if (error != null) throw new AssertionError("RemoteUI processing failed", error);
      return condition.getAsBoolean();
    }, 10);
  }
}
