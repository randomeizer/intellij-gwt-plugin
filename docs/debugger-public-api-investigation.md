# Browser and JavaScript debugger public API migration (#51)

Investigated against IDEA Ultimate **263.7307.200**. Browser launch and debugger startup now use the public replacement, and an isolated native Chrome smoke passes through Java source-map breakpoint binding and session reuse. Replacing the source resolver still requires an agreed mapping scope: simply deleting it loses inherited-module resolution. The existing source resolver is retained pending that decision.

## API status evidence

Public Java visibility alone is insufficient. JetBrains states that Internal elements must not be consumed by plugins and that annotations, including Experimental status, affect compatibility expectations. Sources: [Internal API migration](https://plugins.jetbrains.com/docs/intellij/api-internal.html), [2026 API compatibility](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html).

Matching-tag source and `javap -p -v` on the installed SDK establish:

| Candidate | Exact status and permitted use |
| --- | --- |
| `JavaScriptDebuggerStarter` | Outer interface itself is `@ApiStatus.Internal`. Its public `isApplicable` and `start` methods do not make it consumable. `Util` is nested under this Internal interface; its `EP_NAME` is package-private. Reimplementing dispatch against the same interface is not a public solution. |
| `BrowserLauncher` | Public unannotated abstract API; `getInstance().browse(String, WebBrowser, Project)` is public and unannotated. Consume the service; do not instantiate Internal `BrowserLauncherAppless` or implementation classes. |
| `JavaScriptDebugConfiguration` | SDK class, constructor, `getUri`/`setUri`, `getEngineId`/`setEngineId` have no Internal, Experimental, TestOnly, OverrideOnly, or NonExtendable restriction. |
| `JavascriptDebugConfigurationType` | SDK class and `getTypeInstance` have none of those restrictions. It extends public `SimpleConfigurationType`, which is also its configuration factory. |
| `FileUrlMapper` | SDK class, constructor, `getUrls`, `getFile`, `getNavigatable`, and `EP_NAME` have none of those restrictions. Its extension declaration is dynamic and has no Internal marker. |
| `FileUrlMapper.createSourceResolver` | Method is unannotated, but the returned `SourceFileResolver` interface is explicitly Internal. Implementing or mentioning that type retains private API usage. |
| `RunManager` | Use public `getInstance`, `createConfiguration`, `getConfigurationSettingsList`, `addConfiguration`; avoid its separately Internal members. `getInstance` requires blocking context in this SDK. |
| `ProgramRunner` / `ProgramRunnerUtil` | `ProgramRunner.getRunner` and `ProgramRunnerUtil.executeConfiguration(settings, executor)` are public unannotated entry points. Do not use deprecated `ProgramRunnerUtil.getRunner`. |
| `XDebuggerManager` / `XDebugSession` | Both are NonExtendable: consumption is allowed, implementation/subclassing is not. Public `getDebugSessions`, `getRunProfile`, and `AbstractDebuggerSession.isStopped` can identify a matching active JS configuration without consuming a private debugger implementation. |

Sources: matching-tag [starter](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/execution-impl/src/com/intellij/ide/browsers/JavaScriptDebuggerStarter.java), [browser launcher](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/platform-api/src/com/intellij/ide/browsers/BrowserLauncher.kt), [source resolver](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/script-debugger/backend/src/debugger/sourcemap/SourceResolver.kt), [run manager](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/execution/src/com/intellij/execution/RunManager.kt), [program runner utility](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/execution-impl/src/com/intellij/execution/ProgramRunnerUtil.java); SDK binary evidence below for proprietary JavaScript classes. A full SDK JAR scan found no `package-info.class` on the candidate packages or enclosing package chain that adds a restriction. Unrelated `browsers.impl` and execution subpackages do have package annotations and are not used.

## Browser and debugger implementation shape

Keep optional debugger integration behind a plugin-owned optional implementation, so base run configuration/editor classes load when the JavaScript Debugger plugin is absent. Existing optional descriptor: [`resources/META-INF/javascript-debugger.xml`](../resources/META-INF/javascript-debugger.xml). Additional plugin descriptors are the platform-supported optional-feature mechanism: [plugin configuration documentation](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#additional-plugin-configuration-files).

For ordinary launch or missing debugger capability, call `BrowserLauncher.getInstance().browse(url, selectedBrowser, project)`. That is exactly the fallback used by the old utility. For debugging, create or reuse `RunnerAndConfigurationSettings` for `JavascriptDebugConfigurationType`, cast to `JavaScriptDebugConfiguration`, set URI to the startup URL and engine ID to the selected browser UUID string, then invoke `ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())`. Check `ProgramRunner.getRunner(DefaultDebugExecutor.EXECUTOR_ID, configuration)` before accepting startup. This delegates actual browser/debugger behavior to the native JS runner rather than launching a browser with hand-built debug flags. Public execution architecture: [Execution API](https://plugins.jetbrains.com/docs/intellij/execution.html); concrete mechanism: SDK `BaseJavaScriptDebuggerStarter`, `JavaScriptDebugConfiguration`, and `JavaScriptDebugRunner` bytecode.

Preserve native behavior deliberately: the old starter checks active sessions for matching URL/browser, then searches saved configurations, creates a missing configuration, adds it to RunManager for the default starter, and executes it. A new helper can scan public session/run-manager APIs for the same matches. Skipping these checks changes repeat-start behavior and discards saved configuration customizations. The existing per-connection AtomicBoolean still prevents duplicate initial browser opening. `setEngineId(browser UUID)` is confirmed by the old starter bytecode; despite the method name, passing a guessed engine identifier would be wrong.

For editor capability, use the plugin-owned optional implementation to check the registered native configuration type/runner, instead of `Util.hasStarters()`. The old method means any starter extension exists, whereas a native-configuration check means the actual supported native JS debugger path is available. Do not query `org.jetbrains.javaScriptDebuggerStarter` via a string and cast back to Internal types. A third-party-only custom starter is not automatically preserved by the native replacement; retaining arbitrary such integrations would require a public upstream contract.

## Moving source resolution into URLs

SDK `DebuggableFileFinderImpl.findRemoteUrlByExtensionMappers` collects all `FileUrlMapper.getUrls` results using the current debug requestor authority. `getRemoteUrls` returns those aliases if present. SDK `SourceMapHelperKt.getRawLocations` iterates the remote source URLs and calls source-map lookup for each. Matching-tag `SourceMapBase.findSourceIndex` first attempts canonical URL/file equality; the custom resolver is only the fallback when equality fails. Sources: [SourceMap.kt](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/script-debugger/backend/src/debugger/sourcemap/SourceMap.kt), [SourceResolver.kt](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/script-debugger/backend/src/debugger/sourcemap/SourceResolver.kt), SDK finder/helper bytecode.

Therefore the public replacement is to emit all real GWT aliases, not to access `SourceMap`, `SourceResolver`, or their Internal lookup helpers from plugin code:

- Include each directly owning GWT module plus inheriting modules, using each module output name (`rename-to` included). B-source compiled through A must include `/sourcemaps/A/<source-path>`.
- Preserve source-root-relative paths and verify actual code-server paths for super-source files. Super-source roots and ordinary source roots are separate in the plugin model; do not silently strip a super-source prefix based on Java package names.
- Support valid code-server authorities/ports explicitly, including localhost defaults, configured/running custom ports, and useful requestor aliases. The web-page authority can differ from the code-server authority; currentAuthority alone is insufficient. This is confirmed: `JavaScriptDebugRunner.startSession` parses the debug configuration URI (the page URL) and constructs `DebuggableFileFinderImpl(project, pageUrl)`; that constructor stores `pageUrl.authority`, which is later passed to `getUrls`. It does not substitute the source-map/script URL authority. An authority-null URL is not a wildcard in the exact URL-key map. `getFile` can observe real source URLs, but no inspected contract guarantees that callback runs before initial breakpoint resolution, so an observation cache alone does not prove parity.
- Handle attached library/source JAR roots explicitly if those cases are in scope. Existing getUrls rejects JAR roots. The old fallback does not explicitly reject JAR roots but then calls `isUnderGwtModule`, which climbs only project source roots, so existing support for ordinary library JARs is unproven.
- Deduplicate aliases and build a growable list/set. Existing fixed array indexes by module only while looping ports, overwriting entries and potentially returning null slots. Its private PORT_KEY is never written in current source, so it does not currently register custom running ports.

Repository evidence: [`GwtFileUrlMapper`](../src/org/randombits/intellij/gwt/run/GwtFileUrlMapper.java), [`GwtModulesManagerImpl`](../src/org/randombits/intellij/gwt/module/GwtModulesManagerImpl.java), [`GwtModuleImpl`](../src/org/randombits/intellij/gwt/module/model/impl/GwtModuleImpl.java). `getAllGwtModules()` searches all scope but filters source/library-class entries; `findGwtModulesByClientSourceFile()` only discovers ancestor-local owning modules. `isInheritedOrSelf()` supplies the transitive module relationship. Source-only library attachments need verification against these filters.

The old suffix resolver accepted any canonical URL ending in the source-root-relative path at a slash boundary, independently of authority and module prefix. A finite concrete alias set is not mathematically equivalent to that unconstrained suffix match. For supported GWT code-server URLs, comprehensive alias generation can preserve behavior. Arbitrary/proxied authorities need an explicit configuration source, a tested runtime discovery source, or upstream API support. Do not claim complete parity before those cases are decided and tested.

## Validation needed for the implementation

The initial investigation used exact source/descriptor/bytecode inspection. Startup implementation and the later Chrome smoke are now validated as recorded below. Source-resolver migration remains a separate pending scope decision; completed validation is recorded below.

Existing relevant tests are `GwtOptionalIntegrationTest`, `GwtRunConfigurationStateTest`, `GwtSuperDevModeUrlBuilderTest`, `GwtDevModeServerTest`, `GwtCommandLineStateTest`, `RemoteUiTransportTest`, `RemoteUiSdkSmokeTest`, and `GwtSuperSourceElementFinderTest`. None currently directly tests `GwtFileUrlMapper` or actual debugger launch. `GwtOptionalIntegrationTest` currently checks loading the Internal SourceFileResolver class and should instead check the public optional bridge/native configuration types.

Add focused behavioral coverage for native configuration URL/browser selection, reuse/persistence, missing-native-debugger browser fallback, exactly-once initialize opening, direct and transitive inherited modules with rename-to, multiple non-default ports with no nulls, separate page/code-server authorities, local/JAR sources, super-source paths, duplicate class names, non-GWT sources, reverse URL navigation, and dumb-mode behavior. Exercise exact aliases through the SDK-owned source-map lookup in test fixtures; testing only the generated strings does not prove debugger binding. Then run normal tests, both edition verifier checks with zero new Internal findings, and an interactive Chrome/GWT breakpoint smoke test before claiming debugger parity. SDK-internal test calls are research/fixture evidence, not a production dependency.

## Exact SDK provenance and reproduction

SDK root used:

```text
/Users/david/.gradle/caches/9.3.1/transforms/c966849d6ed291580f0545dcbdf20e80/transformed/idea-2026.3-aarch64
```

Relevant primary binary artifacts beneath that root:

- `lib/intellij.platform.execution.impl.jar`: JavaScriptDebuggerStarter, ProgramRunnerUtil.
- `lib/intellij.platform.ide.jar`: BrowserLauncher.
- `lib/intellij.platform.scriptDebugger.backend.jar`: SourceFileResolver/SourceMap implementation.
- `lib/intellij.platform.execution.jar`: RunManager, ConfigurationType, ProgramRunner.
- `lib/intellij.platform.debugger.jar`: XDebuggerManager/XDebugSession.
- `plugins/javascript-debugger/lib/modules/intellij.javascript.debugger.ui.jar`: FileUrlMapper.
- `plugins/javascript-debugger/lib/modules/intellij.javascript.debugger.backend.jar`: JavaScriptDebugConfiguration, JavascriptDebugConfigurationType, BaseJavaScriptDebuggerStarter, JavaScriptDebugRunner, DebuggableFileFinderImpl, SourceMapHelperKt, native extension descriptor.

Inspect class/method/annotation evidence with `javap -p -v -classpath <owning-jar> <fully-qualified-class>` and implementation flow with `javap -p -c`; inspect `intellij.javascript.debugger.backend.xml` inside the backend JAR for the public mapper extension declaration. No decompiled proprietary source is copied into this repository.

Google Chrome is installed at `/Applications/Google Chrome.app`. The later native debugger/Chrome fixture is recorded below.

SHA-256 of inspected class bytes (reproducible with ZIP extraction plus SHA-256):

| Class | SHA-256 |
| --- | --- |
| JavaScriptDebugConfiguration | `4a4cc538dbe6911765d238cb5c81baee6f991b1d22f0142269c7518426b6887f` |
| JavascriptDebugConfigurationType | `3d7ce3f3fc31f1fca844b99d4b9fc3df7bf325088dfa0efa50f30b64e1a39eee` |
| BaseJavaScriptDebuggerStarter | `dc5355827c0c77a28d11309102eae08b59e0c8659283843099f56bf434ea13f7` |
| DebuggableFileFinderImpl | `d69df4c86d1e5517750334eebb227e6d317cceb572383b26f9a72c511dc10e7e` |
| SourceMapHelperKt | `29f563b394f4dd811b689a56f511562a54eb912e2f2469d94e2052911b25a7dc` |
| FileUrlMapper | `ea0baacefb96b066094641dbbc8147cce9800d8d52e77746cae60aa05a5462c6` |


## Native remote URL mappings

`JavaScriptDebugConfiguration.getMappings`/`setMappings` and `RemoteUrlMappingBean(String localFilePath, String remoteUrl)` are public without Internal/Experimental/TestOnly/OverrideOnly/NonExtendable restrictions in the inspected SDK. The constructor assigns its first argument to the local path and passes the second to the remote URL setter.

A saved native JS configuration can map an external code server without adding production private API calls:

```java
debug.setMappings(List.of(new RemoteUrlMappingBean(
  sourceRoot.getPath(), "https://external-code.example:9443/sourcemaps/App")));
```

For `/src/com/acme/Foo.java` under mapped `/src`, this gives `https://external-code.example:9443/sourcemaps/App/com/acme/Foo.java`. SDK `JavaScriptDebugRunner.startSession` constructs a `RemoteDebuggingFileFinder` around the default extension mapper finder. Its forward `getRemoteUrls` checks the file then its ancestors in the inverse manual mapping; the first match returns immediately. Only absent a manual mapping does it delegate to the default finder and `FileUrlMapper.getUrls`. Thus explicit native mappings take precedence over plugin aliases. Reverse source navigation also consults the remote-to-local mappings.

The native table uses a BiMap and `forcePut`, so a local root has one remote base in a session. This supports a selected active output module; it cannot independently assign many different remote module bases to that same root. Requiring a user mapping where the old resolver matched any authority automatically is a behavior change and must be agreed explicitly. Evidence: SDK `RemoteUrlMappingBean`, `RemoteDebuggingFileFinder`, `RemoteDebuggingFileFinderKt`, and `JavaScriptDebugRunner` in `intellij.javascript.debugger.backend.jar`; [native configuration documentation](https://www.jetbrains.com/help/idea/run-debug-configuration-javascript-debug.html).

## Isolated real Chrome smoke shape

The fixture described here is now implemented and its native Chrome smoke passes, as recorded below. The machine has `/Applications/Google Chrome.app/Contents/MacOS/Google Chrome`. Current Chrome supports headless execution without visible UI: [Chrome Headless documentation](https://developer.chrome.com/docs/automation-and-testing/headless).

Use a new browser UUID and a fresh temporary profile in the test IDE application. Save and restore the browser manager state in `finally`:

```java
WebBrowserManager manager = WebBrowserManager.getInstance();
Element savedBrowserState = manager.getState().clone();
ChromeSettings chrome = new ChromeSettings();
chrome.setUseCustomProfile(true);
chrome.setUserDataDirectoryPath(profileDirectory.toString());
chrome.setCommandLineOptions(
  "--headless --remote-debugging-port=0 --no-first-run --no-default-browser-check");
WebBrowser browser = manager.addBrowser(
  UUID.randomUUID(), BrowserFamily.CHROME, "GWT debugger smoke",
  "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome", true, chrome);
// ... use the public startup bridge with this selected browser ...
// finally: stop owned sessions, wait/clean owned processes, then:
manager.loadState(savedBrowserState);
```

The ChromeSettings constructor/setters and `WebBrowserManager.addBrowser` are public unannotated API in both source and bytecode. `ConfigurableWebBrowser` constructors are package-private; use the public manager factory. Sources: [ChromeSettings](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/platform-api/src/com/intellij/ide/browsers/chrome/ChromeSettings.java), [WebBrowserManager.addBrowser](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/platform-impl/src/com/intellij/ide/browsers/WebBrowserManager.java#L379).

Let the native runner launch Chrome. Do not first launch the same custom profile with ProcessBuilder and assume the native runner attaches: the default new launcher checks the profile lock and may ask whether an instance is running before starting another process. That creates an unnecessary modal-dialog path. The installed backend descriptor sets registry `js.debugger.browser.split=true` by default. `ChromeLocalDebuggerEngine` consequently uses its RPC connection and the common `TcpBrowserLauncher`. The launcher uses `ChromeSettings` custom profile before considering `isUseTemporaryUserDataDir`; a custom fresh profile is deterministic regardless of that flag. It parses the selected transport, filters duplicate transport/profile arguments, then adds its own profile and port flags. Port 0 is accepted for automatic allocation.

Process lifetime is distinct from profile selection. `TcpBrowserLauncher` wraps its child in `WithProcessKt.withProcess`, whose success and failure paths enter a NonCancellable `finally`. That path calls `ProcessUtilsKt.terminateGracefully` on its own KillableProcessHandler if it is still alive. This termination applies irrespective of the temporary-profile flag. The old nondefault connection path instead has a separate `js.debugger.chrome.termination.policy` setting; do not infer identical lifecycle for it. These are SDK implementation observations, not plugin APIs to invoke: `ChromeLocalDebuggerEngine`/`ChromeLocalVmConnection` in the backend JAR; `TcpBrowserLauncher`, `WithProcessKt`, and `ProcessUtilsKt` in `intellij.javascript.debugger.common.jar`; default registry declaration in `intellij.javascript.debugger.backend.xml`.

For a failure-safe fixture, snapshot only `ProcessHandle.current().descendants()` and capture handles whose argument array contains the exact `--user-data-dir=<unique-profile-directory>` argument, including their start instants. Record those roots and their descendants while alive. Stop the owned XDebugSession first and wait with a deadline; if it leaves survivors, terminate only captured owned handles with unchanged start instants, then force those same handles if necessary. Never terminate by the generic Chrome process name or scan/kill a shared debugging port. Keep the fixture HTTP server, profile, breakpoint, saved JS configuration, and browser-manager state in the same `finally` cleanup. Chrome startup should be observed before asserting the native cleanup behavior.

A minimal breakpoint scenario can avoid timing a one-shot script:

1. Create a real fixture Java source root containing an indexed GWT module XML and a Java class under its client path. Add a JavaLineBreakpoint with public XBreakpointManager.addLineBreakpoint and the Java breakpoint type from the extension list.
2. Serve a loopback HTML page, a small script with a repeated timer callback, and its version-3 source map. The map source must equal a concrete URL returned by GwtFileUrlMapper for that Java file; a fixture source URL need not be fetched if it resolves locally. Embed source content as well. Use distinct page/code-server-style authorities when covering alias behavior.
3. Map the repeatedly executed generated statement to the Java breakpoint line. For example, a generated statement on zero-based line 1 mapping to source index 0, Java zero-based line 3, column 0 can use mappings `;AAGA`. Source-map test generation should encode/check the chosen zero-based coordinates explicitly.
4. Start through the actual public plugin bridge with the isolated browser. Wait while pumping IDE events; accept success only when a matching actual XDebugSession is suspended and its current source position is the expected Java file and line. A session existing or an HTTP request alone does not prove breakpoint binding.
5. Call the bridge again with the same URL/browser while suspended and assert no second matching session/configuration; then stop it and verify both session and captured Chrome process termination.

The timer avoids a race between initial navigation, source-map loading, and asynchronous Java breakpoint installation. This is a Chrome/native-debugger/source-map integration fixture, not proof that real GWT compiler output was exercised. A real GWT application remains a separate smoke qualification. The fixture may use SDK implementation classes solely to inspect test behavior if unavoidable; production must retain the public dependency boundary.


## Split debugger console ownership during test cleanup

In SDK 263, the public session descriptor is a backend mock. `XDebugSessionImpl.getMockRunContentDescriptorIfInitialized` explicitly says that descriptor is not registered in RunContentManager and directs UI descriptor access through `XDebugSessionProxy.sessionTab`. Independently, `XDebugSessionTab.init` creates the frontend descriptor with the session console and registers that descriptor under the project. RunContentDescriptor constructors register their execution console as a disposable child; creating the frontend descriptor can therefore reparent a shared local console away from the backend mock. Sources: matching-tag [XDebugSessionImpl.kt](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/xdebugger-impl/src/com/intellij/xdebugger/impl/XDebugSessionImpl.kt), [RunContentDescriptor.java](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/execution/src/com/intellij/execution/ui/RunContentDescriptor.java), SDK `XDebugSessionTab.init` bytecode in `intellij.platform.debugger.impl.ui.jar`.

The test-only local accessor is `MonolithSessionProxyKt.asProxy(session).getSessionTab()`. When the resulting IXDebuggerSessionTab is an XDebugSessionTab, its `getRunContentDescriptor()` is the UI descriptor to remove/dispose. Capture it before cancelling the backend tab scope, and wait/pump for asynchronous tab initialization if needed. The nullable monolith accessor delegates to `sessionImpl.sessionTabIfInitialized` without the split-warning path of the legacy sessionTab getter. Source: matching-tag [MonolithSessionProxy.kt](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/xdebugger-impl/src/com/intellij/xdebugger/impl/proxy/MonolithSessionProxy.kt).

A frontend descriptor may exist before it has been shown, so an empty RunContentManager.getAllDescriptors list does not by itself establish that no frontend descriptor owns the console. If cleanup still leaks, `Disposer.getTree().printParentChainToRoot(console)` and `Disposer.getRegistrationTrace(console)` are precise test-only probes to establish the current owner. Dispose the owned console/descriptor through its lifecycle; releasing individual leaked Editor instances would hide ownership failures. These Internal implementation probes belong only in test investigation, never shipped production code.


## Implemented startup and runtime validation

The public browser/debugger startup bridge is implemented. The startup slice removes four verifier Internal API findings, reducing the checked total from **12 to 8**. The remaining source-resolver work is retained pending agreement on automatic local/configured aliases versus explicit native mappings for external code-server authorities; this result does not claim issue #51 is complete.

The isolated native Chrome smoke passes in `GwtJavaScriptDebugSdkSmokeTest.testNativeChromeStopsAtJavaSourceMapBreakpoint`: native debugger/browser startup succeeds, the actual XDebugSession suspends at the expected Java VirtualFile and zero-based source line, and repeated startup reuses the active session. Its synthetic GWT-format version-3 map exercises the existing GwtFileUrlMapper and Java breakpoint integration. This is real Chrome and the native JetBrains debugger, with synthetic map data; it is not a real-GWT-compiler smoke.

Runtime evidence: `build/test-results/test/TEST-org.randombits.intellij.gwt.run.GwtJavaScriptDebugSdkSmokeTest.xml` records one test, zero failures/errors/skips. The fixture uses a dedicated temporary browser profile and restores browser-manager state. Cleanup now captures the frontend descriptor through test-only `XDebugSessionImpl.getSessionTabIfInitialized()` before stopping the session, then removes/disposes that frontend owner. This resolves the console-editor leak through the SDK ownership lifecycle; it neither releases bare Editor instances nor force-disposes the console independently. The test-only implementation-class access is not part of shipped plugin code.

Full startup-checkpoint validation passes **234 normal + 50 free-mode tests** with no failures, errors or skips when all local SDK and Chrome inputs are supplied. `check`, `buildPlugin` and `verifyPluginStructure` pass. Plugin Verifier reports binary compatibility, no new Internal findings and **8 retained Internal usages** (five source-resolver, three language-level); the strict gate still fails on those. Maintenance counts remain 59 deprecated, 8 scheduled-removal and 68 experimental usages.

Run the isolated Chrome fixture with:

```shell
GWT_JS_DEBUG_SMOKE_BROWSER="/path/to/Chrome/executable" \
  ./gradlew --offline --no-daemon --max-workers=1 test --tests '*GwtJavaScriptDebugSdkSmokeTest'
```

Without that input, the Chrome fixture explicitly skips. The executable path is a Gradle test input. The real GWT 2.12.2/2.13.1 RemoteUI and GWT 2.13.1/JPS upgrade/build smokes also pass in the full run; see the Marketplace guide for their inputs.
