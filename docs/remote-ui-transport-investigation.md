# RemoteUI transport investigation (#49)

The plugin can replace `com.intellij.util.io.socketConnection` with a small JDK transport while retaining its protobuf messages and run lifecycle. Preserve one accepted client per run, FIFO writes, independent reading, and exact-class handler fanout. Explicit socket closure on EOF, failure, and disposal fixes gaps in the old implementation; it should be described as a cleanup improvement rather than exact emulation.

## Evidence and reproduction

Inspected the actual IDEA **263.7307.200** SDK, not signatures from an older online mirror:

```text
~/.gradle/caches/9.3.1/transforms/c966849d6ed291580f0545dcbdf20e80/
  transformed/idea-2026.3-aarch64/lib/intellij.platform.ide.socketConnection.jar
SHA-256: a8c0a57748bc8a6134968072e44a3a7a2951e5d678ab34f7c75256cb4758f8f6
```

Use `javap -c -p -classpath <jar>` on these owning classes to reproduce the findings:

- `com.intellij.util.io.socketConnection.impl.ServerSocketConnectionImpl`
- `com.intellij.util.io.socketConnection.impl.SocketConnectionBase`
- `com.intellij.util.io.socketConnection.impl.ResponseProcessor`
- `com.intellij.openapi.util.MultiValuesMap` and its `$1` class, in adjacent `intellij.platform.ide.impl.jar`.

The relevant SDKs are `org.gwtproject:gwt-dev:2.12.2` and `:2.13.1`, cached under `/private/tmp/gwt212-sdk-probe/repo` and `/private/tmp/gwt213-sdk-probe/repo`. Both JARs include primary `.java` source and `com/google/gwt/dev/shell/remoteui/remotemessage.proto`. The embedded `RemoteUI.java`, `MessageTransport.java`, `ViewerServiceClient.java`, and schema compare byte-for-byte equal across these versions. The corresponding upstream source was also checked through GitHub's API. Links below identify the owning source; the SDK JARs anchor the exact version evidence.

## Existing SDK transport behavior

| Concern | Observed IDEA 263.7307.200 behavior | Implication |
| --- | --- | --- |
| Port selection | `createSocket()` tries `defaultPort + i` for `0 <= i < attempts`, continuing after `IOException`. The plugin passes `7901, null, 20`. | Exactly **7901–7920 inclusive**, then throw the last bind exception. |
| Bind address | Constructs `new ServerSocket(port, 0, bindAddress)` with `bindAddress == null`. | Existing listener binds the wildcard address; changing to loopback is a separate behavior change. |
| `open()` | Creates the listening socket and records its local port synchronously, then schedules a pooled accept task. | Return only after a successful bind; do not wait for the GWT process to connect. |
| Acceptance | `waitForConnection()` performs one `accept()`, calls `attachToSocket()`, and ultimately closes accepted/listening sockets. No accept loop exists. | No automatic in-run TCP reconnect or second accepted client. A new run creates a fresh transport. |
| Outgoing requests | Unbounded `LinkedBlockingQueue`; `sendRequest` enqueues even before accept; one writer repeatedly takes and writes. | Keep FIFO ordering and avoid blocking caller threads on network writes. |
| Concurrent I/O | `attachToSocket()` starts a separate response-reading task, then runs the writer loop itself. | Reader handlers must be able to enqueue acknowledgments while the writer is idle. |
| Flushing | Neither SDK request loop nor plugin `RemoteUiRequest.sendMessage()` explicitly calls `OutputStream.flush()`. Socket output is passed directly to protobuf. | Keep each delimited message promptly deliverable; adding buffering requires flush after each complete message. Explicit flush on raw socket output is harmless. |
| Class dispatch | `ResponseProcessor` looks up `response.getClass()`, snapshots all handlers for that exact class under a lock, then invokes them sequentially outside that lock on the reader thread. | Do not use assignability matching or replace an earlier handler. `InitializeResponse` has both browser and console consumers. |
| Handler ordering | Default `MultiValuesMap()` uses an unordered `HashSet` for each class. | Registration order was not guaranteed; registering the same handler instance twice deduplicated it. A list with registration order is a deterministic refinement. |
| Handler failure | Reader catches `InterruptedException` and `IOException`, with a final stopped flag. A handler's `RuntimeException` escapes and prevents later handlers for that message. | Treat a handler exception as terminal; continuing subsequent messages would change legacy failure behavior. Closing owned resources at termination improves cleanup. |
| Reader EOF/failure | EOF returns from the reader; I/O failure is logged and stops that reader. Neither path directly closes the writer/listener. | Do not reproduce the resource leak: terminate the whole new transport. |
| `close()` | Sets a stopping flag once, interrupts tracked threads, calls the empty base `onClosing()`, stops the reader, and calls `Disposer.dispose(this)`. Server subclass has no `onClosing()` override. | Pending blocking accept/read operations are not explicitly socket-closed by this path. New transport should close listener and accepted socket explicitly and unblock the writer. |
| `dispose()` | Base method only logs; child disposables are handled by the IDE disposer. | New transport must remain a proper `Disposable`, because the log manager registers its coroutine scope cancellation as a child. |

All entries in this table are grounded in the named methods' exact SDK bytecode. Generic request-ID callbacks/timeouts exist in the SDK but the plugin only uses class handlers and `sendRequest(request)`; they do not need a replacement abstraction.

## Wire protocol and existing plugin behavior

The owning [GWT schema](https://github.com/gwtproject/gwt/blob/main/dev/core/src/com/google/gwt/dev/shell/remoteui/remotemessage.proto) and plugin [schema](../remoteUi/remotemessage.proto) have the same field numbers, types, enum values, and requiredness. Local differences are explicit `syntax = "proto2"`, the generated Java package, and `optimize_for = LITE_RUNTIME`; none changes this wire format.

Messages are protobuf **length-delimited**, using `Message.writeDelimitedTo` and `Message.parseDelimitedFrom`, not newline, Java serialization, or a fixed-width length. The envelope carries `REQUEST`, `RESPONSE`, or `FAILURE` and a required `uint32 messageId`. Responses retain the incoming request's message ID. GWT's own [MessageTransport](https://github.com/gwtproject/gwt/blob/main/dev/core/src/com/google/gwt/dev/shell/remoteui/MessageTransport.java) matches responses/failures against pending IDs and uses an independent FIFO send queue. The plugin's restart request retains the existing Java ID `-1`; its wire interpretation is uint32. Preserve it rather than introducing request-ID allocation in this transport change.

The plugin's reader converts incoming viewer requests into `RemoteUiResponse` subclasses despite that local naming. Ordinary incoming `RESPONSE` messages and unsupported request types become `UnknownRemoteUiResponse`, which has no registered consumer. `FAILURE` becomes `FailureResponse` for the console. Keep those conversion rules in the existing reader; a transport replacement need not become a new protocol implementation. See [reader](../src/org/randombits/intellij/gwt/run/remoteUi/responses/RemoteUiResponseReader.java) and [request builder](../src/org/randombits/intellij/gwt/run/remoteUi/RemoteUiRequest.java).

GWT's [RemoteUI](https://github.com/gwtproject/gwt/blob/main/dev/core/src/com/google/gwt/dev/shell/remoteui/RemoteUI.java) creates one socket and starts its transport immediately. When modules finish loading, it sends `INITIALIZE` and waits for the acknowledgment, then performs capability exchange. Do not assume capability exchange arrives first. The [ViewerServiceClient](https://github.com/gwtproject/gwt/blob/main/dev/core/src/com/google/gwt/dev/shell/remoteui/ViewerServiceClient.java) blocks for these replies and for log creation/disconnection. Every current acknowledgment therefore matters, including empty response envelopes for initialize, log entries, and disconnect.

The plugin currently advertises all six viewer capabilities: initialize, capability exchange, add log, add log branch, add log entry, and disconnect. Add-log and branch replies return allocated handles; restart sends a `DEV_MODE/RESTART_WEB_SERVER` request. Startup browser dispatch remains on Swing's event queue and guarded by the existing once-only flag. These are protocol/UI behavior to preserve; debugger API migration belongs to #51.

Closing the IDE-side socket intentionally signals GWT shutdown: its `RemoteUI.onTermination()` closes its own socket and invokes the registered `DoneCallback`. A transport must not silently reconnect after EOF. A module logger disconnect/reconnect on the **same** live TCP session is separate from reconnecting the transport.

## Recommended implementation boundary

- Own a listener, one accepted socket, one reader task, and one FIFO writer task using JDK networking/concurrency.
- Retain synchronous twenty-port binding, wildcard address, pre-accept enqueueing, single accepted client, exact-class multi-consumer dispatch, and existing protobuf builders/reader.
- Keep callbacks outside transport state locks. Keep UI scheduling in existing handlers.
- Have one idempotent I/O stop path for explicit close, IDE disposal, reader EOF, malformed/truncated input, write failure, and handler failure. Explicitly close both sockets, unblock queue waiting and shut down owned workers. Dispose child lifetimes when the run owner explicitly closes/disposes the transport; EOF must leave already queued console updates alive long enough to drain.
- Preserve graceful response handling before termination; reject or drop subsequent enqueue attempts after terminal close. The old queue accepted unusable post-close requests; that behavior has no useful protocol contract.

The final two bullets deliberately improve cleanup. They do not claim the old SDK closed all its resources promptly.

## Real supported SDK smoke test

A small **separate JVM** can exercise the actual GWT implementation without compiling a web application or opening a browser. Use each cached `gwt-dev` JAR in turn and the SDK-owned `RemoteUI`, not plugin-generated protobuf as the client:

1. Start the plugin-owned listener and install real protocol acknowledgment handlers before launching the child.
2. Child constructs `new RemoteUI("127.0.0.1", port, "smoke")`, calls `initialize(TreeLogger.INFO)`, and registers `DoneEvent`/`RestartServerEvent` callbacks.
3. Child supplies a startup URL with `setStartupUrls(Map<String, URL>)`, then calls `moduleLoadComplete(true)`. Returning from this method proves initialize and capability replies interoperated with the real SDK.
4. `getModuleLogger(...)`, `handle.getLogger().log/branch(...)`, and `handle.unload()` exercise module log allocation, entries, branches, and disconnect. Create another module logger on the same socket to cover session reconnection separately from TCP reconnect.
5. Parent sends the existing restart request; child callback confirms delivery. Close the plugin transport; child Done callback confirms GWT sees the intended shutdown.

Use bounded waits and terminate the child process on failure. GWT's transport owns a non-daemon reader and a daemon writer with no public stop method, so a fork avoids retained SDK threads in the IDE test JVM. The public callback signatures and initialization sequence were checked in embedded `DevModeUI`, `DoneCallback`, and `RestartServerCallback` sources.

## Implemented validation

The plugin-owned `RemoteUiTransport` replaces the internal transport interfaces with JDK sockets, a FIFO writer and exact-type `Consumer` handlers. A list now gives the two initialization handlers deterministic registration order; the former set did not promise order. One connection per run and wildcard binding are preserved. Explicit socket closure makes pending accept/read and writer waits terminate on shutdown. EOF stops I/O separately from owner disposal so final debounced console messages are not cancelled; a console regression covers that boundary.

`RemoteUiTransportTest` exercises fragmented/coalesced frames, multi-consumer dispatch, request IDs and pre-accept FIFO writes, ignored envelopes, EOF and a fresh run on the same port, pending accept/partial-read disposal, malformed/truncated input, bind fallback and synchronous range exhaustion. Existing console checks remain, with added initialization/capability/failure and final-log-on-EOF checks.

`RemoteUiSdkSmokeTest` source-launches the fixture `testData/remoteUi/RemoteUiSmokeClient.java` in separate JVMs using **real GWT 2.12.2 and 2.13.1**. Both passed initialization/capability exchange, module logs, nested error output, same-module session unload/recreation, startup URL display, restart callback and socket-close shutdown callback. This is a real SDK protocol/UI smoke, not a complete DevMode web application compile or a browser debugging session.

Reproduce with locally available SDK JARs (no SDK JAR is bundled or downloaded by the test):

```shell
GWT_REMOTE_UI_SMOKE_JARS=/path/to/gwt-dev-2.12.2.jar:/path/to/gwt-dev-2.13.1.jar \
  ./gradlew --offline --no-daemon --max-workers=1 test --rerun-tasks --tests '*RemoteUiSdkSmokeTest'
```

The environment value uses the platform path separator (`:` on macOS/Linux, `;` on Windows). Without it, the SDK-dependent test reports a skip; normal loopback and console coverage always runs. The explicit rerun avoids reusing a previous Gradle test result when changing only this optional environment input. Full release-gate results are recorded in [the publishing guide](marketplace-publishing.md).
