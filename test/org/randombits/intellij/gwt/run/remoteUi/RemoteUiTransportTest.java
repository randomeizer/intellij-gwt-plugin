package org.randombits.intellij.gwt.run.remoteUi;

import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LightPlatformTestCase;
import org.randombits.intellij.gwt.run.remoteUi.responses.InitializeResponse;
import org.randombits.intellij.gwt.run.remoteUi.responses.RemoteUiResponse;
import org.randombits.intellij.gwt.shell.remoteUi.generated.RemoteMessageProto.Message;
import org.randombits.intellij.gwt.shell.remoteUi.generated.RemoteMessageProto.Message.Request.ViewerRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RemoteUiTransportTest extends LightPlatformTestCase {
  private RemoteUiTransport myTransport;

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myTransport != null) {
        myTransport.close();
        assertTrue("RemoteUI workers leaked", myTransport.awaitTermination(5, TimeUnit.SECONDS));
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testFragmentedAndCoalescedFramesDispatchToEveryExactTypeHandlerInOrder() throws Exception {
    myTransport = transport(0, 1);
    var received = new CopyOnWriteArrayList<String>();
    var done = new CountDownLatch(4);
    myTransport.registerHandler(RemoteUiResponse.class, response -> fail("Must dispatch by exact response type"));
    myTransport.registerHandler(InitializeResponse.class, response -> {
      received.add(response.getMessageId() + ":" + response.getStartupUrlsList().getFirst());
      done.countDown();
    });
    myTransport.registerHandler(InitializeResponse.class, response -> {
      received.add(response.getMessageId() + ":second");
      done.countDown();
      myTransport.sendRequest(RemoteUiRequest.createDummyResponseRequest(response.getMessageId()));
    });
    String url = "http://localhost/" + "fragment".repeat(30);
    try (var client = connect()) {
      var bytes = new ByteArrayOutputStream();
      initialize(41, url).writeDelimitedTo(bytes);
      initialize(42, "next").writeDelimitedTo(bytes);
      byte[] frames = bytes.toByteArray();
      client.getOutputStream().write(frames, 0, 1);
      client.getOutputStream().flush();
      client.getOutputStream().write(frames, 1, 2);
      client.getOutputStream().flush();
      client.getOutputStream().write(frames, 3, frames.length - 3);
      client.getOutputStream().flush();
      assertTrue(done.await(5, TimeUnit.SECONDS));
      assertEquals(List.of("41:" + url, "41:second", "42:next", "42:second"), received);
      assertEquals(41, Message.parseDelimitedFrom(client.getInputStream()).getMessageId());
      assertEquals(42, Message.parseDelimitedFrom(client.getInputStream()).getMessageId());
    }
  }

  public void testRequestsQueuedBeforeConnectKeepTheirWireIdsAndOrder() throws Exception {
    myTransport = transport(0, 1);
    myTransport.sendRequest(RemoteUiRequest.createDummyResponseRequest(9));
    myTransport.sendRequest(RemoteUiRequest.createRestartServerRequest());
    myTransport.sendRequest(RemoteUiRequest.createDummyResponseRequest(10));
    try (var client = connect()) {
      assertEquals(9, Message.parseDelimitedFrom(client.getInputStream()).getMessageId());
      var restart = Message.parseDelimitedFrom(client.getInputStream());
      assertEquals(-1, restart.getMessageId());
      assertEquals(Message.MessageType.REQUEST, restart.getMessageType());
      assertEquals(Message.Request.ServiceType.DEV_MODE, restart.getRequest().getServiceType());
      assertTrue(restart.getRequest().getDevModeRequest().hasRestartWebServer());
      assertEquals(10, Message.parseDelimitedFrom(client.getInputStream()).getMessageId());
    }
  }

  public void testUnknownResponseDoesNotPreventFollowingRequestDispatch() throws Exception {
    myTransport = transport(0, 1);
    myTransport.registerHandler(InitializeResponse.class, response ->
      myTransport.sendRequest(RemoteUiRequest.createDummyResponseRequest(response.getMessageId())));
    try (var client = connect()) {
      Message.newBuilder().setMessageType(Message.MessageType.RESPONSE).setMessageId(1)
        .setResponse(Message.Response.newBuilder()).build().writeDelimitedTo(client.getOutputStream());
      initialize(2, "next").writeDelimitedTo(client.getOutputStream());
      client.getOutputStream().flush();
      assertEquals(2, Message.parseDelimitedFrom(client.getInputStream()).getMessageId());
    }
  }

  public void testEndOfStreamStopsWorkersAndAllowsFreshRunOnSamePort() throws Exception {
    myTransport = transport(0, 1);
    var childDisposed = new AtomicBoolean();
    Disposer.register(myTransport, () -> childDisposed.set(true));
    int port;
    try (var client = connect()) {
      port = myTransport.getPort();
      client.shutdownOutput();
      assertPeerDisconnected(client);
    }
    assertTrue(myTransport.awaitTermination(5, TimeUnit.SECONDS));
    assertFalse("EOF must let queued console updates drain until owner disposal", childDisposed.get());
    myTransport.close();
    assertTrue(childDisposed.get());
    myTransport = transport(port, 1);
    myTransport.registerHandler(InitializeResponse.class, response ->
      myTransport.sendRequest(RemoteUiRequest.createDummyResponseRequest(response.getMessageId())));
    try (var client = connect()) {
      initialize(77, "fresh run").writeDelimitedTo(client.getOutputStream());
      client.getOutputStream().flush();
      assertEquals(77, Message.parseDelimitedFrom(client.getInputStream()).getMessageId());
    }
  }

  public void testDisposeUnblocksPendingAcceptAndReleasesPort() throws Exception {
    myTransport = transport(0, 1);
    myTransport.open();
    int port = myTransport.getPort();
    Disposer.dispose(myTransport);
    assertTrue(myTransport.awaitTermination(5, TimeUnit.SECONDS));
    try (var rebound = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
      assertEquals(port, rebound.getLocalPort());
    }
  }

  public void testDisposeUnblocksPartialFrameReadAndWaitingWriter() throws Exception {
    myTransport = transport(0, 1);
    try (var client = connect()) {
      client.getOutputStream().write(0x80);
      client.getOutputStream().flush();
      myTransport.close();
      myTransport.close();
      assertTrue(myTransport.awaitTermination(5, TimeUnit.SECONDS));
      assertPeerDisconnected(client);
      myTransport.sendRequest(RemoteUiRequest.createDummyResponseRequest(1));
    }
  }

  public void testMalformedLengthClosesConnectionAndStopsWorkers() throws Exception {
    assertBadFrame(new byte[]{(byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80,
                              (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80, (byte)0x80});
  }

  public void testTruncatedPayloadClosesConnectionAndStopsWorkers() throws Exception {
    assertBadFrame(new byte[]{5, 8});
  }

  public void testBusyPortFallsBackToNextPortAndExhaustionFailsSynchronously() throws Exception {
    try (var busy = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      myTransport = transport(busy.getLocalPort(), 1);
      try {
        myTransport.open();
        fail("Occupied range should fail synchronously");
      }
      catch (IOException expected) {
        assertFalse(busy.isClosed());
      }
      myTransport.close();
      int next = busy.getLocalPort() + 1;
      if (next > 65535) return;
      try (var probe = new ServerSocket(next, 0, InetAddress.getLoopbackAddress())) {
        // Only select a range whose second port is available before checking fallback.
      }
      myTransport = transport(busy.getLocalPort(), 2);
      try (var client = connect()) {
        assertEquals(next, myTransport.getPort());
      }
    }
  }

  private void assertBadFrame(byte[] bytes) throws Exception {
    myTransport = transport(0, 1);
    try (var client = connect()) {
      client.getOutputStream().write(bytes);
      client.getOutputStream().flush();
      client.shutdownOutput();
      assertPeerDisconnected(client);
      assertTrue(myTransport.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  private static RemoteUiTransport transport(int firstPort, int attempts) {
    // An exact loopback bind prevents ephemeral-port collisions with specific-address IDE listeners.
    return new RemoteUiTransport(firstPort, attempts, InetAddress.getLoopbackAddress());
  }

  private Socket connect() throws Exception {
    myTransport.open();
    var client = new Socket(InetAddress.getLoopbackAddress(), myTransport.getPort());
    client.setSoTimeout(5000);
    return client;
  }

  private static void assertPeerDisconnected(Socket client) throws IOException {
    try {
      assertEquals(-1, client.getInputStream().read());
    }
    catch (SocketException expected) {
      // Closing a socket with a partial/unread frame can send TCP RST rather than FIN, notably on Linux.
      // A read timeout is not a SocketException and still fails this test.
    }
  }

  private static Message initialize(int id, String url) {
    return Message.newBuilder().setMessageType(Message.MessageType.REQUEST).setMessageId(id)
      .setRequest(Message.Request.newBuilder().setServiceType(Message.Request.ServiceType.VIEWER)
        .setViewerRequest(ViewerRequest.newBuilder().setRequestType(ViewerRequest.RequestType.INITIALIZE)
          .setInitialize(ViewerRequest.Initialize.newBuilder().addStartupURLs(url)))).build();
  }
}
