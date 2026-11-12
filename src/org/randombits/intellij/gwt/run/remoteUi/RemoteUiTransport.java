package org.randombits.intellij.gwt.run.remoteUi;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import org.randombits.intellij.gwt.run.remoteUi.responses.RemoteUiResponse;
import org.randombits.intellij.gwt.run.remoteUi.responses.RemoteUiResponseReader;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** One DevMode process, with ordered protobuf reads and queued writes on separate workers. */
public final class RemoteUiTransport implements Disposable {
  private static final Logger LOG = Logger.getInstance(RemoteUiTransport.class);
  private final Object myLock = new Object();
  private final int myFirstPort;
  private final int myPortAttempts;
  private final InetAddress myBindAddress;
  private final BlockingQueue<RemoteUiRequest> myRequests = new LinkedBlockingQueue<>();
  private final Map<Class<?>, List<Consumer<RemoteUiResponse>>> myHandlers = new ConcurrentHashMap<>();
  private final AtomicBoolean myCloseRequested = new AtomicBoolean();
  private final ExecutorService myWorkers = Executors.newFixedThreadPool(2,
    Thread.ofPlatform().daemon().name("GWT RemoteUI transport-", 0).factory());
  private boolean myDisposed;
  private volatile boolean myStopped;
  private ServerSocket myServer;
  private Socket myClient;
  private int myPort;

  RemoteUiTransport(int firstPort, int portAttempts) {
    this(firstPort, portAttempts, null);
  }

  RemoteUiTransport(int firstPort, int portAttempts, InetAddress bindAddress) {
    if (firstPort < 0 || firstPort > 65535 || portAttempts < 1 || firstPort + (long)portAttempts - 1 > 65535) {
      throw new IllegalArgumentException("Invalid RemoteUI port range");
    }
    myFirstPort = firstPort;
    myPortAttempts = portAttempts;
    myBindAddress = bindAddress;
  }

  public <T extends RemoteUiResponse> void registerHandler(Class<T> type, Consumer<? super T> handler) {
    synchronized (myLock) {
      if (myDisposed) throw new IllegalStateException("RemoteUI transport is closed");
      myHandlers.computeIfAbsent(type, ignored -> new CopyOnWriteArrayList<>())
        .add(response -> handler.accept(type.cast(response)));
    }
  }

  public void sendRequest(RemoteUiRequest request) {
    synchronized (myLock) {
      if (!myStopped) myRequests.add(request);
    }
  }

  public void open() throws IOException {
    synchronized (myLock) {
      if (myStopped || myServer != null) throw new IOException("RemoteUI transport has already opened or closed");
      myServer = bind();
      myPort = myServer.getLocalPort();
      myWorkers.execute(this::acceptAndRead);
    }
  }

  private ServerSocket bind() throws IOException {
    IOException failure = null;
    for (int attempt = 0; attempt < myPortAttempts; attempt++) {
      try {
        // A null address preserves the existing wildcard binding and consecutive port fallback.
        return new ServerSocket(myFirstPort + attempt, 0, myBindAddress);
      }
      catch (IOException e) {
        failure = e;
      }
    }
    throw failure;
  }

  public int getPort() {
    synchronized (myLock) {
      return myPort;
    }
  }

  private void acceptAndRead() {
    try (Socket client = myServer.accept()) {
      synchronized (myLock) {
        if (myStopped) return;
        myClient = client;
        myWorkers.execute(() -> writeRequests(client));
      }
      var reader = new RemoteUiResponseReader(client.getInputStream());
      RemoteUiResponse response;
      while (!myStopped && (response = reader.readResponse()) != null) {
        // Several consumers handle INITIALIZE; only handlers for the exact wire response type run.
        var handlers = myHandlers.get(response.getClass());
        if (handlers != null) {
          for (var handler : handlers) {
            if (myStopped) break;
            handler.accept(response);
          }
        }
      }
    }
    catch (IOException e) {
      if (!myStopped) LOG.info("RemoteUI connection ended", e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    catch (RuntimeException e) {
      if (!myStopped) LOG.warn("RemoteUI handler failed", e);
    }
    finally {
      stop();
    }
  }

  private void writeRequests(Socket client) {
    try {
      var output = new BufferedOutputStream(client.getOutputStream());
      while (!myStopped) {
        myRequests.take().sendMessage(output);
        output.flush();
      }
    }
    catch (IOException e) {
      if (!myStopped) LOG.info("RemoteUI write failed", e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    finally {
      stop();
    }
  }

  public void close() {
    if (myCloseRequested.compareAndSet(false, true)) Disposer.dispose(this);
  }

  @Override
  public void dispose() {
    synchronized (myLock) {
      if (myDisposed) return;
      myDisposed = true;
      myCloseRequested.set(true);
      stop();
      myHandlers.clear();
    }
  }

  private void stop() {
    synchronized (myLock) {
      if (myStopped) return;
      myStopped = true;
      closeSocket(myServer);
      closeSocket(myClient);
      myRequests.clear();
      myWorkers.shutdownNow();
      // EOF stops I/O but leaves queued console updates alive until their owner disposes this transport.
    }
  }

  private static void closeSocket(AutoCloseable socket) {
    if (socket == null) return;
    try {
      socket.close();
    }
    catch (Exception e) {
      LOG.debug("Closing RemoteUI socket", e);
    }
  }

  boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    return myWorkers.awaitTermination(timeout, unit);
  }
}
