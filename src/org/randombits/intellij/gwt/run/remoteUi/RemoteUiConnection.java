package org.randombits.intellij.gwt.run.remoteUi;

import org.randombits.intellij.gwt.run.GwtRunConfiguration;
import org.randombits.intellij.gwt.run.remoteUi.responses.CapabilityExchangeResponse;
import org.randombits.intellij.gwt.run.remoteUi.responses.InitializeResponse;
import org.randombits.intellij.gwt.sdk.GwtVersion;
import com.intellij.ide.browsers.JavaScriptDebuggerStarter;
import com.intellij.ide.browsers.WebBrowser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteUiConnection {
  private final RemoteUiTransport myConnection;
  private final GwtRunConfiguration myConfiguration;
  private final GwtVersion mySdkVersion;
  private final boolean myOpenInBrowser;
  private final boolean myStartJavaScriptDebugger;
  private final WebBrowser myBrowser;
  private final AtomicBoolean myBrowserOpened = new AtomicBoolean();

  public RemoteUiConnection(GwtRunConfiguration configuration, @NotNull GwtVersion sdkVersion, boolean openInBrowser,
                            boolean startJavaScriptDebugger, @Nullable WebBrowser browser) {
    myConfiguration = configuration;
    mySdkVersion = sdkVersion;
    myOpenInBrowser = openInBrowser;
    myStartJavaScriptDebugger = startJavaScriptDebugger;
    myBrowser = browser;
    myConnection = new RemoteUiTransport(7901, 20);
    myConnection.registerHandler(CapabilityExchangeResponse.class, response ->
      myConnection.sendRequest(RemoteUiRequest.createCapabilitiesResponse(response.getMessageId())));
    myConnection.registerHandler(InitializeResponse.class, response -> {
      final List<String> urls = response.getStartupUrlsList();
      if (!urls.isEmpty()) {
        final String url = urls.get(0);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> onInitialized(url));
      }
      myConnection.sendRequest(RemoteUiRequest.createDummyResponseRequest(response.getMessageId()));
    });
  }

  public void sendRestartServerRequest() {
    myConnection.sendRequest(RemoteUiRequest.createRestartServerRequest());
  }

  public @NotNull GwtVersion getSdkVersion() {
    return mySdkVersion;
  }

  private void onInitialized(String url) {
    openBrowserOnce(url);
  }

  public void openBrowserOnce(@NotNull String url) {
    if (myOpenInBrowser && myBrowser != null && myBrowserOpened.compareAndSet(false, true)) {
      JavaScriptDebuggerStarter.Util.startDebugOrLaunchBrowser(myConfiguration, url, myBrowser.getId().toString(),
                                                               myStartJavaScriptDebugger);
    }
  }

  public int open() throws IOException {
    myConnection.open();
    return myConnection.getPort();
  }

  public RemoteUiTransport getConnection() {
    return myConnection;
  }

  public void close() {
    myConnection.close();
  }

}
