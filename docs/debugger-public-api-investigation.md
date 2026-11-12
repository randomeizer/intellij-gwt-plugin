# Browser and JavaScript debugger public API migration (#51)

Implemented against IDEA Ultimate **263.7307.200**. Browser startup and Java source-map resolution use public production APIs. The publisher approved concrete local/configured URL aliases with explicit native debugger mappings for unknown external servers. The internal source resolver is removed. Exact native lookup and real Chrome checks cover inherited modules, physical super-source paths, custom ports and explicit external mappings.

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

The initial investigation used exact source/descriptor/bytecode inspection. Startup implementation and the later Chrome smoke are now validated as recorded below. The approved mapping boundary and implementation validation are recorded below.

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


## Completed implementation and validation

`GwtJavaScriptDebugSupport` retains the optional native startup bridge, selected-browser fallback, active-session reuse and saved configuration customizations. `GwtFileUrlMapper` now supplies concrete aliases through public `getUrls`, `getFile` and `getNavigatable`; it no longer implements or returns the Internal SourceFileResolver. All **nine #51 Internal findings are removed**: **12 → 3** overall, **8 → 3** after the startup checkpoint, with no new Internal findings. The remaining three belong to #52.

Aliases include directly owning and transitively inheriting GWT modules with `rename-to` output names. Paths are physical classpath-relative paths for ordinary sources, super-sources and embedded JAR sources. The model exposes super-source membership through the same include/exclude matcher as ordinary sources. Attached source JARs are associated with their own library's compiled GWT resources, and reverse navigation prefers attached sources. Classpath visibility separates duplicate class/module definitions.

`GwtCodeServerUrls` uses default loopback aliases, configured DevMode `-codeServerPort`/`-bindAddress` values, page-host aliases and per-process snapshots. DevMode's `-port` remains the web-server port. A listener reads complete, possibly fragmented ready lines on stdout/stderr and records the actual origin for auto-selected ports. Stopped processes naturally leave the running-process lookup. Runtime log discovery depends on GWT's ready-line format; explicit fixed configuration or native URL mappings are available when an external/version-specific launcher does not expose it.

Fast fixtures exercise native SDK source-map lookup with **both the fallback resolver and source file set to null**, proving exact URL matching. They cover inherited/renamed modules, physical super-source paths/filters, multiple origins, settings refresh, embedded/attached JAR sources, duplicate module classpaths, non-client input, invalid paths and fragmented auto-port discovery. SDK implementation classes used to parse maps in tests are not shipped.

Three real native debugger/headless Chrome checks hit Java breakpoints and verify local file/line plus session reuse: ordinary local sources; inherited super-sources at a configured host/port; and an unknown external origin requiring a native mapping. Map data is synthetic GWT-format version 3. This is real Chrome/native-debugger validation, separate from the real GWT emitted-map probe below and the real JPS/GWT compiler smoke. It does not claim a complete SuperDevMode application smoke across all browsers/servers.

Each Chrome fixture uses a separate heavyweight project, physical source roots, a unique temporary profile and local HTTP server; it restores browser state and closes owned processes. The frontend tab descriptor is captured through test-only `XDebugSessionImpl.getSessionTabIfInitialized()` before stopping the session, then removed/disposed through its owner lifecycle. The owned backend debug process is also disposed; breakpoints are removed before its connection closes. Source navigation is awaited while active, queued work is drained, and opened files close through FileEditorManager. No bare Editor instances are force-released, and these SDK implementation accesses are not packaged.

Full suite/verifier totals are recorded in the Marketplace guide. The strict gate remains active for #52.

```shell
GWT_JS_DEBUG_SMOKE_BROWSER="/path/to/Chrome/executable" \
  ./gradlew --offline --no-daemon --max-workers=1 test --tests '*GwtJavaScriptDebugSdkSmokeTest'
```

Without that input the Chrome fixtures explicitly skip; its executable path is a Gradle input.

## Configure an unknown external code server

This is the publisher-approved behavior change: arbitrary authority-independent suffix matching is replaced with explicit mappings when the endpoint cannot be inferred. Open the generated permanent `<GWT configuration name> JavaScript` configuration, or create a permanent JavaScript Debug configuration with the same page URL/browser. In **Remote URLs of local files**, map the local Java classpath/source root to the external code server's module source root. Example:

| File/Directory | Remote URL |
| --- | --- |
| `/path/to/project/src` | `http://gwt.example:9876/sourcemaps/app` |

Then `/path/to/project/src/com/acme/Probe.java` maps to `http://gwt.example:9876/sourcemaps/app/com/acme/Probe.java`. For a super-source, retain its physical prefix beneath the selected local root. Native mappings take precedence over automatic aliases, and startup reuses matching saved configurations without replacing their mappings. The native UI supports one remote base per local root per debug session; use separate configurations for different remote module roots. [JetBrains configuration reference](https://www.jetbrains.com/help/idea/run-debug-configuration-javascript-debug.html), [mapping instructions](https://www.jetbrains.com/help/idea/debugging-javascript-in-chrome.html).

## GWT 2.13.1 URL contract and approved alias scope

The user approved completing the resolver migration with automatic local/configured code-server aliases and explicit native JS configuration mappings for unknown external servers. The following evidence defines that implementation; it does not claim the alias migration is already validated.

GWT **2.13.1** is pinned by upstream tag to commit `9483b1b89e181cc03b83d81c862660af0e6d523d`. Exact source was checked both through that tag and the embedded `.java` files in `/private/tmp/gwt213-sdk-probe/repo/org/gwtproject/gwt-dev/2.13.1/gwt-dev-2.13.1.jar`.

Source-map filenames are physical **classpath-relative** resource paths. `CompilationUnitBuilder.getSourceMapPathFor` returns `resource.getPathPrefix() + resource.getPath()`. For a super-source resource, ResourceOracleImpl.RerootedResource strips the super-source prefix from logical getPath but returns that stripped prefix from getPathPrefix, restoring the physical path in source maps. CompilationStateBuilder passes that map path to GwtAstBuilder; SourceMappingWriter records SourceInfo.getFileName. Therefore a physical `com/acme/super/java/lang/Foo.java` stays that complete path, not `java/lang/Foo.java`. Sources: [CompilationUnitBuilder](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/javac/CompilationUnitBuilder.java#L238), [ResourceOracleImpl](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/resource/impl/ResourceOracleImpl.java#L67), [CompilationStateBuilder](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/javac/CompilationStateBuilder.java#L126), [SourceMappingWriter](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/core/ext/soyc/SourceMappingWriter.java#L63).

Library sources use their JAR/classpath entry paths, with no additional library or `src/` segment. SourceHandler dispatches through the output module name; Outbox.openSourceFile resolves regular paths with the compiler resource loader. Generated files are explicitly different: they use `gen/<qualified-type-path>.java`. Sources: [SourceHandler](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/SourceHandler.java), [Outbox](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/Outbox.java#L255), [generated source path](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/javac/StandardGeneratorContext.java#L176).

SourceHandler.makeSourceMapPage substitutes the sourceRoot at request time as `http://<request.getServerName()>:<request.getServerPort()>/sourcemaps/<output-module-name>/`. It deliberately uses the incoming request hostname because reverse proxies can change the visible server. Recompiler uses the preferred host/port for the initial absolute source-map directive. This supports known bind/connect endpoints and request-host aliases; a page hostname cannot establish an unknown external code-server authority. Sources: [runtime sourceRoot](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/SourceHandler.java#L145), [absolute map directive](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/Recompiler.java#L504).

| Launcher | Code-server port | Other host/port behavior |
| --- | --- | --- |
| Plugin-launched `com.google.gwt.dev.DevMode` with SuperDevMode | `-codeServerPort <number>`; default SDM 9876 | `-port` is the separate embedded web server, default 8888. Never treat it as the code-server port here. |
| Standalone `com.google.gwt.dev.codeserver.CodeServer` | `-port <integer>`; default 9876 | Different CLI contract; this is not the plugin's current main class. |
| DevMode `-codeServerPort auto` or `0` | SuperDevListener selects an available port at runtime | Source cannot infer the allocated port from persisted flags; observe the owned process's ready URL or resolve the allocation before launch. |
| DevMode SDM negative port or port 9997 | SuperDevListener substitutes 9876 | 9997 is the classic DevMode default; it is translated even if explicitly supplied. |

`-bindAddress <host-name-or-address>` defaults to `127.0.0.1` and is forwarded to the embedded SDM CodeServer. A concrete value is also the preferred connect hostname. For wildcard bind addresses, ArgHandlerBindAddress replaces the connect host with InetAddress.getLocalHost().getHostAddress(); the wildcard itself is not a useful browser destination. The inspected owning argument/options/source-handler classes expose no Java system-property override for host/port. An embedded-source scan found no host/port property reader to justify inventing support for `-Dgwt.codeserver.host` or similar names. Sources: [DevModeBase port handlers](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/DevModeBase.java#L104), [SuperDevListener port selection/argument forwarding](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/shell/SuperDevListener.java#L119), [standalone Options](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/Options.java#L78), [bind/connect handler](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/util/arg/ArgHandlerBindAddress.java#L58).

For runtime port discovery, DevMode's SuperDevListener logs resolved CodeServer arguments, and CodeServer prints `The code server is ready at http://<preferred-host>:<resolved-port>/`. The existing plugin process listener already reacts to that ready message. An endpoint registry may observe that same owned process output, retain the resolved host/port while it runs, and unregister it on termination. This is an observed GWT output contract rather than a typed platform API. Preserve complete output lines across chunk boundaries and do not accept unrelated process messages. Source: [SuperDevListener](https://github.com/gwtproject/gwt/blob/2.13.1/dev/core/src/com/google/gwt/dev/shell/SuperDevListener.java#L95), [CodeServer ready message](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/CodeServer.java#L105).

A real cached-SDK compile independently verified source paths. Temporary fixture `/private/tmp/gwt51-map-probe` ran CodeServer with `-compileTest -noincremental -sourceLevel 17 -workDir <temp>/work -logLevel WARN Probe`, using a module with a super-source and a separate source-bearing library JAR. The emitted map under `work/Probe/compile-1/extras/probe/symbolMaps/*_sourceMap0.json` contains all three expected sources: `client/Probe.java`, `library/client/Library.java`, and `super/probe/Shared.java`. GWT's JRE super-sources likewise appear as `com/google/gwt/emul/java/lang/Object.java` and related physical entries. sourceRoot remains `$sourceroot_goes_here$` until SourceHandler serves the map. Compilation exited 0; the Core-only fixture emitted harmless undefined locale/user.agent warnings. `-noincremental` matters: standalone CodeServer's default incremental mode disables precompile, so its compile-test path can report PASS without emitting maps. Source: [Options.processArgs](https://github.com/gwtproject/gwt/blob/2.13.1/dev/codeserver/java/com/google/gwt/dev/codeserver/Options.java#L126), plus inspected emitted JSON.

Repository implementation implications: discover super-source membership explicitly because current GwtModule.isSourceFile/findGwtModulesByClientSourceFile check ordinary source roots only. Reuse GwtModuleImpl's include/exclude/defaultExcludes/caseSensitive logic for super-source membership; ancestry alone would ignore patterns. Build aliases relative to the physical IDE classpath/source/library root, and resolve reverse source URLs to those physical files before attempting a logical-class-name fallback. The former findClass-only conversion treated the physical super-source prefix as part of the Java package. Register known endpoints from effective launch arguments after server patching; `myPatchedShellParametersList` is copied into JavaParameters and is not itself the final launcher argument list. Sources: repository GwtModuleImpl, GwtModulesManagerImpl, GwtFileUrlMapper, and GwtCommandLineState inspected in this investigation.


## Deferred execution-position navigation in smoke teardown

A mapped breakpoint also schedules opening the Java source editor independently of console ownership. In this SDK, XDebuggerExecutionPointManagerImpl.setExecutionPoint launches OPEN navigation on the project service coroutine scope while capturing the point VM. Replacing/clearing the point cancels the point VM child scope, but that OPEN launch belongs to the enclosing service scope. ExecutionPositionNavigator.navigateTo then suspends while obtaining its OpenFileDescriptor through readAction before opening the editor on EDT. Consequently an immediate closeAllFiles after session.stop can race a queued navigation that opens the file later. Sources: [execution-point manager](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/xdebugger-impl/ui/src/com/intellij/xdebugger/impl/XDebuggerExecutionPointManagerImpl.kt), [navigator](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/xdebugger-impl/ui/src/com/intellij/xdebugger/impl/ExecutionPositionNavigator.kt).

The fixture finishes the expected native source navigation while its session is active, then stop the session, dispose its frontend owner, drain queued work, and closes source files through FileEditorManager.getOpenFiles()/closeFile(). TestEditorManagerImpl.closeAllFiles delegates to its owned closeFile lifecycle, which disposes FileEditor providers and releases their Editors; the test must not bypass that owner with bare editor releases. The SDK EditorOpenTestUtil barriers explicitly exclude direct legacy open calls in light tests, so they do not alone prove this XDebuggerUtil legacy navigation has finished. Sources: [TestEditorManagerImpl](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/lang-impl/src/com/intellij/openapi/fileEditor/impl/TestEditorManagerImpl.kt), [EditorOpenTestUtil](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/platform/testFramework/src/com/intellij/testFramework/EditorOpenTestUtil.kt). The heavyweight Chrome fixtures now follow this lifecycle. Full validation passes 245 normal and 50 free-mode tests, with no failures/errors/skips when all optional SDK and browser inputs are supplied; check/build/structure pass and strict verification retains only the three #52 findings.
