import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.dev.shell.remoteui.RemoteUI;
import com.google.gwt.dev.ui.DoneEvent;
import com.google.gwt.dev.ui.RestartServerEvent;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Source-launched in a child JVM with one real gwt-dev JAR, without plugin protobuf classes. */
public class RemoteUiSmokeClient {
  public static void main(String[] args) {
    try {
      var done = new CountDownLatch(1);
      var restarted = new CountDownLatch(1);
      var ui = new RemoteUI("127.0.0.1", Integer.parseInt(args[0]), "SDK smoke");
      ui.initialize(TreeLogger.INFO);
      ui.setCallback(DoneEvent.getType(), () -> done.countDown());
      ui.setCallback(RestartServerEvent.getType(), logger -> restarted.countDown());
      ui.setStartupUrls(Map.of("smoke", URI.create("http://localhost:8888/smoke").toURL()));
      // GWT sends INITIALIZE first and then negotiates all viewer capabilities.
      ui.moduleLoadComplete(true);
      var first = ui.getModuleLogger("SDK smoke", "127.0.0.1", "http://localhost:8888/smoke",
        "tab", "example.Smoke", "session-one", "smoke", null, TreeLogger.INFO);
      first.getLogger().branch(TreeLogger.INFO, "SDK smoke branch").log(TreeLogger.ERROR, "SDK smoke error");
      first.unload();
      var second = ui.getModuleLogger("SDK smoke", "127.0.0.1", "http://localhost:8888/smoke",
        "tab", "example.Smoke", "session-two", "smoke", null, TreeLogger.INFO);
      second.getLogger().log(TreeLogger.INFO, "SDK smoke reconnected");
      second.unload();
      System.out.println("READY");
      if (!restarted.await(15, TimeUnit.SECONDS)) throw new AssertionError("Restart callback missing");
      System.out.println("RESTARTED");
      if (!done.await(15, TimeUnit.SECONDS)) throw new AssertionError("Shutdown callback missing");
      System.out.println("DONE");
      System.exit(0);
    }
    catch (Throwable error) {
      error.printStackTrace();
      System.exit(1);
    }
  }
}
