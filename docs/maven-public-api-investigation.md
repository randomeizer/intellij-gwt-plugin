# Maven public API investigation (#48)

Investigated 2026-11-12 against the **actual IDEA 263.7307.200 SDK** used by this checkout. The public-API implementation and validation are summarized below.

## Result

The two JPS descriptor findings have a direct public replacement with equivalent behavior. The six Maven-import findings can be replaced without dropping GWT dependency downloads or Codehaus source jars: use the public resolved-project topic, acquire a Maven-native embedder through the existing third-party API, and add the resulting source artifacts through the importer root model. The material qualification is that JetBrains marks the embedder manager **`@ApiStatus.Obsolete`**. It is public, explicitly retained for third-party plugins, and not `Internal`; it is not a newly recommended modern API. No equally capable modern public acquisition API was established.

If the acceptance criterion excludes obsolete APIs as well as internal APIs, this investigation does **not** establish a feature-preserving replacement. That additional restriction needs a scope decision or an upstream public API. Merely deleting the contributor or switching Maven imports to generic IDE repositories would lose behavior.

## Exact SDK evidence

SDK directory:

```text
/Users/david/.gradle/caches/9.3.1/transforms/c966849d6ed291580f0545dcbdf20e80/transformed/idea-2026.3-aarch64
```

The owning artifacts are `plugins/maven-plugin/lib/intellij.maven.jar`, `plugins/maven-plugin/lib/maven-server.jar`, `plugins/java/lib/modules/intellij.java.jar`, `plugins/java/lib/modules/intellij.java.ui.jar`, and `lib/jps-model.jar`. Signatures, method bodies, and runtime-invisible annotations were inspected with `javap -p -v -c -classpath <owning jars> <class>`. The local binary is the authority for availability and annotations; linked upstream source corroborates behavior but is not claimed to be the exact SDK revision.

| API | Exact 263 status / behavior |
| --- | --- |
| `MavenProjectResolutionContributor` | Class is `@ApiStatus.Internal`; its extension point is also documented as internal. |
| `MavenImporter.resolve` / `FacetImporter.resolve` | **Absent**, including from the superclass. The plugin's `GwtFacetImporter.resolve(...)` is an ordinary plugin method, not a platform override. |
| `MavenProjectsTree.Listener.projectsResolved(List<MavenProject>)` and `TOPIC` | Public; neither listener class nor callback/topic has `Internal`. |
| `MavenProjectsManager.getEmbeddersManager()` | Public, no `Internal` annotation. |
| `MavenEmbeddersManager` | Class has `@ApiStatus.Obsolete`, no `Internal`; public `getEmbedder(...)`, `release(...)`, and `FOR_DEPENDENCIES_RESOLVE` are available. |
| `MavenRootModelAdapter.addLibraryDependency(...)` | Public; class and method have no `Internal`. |
| `RepositoryLibraryProperties(String, String, String)` | Public; no `Internal`; enables transitives and supplies an empty exclusions list. |
| `JarRepositoryManager.loadDependenciesModal(Project, RepositoryLibraryProperties, boolean, boolean, String, Collection)` | Public; no `Internal`; delegates to the same descriptor overload used today. |

JetBrains documents the contributor extension point as internal in its [extension point list](https://plugins.jetbrains.com/docs/intellij/intellij-community-plugins-extension-point-list.html). This classification is independent of the JVM `public` modifier.

## Preserve Maven import behavior

### Callback timing is real; the old resolve method is not a callback

In this SDK, `MavenProjectResolver.collectProjectWithUnresolvedPlugins(...)` invokes the resolution contributors after updating each `MavenProject`. `doResolve(...)` awaits those jobs and then calls `tree.fireProjectsResolved(...)`. The tree method uses `project.getMessageBus().syncPublisher(Listener.TOPIC).projectsResolved(projects)`. The event therefore runs synchronously before project resolution returns. These calls are present in exact-SDK bytecode and agree with the [upstream resolver source](https://github.com/JetBrains/intellij-community/blob/master/plugins/maven/src/main/java/org/jetbrains/idea/maven/project/MavenProjectResolver.kt).

Subscribe a project listener using `MavenProjectsTree.Listener.TOPIC`, declaratively or via a disposable message-bus connection. `MavenProjectsManager.addProjectsTreeListener(...)` is public but deprecated and just subscribes to that same topic, so direct topic registration is preferable. Download work must finish before returning from `projectsResolved`; starting an independent asynchronous job would introduce an import race.

The existing main descriptor loads `gwt-maven-support.xml` through an optional dependency on `org.jetbrains.idea.maven`. Keep the new listener registration and its Maven-dependent service there. Use `<projectListeners><listener class="..." topic="org.jetbrains.idea.maven.project.MavenProjectsTree$Listener"/></projectListeners>` and a constructor accepting `Project`. Official [listener documentation](https://plugins.jetbrains.com/docs/intellij/plugin-listeners.html) specifies lazy creation on first topic publication and supports that constructor; the [optional dependency documentation](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html#optional-plugin-dependencies) defines the dependency-gated descriptor. This avoids requiring a startup activity to subscribe before the first resolution event. Keep the listener itself stateless, with persistent data in its project service.

The exact SDK has a working legacy importer bridge: `MavenProjectImporterUtil.LegacyExtensionImporter.config(...)` calls `MavenImporter.process(...)`, and `FacetImporter.process(...)` calls `reimportFacet(...)`. Neither superclass declares `resolve(...)`. Leaving the plugin's method in place after deleting the contributor would leave it uncalled.

### Acquire a Maven-native embedder

The usable signatures are:

```java
MavenEmbeddersManager MavenProjectsManager.getEmbeddersManager();
MavenEmbedderWrapper MavenEmbeddersManager.getEmbedder(
    MavenProject mavenProject, Key<?> kind);
void MavenEmbeddersManager.release(MavenEmbedderWrapper embedder);
// public static final Key<?> FOR_DEPENDENCIES_RESOLVE
```

For each applicable Codehaus project, acquire with `FOR_DEPENDENCIES_RESOLVE`, reuse the existing `resolve(project, mavenProject, embedder)` download logic after separating source-artifact mutation, and release in `finally`. The public wrapper methods already used by the plugin, `resolve(MavenArtifactInfo, List<MavenRemoteRepository>)` and `resolveArtifactTransitively(List<MavenArtifactInfo>, List<MavenRemoteRepository>)`, are deprecated but not internal in this SDK. No suppression is needed to call them from Java.

The [manager source](https://github.com/JetBrains/intellij-community/blob/master/plugins/maven/src/main/java/org/jetbrains/idea/maven/project/MavenEmbeddersManager.kt) explicitly identifies both the project-based acquisition method and key as used by third-party plugins. The SDK's private implementation obtains the project's Maven distribution, converts `MavenProjectsManager.generalSettings`, and creates a Maven-server embedder for the selected project directory. This retains the Maven-native settings path instead of replacing it with IDE repository configuration. The new `MavenEmbedderWrappers` abstraction is `@ApiStatus.Internal` and is not an acceptable replacement.

Qualification: this is a separately acquired Maven embedder, not the identical live importer instance. Maven settings and project repositories follow the same subsystem, but project-native configuration, offline/mirror/authentication behavior, cancellation, and multi-module path selection still need an actual import regression test. Bytecode inspection alone does not establish all integration behavior.

### Preserve `compileSourcesArtifacts` on the compiler classpath

Do not call `MavenProject.addDependencies(...)`. Resolve the same matching dependencies with classifier `sources`, retain the resulting `MavenArtifact` values in plugin-owned project state keyed by POM, and apply them synchronously through the existing Codehaus importer hook:

```java
LibraryOrderEntry MavenRootModelAdapter.addLibraryDependency(
    MavenArtifact artifact, DependencyScope scope,
    IdeModifiableModelsProvider modelsProvider, MavenProject project);
```

Use `DependencyScope.COMPILE` and the importer-supplied root/model provider. Exact-SDK `MavenRootModelAdapterLegacyImpl.addLibraryDependency(...)` writes the artifact itself under `OrderRootType.CLASSES` before adding the order entry. A sources-classified artifact thus becomes an actual classpath root, preserving the old `addDependencies` effect. Attaching the jar only under `OrderRootType.SOURCES` would not preserve GWT compiler behavior.

Keep downloads outside deferred `smartInvokeLater` facet work and outside write actions. Clear/replace cached source artifacts when the configuration disappears; avoid duplicates across imports. Verify first import, reload, removed configuration, unchanged dependencies with changed plugin configuration, and Maven module layouts. The implementation uses these hooks; validation details and remaining limits are recorded below.

### Reopen and cached imports

An in-memory map alone is insufficient: cached model imports after project reopening need their previous source-artifact results even when `projectsResolved` has not run in the new service instance. Persist plugin-owned source artifact data in a project `PersistentStateComponent` bean, keyed by POM and source-request identity. Filter stored entries against current plugin applicability, `compileSourcesArtifacts`, and matching dependency coordinates on **every** import; replace the POM's complete result set after successful resolution. Do not leave removed configuration effective merely because stale downloaded jars still exist.

The exact SDK provides this public, non-internal constructor in `maven-server.jar`:

```java
MavenArtifact(String groupId, String artifactId, String version,
    String baseVersion, String type, String classifier, String scope,
    boolean optional, String extension, File file, File localRepository,
    boolean resolved, boolean stubbed);
```

This allows reconstructing an attachable source artifact from plugin-owned primitive/string fields; storing platform model internals or reflecting into the artifact is unnecessary. `MavenProject.getLocalRepositoryPath()` is also public/non-internal. Preserve the resolver's actual returned version, base version, classifier, and path; timestamped snapshots and custom repositories make path guessing fragile. In particular, `MavenArtifact.getPathForExtraArtifact("sources", "jar")` combines an existing classifier with `sources`, while the existing Codehaus implementation requests classifier `sources` directly. It is not an equivalent universal reconstruction rule.

`MavenRootModelAdapterLegacyImpl.initOrderEntries()` removes previous Maven library entries while retaining user-added libraries, and `addLibraryDependency` marks its libraries as Maven-owned. This supplies the usual clean reimport path. Still verify actual modern import behavior when configuration is removed entirely, because the Codehaus importer itself may then cease to be applicable. Any supplemental cleanup must identify only the plugin's previously attached artifacts and must not remove an explicit user/POM source dependency.

## Preserve native-run fallback behavior

Replace explicit descriptor construction with:

```java
RepositoryLibraryProperties properties =
    new RepositoryLibraryProperties(groupId, artifactId, version);
Collection<OrderRoot> roots = JarRepositoryManager.loadDependenciesModal(
    project, properties, false, false, null, null);
```

The three-string constructor delegates to the five-argument constructor with transitives `true` and empty exclusions. The public modal overload obtains its descriptor internally and delegates to the original overload. With `loadSources=false`, `loadJavadoc=false`, and jar packaging it selects the original artifact kind. Keep the existing EDT scheduling, disposal checks, path conversion, and dependency storage. This path already uses IDE repository resolution, so the wrapper introduces no Maven-to-IDE repository change. See the [owning JarRepositoryManager source](https://github.com/JetBrains/intellij-community/blob/master/java/idea-ui/src/com/intellij/jarRepository/JarRepositoryManager.java).

## Validation boundary

Performed: task-relevant plugin source inspection; owning-jar discovery; exact-SDK signature, annotation, constructor-default, listener dispatch, legacy importer call-path, class-root, and settings-conversion bytecode inspection; official JetBrains source/documentation checks. Implementation validation: 192 normal tests and 45 free-mode tests pass, including six metadata/cache tests and four real offline Maven import/control cases. First import, SDK transitives, repeat import, cached model reapplication without resolution, configuration removal, explicit POM source dependencies and missing sources are covered. Plugin Verifier remains binary compatible and removes all eight scoped findings (112 to 104), with no override-only violations. The distributed ZIP does not contain the telemetry test-runtime JAR. Cached reapplication uses an internal SDK test-orchestration method only in test code; production uses public hooks. Full IDE close/reopen and live credential/mirror configurations were not exercised.

## Published integration test framework is available

A follow-up inspected both published artifacts already selected by `TestFrameworkType.Plugin.Maven`:

```text
com.jetbrains.intellij.maven:maven-test-framework:263.7307.200
binary SHA path: d6773e5e356ee6dbf0df501bfc641247f32c8b01
sources SHA path: 25d6224cd7471ee6f9d4197d8483d77eecb1b3f0
```

They are present under `~/.gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.maven/maven-test-framework/263.7307.200/`. **The quarantine README's original Maven fixture limitation was stale for these artifacts:** both the binary and matching sources contain `getFacet` (`fixtures/MavenFacetAssertions.kt`), `waitForImportWithinTimeout` (`fixtures/MavenTestFixtureImport.kt`), and `MavenVersionArguments`. This finding establishes API availability, not a successful test execution.

The current supported Kotlin entry point is `com.intellij.maven.testFramework.fixtures.mavenImportingFixture(mavenVersion: String = "bundled", modelVersion: String = "4.0.0", skipPluginResolution: Boolean = true, initialPom: String? = null)`. Use it as a property delegate in an `@TestApplication` class. Its public `MavenImportingTestFixture` exposes `project`, `dir`, `projectsManager`, mutable `repositoryPath: Path`, and mutable `projectPom: VirtualFile`. Available suspend extensions include `importProjectAsync(String)`, `importProjectAsync()`, `updateAllProjects()`, `updateAllProjectsFullSync()`, `scheduleProjectImportAndWait()`, `waitForImportWithinTimeout { ... }`, and `awaitConfiguration()`.

`maven.repositoryPath = temporaryRepository` changes the project's Maven local-repository setting and reloads its settings cache. Set `maven.mavenGeneralSettings.isWorkOffline = true` through the published settings extension; populate that directory with small valid fixture POMs and jars, including the sources-classified jar and GWT transitive fixture dependencies. Use `mavenVersion = "bundled"` to avoid downloading a wrapper distribution; keep `skipPluginResolution = true` to avoid downloading unrelated build plugins. Run suspend import/wait methods from a background coroutine, not EDT (`awaitConfiguration` explicitly checks this). Test scope legitimately uses the framework's import orchestration; production code need not adopt its internal implementation calls.

The no-argument `com.intellij.maven.testFramework.MavenImportingTestCase` also exists as a deprecated legacy fallback. It extends `MavenTestCase` and exposes protected Kotlin `repositoryPath`, `mavenGeneralSettings`, `createProjectPom`, `updateProjectPom`, `importProjectAsync`, `updateAllProjects`, `updateAllProjectsFullSync`, and `scheduleProjectImportAndWait`. The current build already includes the JUnit Vintage engine, so legacy JUnit3 test discovery is not excluded by its JUnit Platform configuration.

For lifecycle coverage, use an actual import for first attachment and configuration removal. **Correction:** `scheduleProjectImportAndWait()` is not an import-only operation. Its source asserts that an auto-reload notification is pending, calls `AutoImportProjectTracker.scheduleProjectRefresh()`, then awaits configuration and checks that the notification cleared. It may run resolution again and fails when there is no pending reload. The earlier inference from its name was incorrect.

The actual import-only SDK operation is `suspend MavenAsyncProjectsManager.importMavenProjects(projects: List<MavenProject>)`, inherited by `MavenProjectsManager`. Exact bytecode follows its `MavenProjectsManagerEx` implementation through the reapply-model activity into `doImportMavenProjects`, `runImportProjectActivity`, and the created `MavenProjectImporter.importProject()`, bypassing `resolveMavenProjects`. **The interface method is `@ApiStatus.Internal`**, so this is a possible test-only orchestration call, not a supported production replacement. To establish the test's claim, count `projectsResolved` callbacks during the operation and assert zero, then await configuration and check source class roots.

A public direct-hook alternative uses `new MavenRootModelAdapter(new MavenRootModelAdapterLegacyImpl(mavenProject, module, modelsProvider))`; exact SDK class and constructor have no internal annotation. Its constructor signature is `(MavenProject, Module, IdeModifiableModelsProvider)`. Using a real already-imported project/module, round-trip the plugin cache state, remove the source order entry, invoke the plugin attachment hook with that adapter/provider, and commit models in the required write action. This proves cached attachment reconstruction without another network resolution; it does not exercise the complete model-import pipeline.

No dedicated close-and-reopen operation is exposed by this Maven fixture: actual reopening requires a platform project lifecycle fixture over the same persisted directory. A state round-trip plus a test-only reapply or direct-hook test must be described as cache reconstruction coverage rather than claiming it closed and reopened the IDE project.

### Artifact DTO accessors

The SDK `MavenArtifact` provides `getGroupId`, `getArtifactId`, `getVersion`, `getBaseVersion`, `getType`, `getClassifier`, `getScope`, `isOptional`, `getExtension`, `getFile`, and `isResolvedArtifact`. The last method returns the raw resolved flag. `isResolved()` additionally excludes stubbed artifacts and checks file existence. There is **no public local-repository getter or stubbed getter**. Preserve returned source results even when unresolved: store the observable `isResolved()` value, preserve the exposed identity/path fields, obtain the repository from `MavenProject.getLocalRepositoryPath().toFile()`, and reconstruct with that resolved value and `stubbed=false`. Missing source jars can then retain their missing class-root URL. Successful-only filtering would change the former `addDependencies` behavior.

This preserves observable `isResolved()` and root attachment, but cannot reproduce a stubbed artifact's separate raw `isResolvedArtifact()` flag with public getters alone. The plugin should not claim an exact private-model clone. Exact-SDK `equals` compares group, artifact, version, baseVersion, type, classifier, scope, extension, and file; it excludes optional, local repository, resolved, and stubbed flags. `getLibraryName()` uses group and artifact, non-jar type if present, classifier if present, and baseVersion when nonblank (otherwise version). Preserving baseVersion and classifier is therefore necessary for stable equality and library identity.

### Minimal local-repository helper

The published Maven fixture supplies project/POM creation helpers but no general artifact installer. A small test-local helper can use JDK APIs directly:

```kotlin
private fun installJar(repo: Path, group: String, artifact: String, version: String,
                       classifier: String? = null) {
  val dir = repo.resolve(group.replace('.', '/')).resolve(artifact).resolve(version)
  Files.createDirectories(dir)
  Files.writeString(dir.resolve("$artifact-$version.pom"), """
    <project><modelVersion>4.0.0</modelVersion><groupId>$group</groupId>
      <artifactId>$artifact</artifactId><version>$version</version></project>
  """.trimIndent())
  val suffix = classifier?.let { "-$it" } ?: ""
  JarOutputStream(Files.newOutputStream(dir.resolve("$artifact-$version$suffix.jar"))).use {
    it.putNextEntry(JarEntry("fixture.txt"))
    it.write("fixture".toByteArray())
    it.closeEntry()
  }
}
```

Imports are `java.nio.file.Files`, `java.nio.file.Path`, `java.util.jar.JarEntry`, and `java.util.jar.JarOutputStream`. Preserve or explicitly replace the fixture POM when adding dependency XML for a transitive test; calling this minimal helper again for the sources classifier overwrites its POM. Install both `org.gwtproject:gwt-dev:2.13.0` and `org.gwtproject:gwt-user:2.13.0`, give one POM a dependency on an independently installed fixture artifact, and request that version in Codehaus `gwtVersion`. A source-classpath test additionally installs a normal dependency and its `sources` jar. `GwtMavenSdk` takes its version from Maven coordinates and always reports itself valid, so real GWT classes are not needed for import/path assertions.

The relevant Kotlin fixture imports are `com.intellij.testFramework.junit5.TestApplication`, `com.intellij.maven.testFramework.fixtures.mavenImportingFixture`, `.importProjectAsync`, `.mavenGeneralSettings`, `.updateAllProjectsFullSync`, `.scheduleProjectImportAndWait`, and `kotlinx.coroutines.runBlocking`. `getFacet` is available from that same fixtures package when facet assertions are needed.

## Standalone Maven test process telemetry classpath

An actual-import test exposed `NoClassDefFoundError: io/opentelemetry/context/propagation/TextMapGetter` in the forked Maven server before plugin listener/importer code ran. Exact-SDK bytecode explains the classpath mismatch:

- `Maven3Support.collectClassPathAndLibsFolder()` selects its development-source branch using `MavenUtil.isRunningFromSources()`, not application unit-test mode. A standalone Gradle plugin test uses the installed-SDK layout branch. The [owning Maven3Support source](https://github.com/JetBrains/intellij-community/blob/master/plugins/maven/src/main/java/org/jetbrains/idea/maven/maven3/Maven3Support.java) documents the same split.
- In that branch, `MavenClasspathBuilder.addMavenServerLibraries()` adds `PathManager.getJarForClass(SpanExporter.class)`, rather than every telemetry dependency. The SDK expects that owner jar to be its merged `lib/intellij.libraries.opentelemetry.jar`.
- In a standalone Gradle test runtime, `SpanExporter` can instead load from the separate `opentelemetry-sdk-trace-1.66.0.jar`. Adding that one owner jar to the fork omits context/API/SDK common jars. Adding those dependencies only to the parent test runtime does not change this child-process selection rule.
- The SDK's source-development branch explicitly collects nine representative telemetry classes, including context/API/common/metrics/logs, but also requires IntelliJ community module outputs and build-dependency resources. Forcing that branch is unsuitable for a standalone plugin test.
- `MavenServerCMDState.createJavaParameters()` consumes the support extension's classpath and adds a small runtime class list. It contains no public test-classpath injection setting established by this investigation; its unit-test flag only selects a special exception-testing main class. See the [owning command-state source](https://github.com/JetBrains/intellij-community/blob/master/plugins/maven/src/main/java/org/jetbrains/idea/maven/server/MavenServerCMDState.kt).

A narrow **test-build-only** correction is to put the existing SDK merged telemetry jar before standalone telemetry jars in Gradle `Test.classpath`, so the owner-class lookup returns the complete SDK jar. No SDK cache editing, production API replacement, extension override, or reflective mutation is required. Gradle plugin 2.18.1 exposes the SDK location as documented public read-only [`intellijPlatform.platformPath: Path`](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#platformpath). For example, in test-task configuration:

```kotlin
classpath = files(intellijPlatform.platformPath.resolve(
  "lib/intellij.libraries.opentelemetry.jar")) + classpath
```

The actual SDK jar contains 758 entries, including both `SpanExporter.class` and `TextMapGetter.class`. This is a proposed harness correction grounded in classpath assembly, not a claim that an import passed: validate the plain-Maven control first, inspect the fork command to confirm the merged jar is selected, then rerun the GWT integration tests. Keep this dependency ordering outside production plugin artifacts.
