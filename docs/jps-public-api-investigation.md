# JPS public artifact packaging API investigation (#50)

Investigated 2026-11-12 against the checkout's IDEA **263.7307.200** SDK and its matching upstream source tag. This note covers the artifact packaging seam; dependency downloads and other JPS replacements are separate work.

## Result

**Supersedes the initial draft:** public substitution was found, but the proposed lifecycle initialization does not preserve artifact builds. The earlier feasibility conclusion must not be used as implementation evidence.

Public `JpsComplexPackagingElement` and the standard directory-copy factory can represent the required artifact layout. **A complete replacement preserving the current build-cache-dependent paths has not been established.** The initially proposed `GwtBuilder.buildStarted` initializer is too late: artifact input roots are constructed during `BuildRunner.load`, before builder lifecycle callbacks. This was discovered while investigating the real artifact-build failure and invalidates the earlier feasibility claim for that initializer. Do not treat an empty successful build as preserved packaging behavior.

The key interface explicitly represents synthetic artifact nodes expanded during the build. It is public and has no internal annotation, as does its `getSubstitution()` method. See matching-tag [JpsComplexPackagingElement](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsComplexPackagingElement.java). The built-in JPS complex-element builder performs expansion; plugin code need not call the internal instruction interfaces. See [LayoutElementBuildersRegistry](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/artifacts/builders/LayoutElementBuildersRegistry.java).

## Preserve build-time paths and dynamic configuration

`JpsPackagingElementSerializer.load(Element)` receives only its XML element. It has no project, module index, or build-data paths. Therefore eagerly replacing a GWT node with concrete directories inside that callback alone cannot reproduce the current build-data-dependent location without adding another source of context. The public serializer can still return a custom complex element retaining its module reference and output kind. See [serializer API](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-serialization/src/org/jetbrains/jps/model/serialization/artifact/JpsPackagingElementSerializer.java).

A substitution must be ready **before the artifact target's `computeRootDescriptors`**, not merely before its dependency calculation. Exact source and bytecode establish this order:

1. `BuildRunner.load` loads the JPS model and constructs `BuildRootIndexImpl` with actual `BuildDataPaths`.
2. `BuildRootIndexImpl` eagerly enumerates target types and targets and calls each target's `computeRootDescriptors`.
3. `ArtifactBuildTarget.computeRootDescriptors` immediately expands the artifact layout into artifact root descriptors and copy instructions. An empty GWT substitution at this point creates no artifact input roots.
4. `BuildRunner.runBuild` first creates the compilation scope. With dependency inclusion enabled, it directly calls `computeDependencies` using a dummy output index, before constructing `IncProjectBuilder`.
5. `IncProjectBuilder.runBuild` invokes target builders' `buildStarted` callbacks, then obtains sorted target chunks. `BuildTargetIndexImpl` constructs a real output index by calling all targets' `getOutputRoots(context)`, then computes the target graph.

Sources: [BuildRunner](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/cmdline/BuildRunner.java), [BuildRootIndexImpl](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/builders/impl/BuildRootIndexImpl.java), [ArtifactBuildTarget](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/artifacts/ArtifactBuildTarget.java), [IncProjectBuilder](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/IncProjectBuilder.java), and [BuildTargetIndexImpl](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/builders/impl/BuildTargetIndexImpl.java).

Both `buildStarted` and `GwtBuildTarget.getOutputRoots(context)` are therefore too late to initialize the artifact roots. Initializing plans in `getOutputRoots` can repair the later dependency graph but cannot retroactively add the artifact roots already stored by `BuildRootIndexImpl`. `GwtBuildTarget.computeRootDescriptors` receives paths early enough in isolation, but no supported ordering guarantee puts the GWT target before artifact targets. `AdditionalRootsProviderService.getAdditionalRoots` is called after the current target has computed its roots, so an artifact-specific provider cannot initialize the substitution before that artifact expands it.

A valid replacement needs a further bridge: make output paths and module selection derivable from the loaded JPS model before root computation, or provide actual path information during model serialization/loading through a supported mechanism. A deterministic model-known compiler output location would avoid the build-data context gap, but changes output-storage design and needs explicit migration/clean behavior analysis. Persisting IDE-computed paths is another potential design, but serialization timing, old configurations, external builds, and cache relocation need validation. Neither alternative is implemented or established by this note.

Once that bridge is solved, retain immutable source/destination path plans and construct fresh standard trees on each `getSubstitution()` call. Reuse the existing module-selection and packaging settings logic, and refresh changed/removed modules before root construction. Fresh trees avoid sharing parented JPS elements across traversals and copies.

The public factory constructs the needed directory tree, including returning the leaf unchanged for an empty destination path. **Do not call `JpsCompositePackagingElement.addChild` directly: it is internal in this SDK.** Calling the public factory is sufficient; its internal implementation performs that mutation. See [factory API](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsPackagingElementFactory.java), [factory implementation](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-impl/src/org/jetbrains/jps/model/artifact/impl/elements/JpsPackagingElementFactoryImpl.java), and [composite API](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsCompositePackagingElement.java).

For each selected GWT module, preserve these mappings from the current `GwtCompilerOutputLayoutElementBuilder`:

| Kind | Source relative to existing GWT compiler root | Destination relative to surrounding artifact directory |
| --- | --- | --- |
| Regular | `outputName` | `packagingRelativePath` |
| Deployment | `WEB-INF/deploy/outputName` | `WEB-INF/deploy/packagingRelativePath` |

The surrounding artifact directory remains implicit: standard substitution elements are expanded in the custom element's existing location. Preserve existing path joining semantics when migrating, including empty and nested paths. Do not copy the entire GWT root as one directory: that would mix regular output and deployment output and ignore per-module placement.

## Dependencies require both scope inclusion and graph ordering

The built-in directory-copy builder obtains dependencies with `TargetOutputIndex.getTargetsByOutputFile(sourceDirectory)`. `TargetOutputIndexImpl` checks the requested path **and every parent directory**. Since each selected source is below the existing `GwtBuildTarget.getOutputRoots(context)` compiler root, this finds the GWT build target even when the module output directory does not yet exist. `BuildTargetIndexImpl` builds this output index for all targets before calculating dependencies. See matching-tag [directory-copy builder](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/artifacts/builders/LayoutElementBuildersRegistry.java), [output index](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/builders/impl/TargetOutputIndexImpl.java), and [target index](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/builders/impl/BuildTargetIndexImpl.java).

Artifact dependency traversal expands complex elements, so the directory-copy leaves participate in dependency computation as well as copy instruction generation. Exact-SDK `ArtifactBuildTarget.computeDependencies` and `JpsArtifactUtil.processPackagingElements` bytecode confirm this. See [artifact target](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/artifacts/ArtifactBuildTarget.java) and [artifact traversal](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/artifacts/impl/JpsArtifactUtil.java). The plugin should consume these public interfaces, not call either internal implementation.

The earlier `BuildRunner.includeDependenciesToScope` pass is different: its dummy `TargetOutputIndex` always returns an empty list. Directory-copy elements therefore do not add GWT targets to an artifact-only scope in that pass, even if substitutions are ready. A later graph dependency does not itself make an out-of-scope target affected. The existing IDE `GwtBuildTargetScopeProvider` explicitly adds GWT and Java production scopes for facets in selected artifacts, so a standalone integration harness must emulate the actual IDE scope contribution rather than assume an artifact-only protobuf scope is equivalent. The provider currently discovers the regular GWT packaging element type; deployment-only artifacts require separate verification.

A complete regression test must prove actual GWT compilation, artifact copying, and failure propagation with realistic scopes. Do not claim standalone artifact-only automatic target inclusion from the standard directory-copy seam alone.

## Remove the internal copy override without losing copying

`JpsPackagingElement.createElementCopy()` is an **internal default method**. It delegates to `getBulkModificationSupport().createCopy()`. `JpsElementBase` publicly supplies `getBulkModificationSupport()`, returning itself. Replace the plugin's `createElementCopy()` override with a plugin `createCopy()` implementation that explicitly constructs the element and a fresh module reference. This preserves the default copy dispatch without overriding the internal method. Copy the immutable plugin-owned path plans and let normal build initialization refresh them for the new model. Recreate standard packaging trees on demand, avoiding shared parent pointers between copies.

`BulkModificationSupport.createCopy()` is deprecated for removal, but is **not internal** in this SDK. Avoid `JpsCompositeElementBase`'s deprecated copy constructor. This is a public legacy API qualification, not a claim that general JPS model copying remains supported. If the acceptance criterion also prohibits deprecated APIs, the copy path requires a separate decision. See matching-tag [JpsPackagingElement](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsPackagingElement.java), [JpsElement](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/JpsElement.java), [JpsElementBase](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/ex/JpsElementBase.java), and [JpsCompositeElementBase](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/ex/JpsCompositeElementBase.java).

## IDE substitution is not sufficient by itself

IDE-side `com.intellij.packaging.elements.ComplexPackagingElement.getSubstitution(context, artifactType)` is public, but belongs to the IDE artifact model. The JPS loader reads serialized XML IDs and dispatches a JPS serializer; it does not instantiate or query IDE packaging elements. Changing the IDE element superclass alone therefore does not supply the JPS build-time substitution or its actual build-data paths. Unknown XML element IDs yield no loaded JPS element. See [IDE complex API](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/java/compiler/openapi/src/com/intellij/packaging/elements/ComplexPackagingElement.java) and [JPS artifact serializer](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-serialization/src/org/jetbrains/jps/model/serialization/artifact/JpsArtifactSerializer.java).

Keep the IDE custom element and existing normal/deploy XML IDs. The JPS complex-element seam described above operates after deserialization and needs no persisted XML expansion. A future IDE substitution could improve presentation, but is unnecessary for this migration and has not been established as a serialization bridge.

## Validation and remaining work

Performed: relevant plugin-source inspection; exact owning-jar discovery; `javap -p -c` control-flow inspection; `javap -p -v` annotation inspection; matching `idea/263.7307.200` upstream source inspection. Owning JARs are `lib/jps-model.jar`, `plugins/java/lib/modules/intellij.platform.jps.build.jar`, and `plugins/java/lib/modules/intellij.java.compiler.jar` under the SDK directory below:

```text
/Users/david/.gradle/caches/9.3.1/transforms/c966849d6ed291580f0545dcbdf20e80/transformed/idea-2026.3-aarch64
```

This research task inspected source and bytecode rather than running a build. A separate real isolated harness reported an empty artifact result without compiler errors, prompting the lifecycle investigation above; that result is a failure to preserve behavior, not a passing packaging test. Required implementation checks: deserialize both existing XML IDs; verify copy dispatch and independent module references; prove a fresh compile with actual IDE-contributed scopes, separately characterize artifact-only scope behavior, then test incremental source edit and repeat no-op build; verify nested custom packaging paths and non-default module rename-to; verify regular versus deployment output isolation; verify rebuild/clean and stale output removal after changed/removed modules; verify compiler-report behavior and failed compilation behavior; rerun Plugin Verifier to show the internal instruction-builder and copy-override findings disappear without replacement internal findings.

JetBrains' official [External Builder API](https://plugins.jetbrains.com/docs/intellij/external-builder-api.html) documents JPS plugin services, build targets, compile context, and build-data storage. Its [Internal API Migration](https://plugins.jetbrains.com/docs/intellij/api-internal.html) guidance distinguishes public visibility from supported API status and directs missing replacement requests upstream. The public representation seam is real, but initialization before artifact root construction remains the deciding issue. Resolve and validate that issue before claiming a complete migration; otherwise request a scope decision or upstream API rather than silently removing packaging.


## Scope decision: retain current packaging or relocate output

Retaining the existing internal packaging builder preserves current behavior while the lifecycle gap is resolved or JetBrains supplies an earlier public hook carrying model and build-data paths. This leaves the nine packaging-builder findings scoped to that service; the separate copy override can still move to public `createCopy()`. A partial migration must leave #50 open and describe this remaining boundary, with no feature deletion or suppression.

A concrete alternative is a new module-specific output area derived from the module's resolved production output directory. Public `JpsJavaExtensionService.getOutputDirectoryPath(module, false)` and `getOutputUrl(module, false)` are available and unannotated in exact 263.7307.200; they are nullable. See [JPS Java service API](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/java/JpsJavaExtensionService.java). A reviewable location is:

```text
<production-output-parent>/.intellij-gwt/<stable-module-key>/war/
<production-output-parent>/.intellij-gwt/<stable-module-key>/extra/
```

For example, production classes at `project/out/production/app` would place GWT data below `project/out/production/.intellij-gwt/<app-key>/`. The key must distinguish modules sharing the same Java output or parent directory, remain filesystem-safe, and be generated identically by all callers. Reserve the private directory for plugin-owned data; never delete the Java output parent during GWT cleanup. Keep GWT output outside Java's class directory so Java compilation/rebuild does not consume GWT files as classes or own their cleanup. Missing output configuration should produce a specific build error rather than guessing another location or silently omitting files.

Moving only the path helper is insufficient. `JpsGwtModuleIndexImpl` currently requires `BuildDataPaths` because it loads the IDE-generated module-discovery cache. Its filesystem scanning fallback is private in `JpsGwtModulesCache`. A full relocation design must expose a model-only module-discovery/index path available to `getSubstitution()` during artifact-root construction, preserving inheritance, test-source selection, compilation-enabled settings, entry-point detection, rename-to, and packaging-relative-path logic. This is additional implementation and validation scope.

Current repository path owners and readers are:

| File / caller | Existing path or behavior | Relocation implication |
| --- | --- | --- |
| `jps-plugin/.../build/JpsGwtCompilerPaths.java` | GWT compiler `-war` output and report `-extra` below target build-data root | Change both helpers to one model-derived module layout; compiler, target output roots, and substitutions must agree. |
| `jps-plugin/.../build/GwtBuilder.java` | Uses compiler root as `-war` and process working directory; report root as `-extra`; registers compiler output directory with JPS | Preserve output registration, compile reports, working-directory-dependent compiler behavior, and clean/rebuild semantics. |
| `jps-plugin/.../build/GwtBuildTarget.java` | Reports compiler output root to JPS | Must report the relocated root, retaining correct dependency and cleanup ownership. Extra/report retention needs an explicit policy. |
| `src/.../make/GwtCompilerPaths.java` | Separate IDE cache under system `gwt`, keyed by project and module identity | This is **not** the JPS compile output location today. Do not mechanically repoint every method to the new compiler location. |
| `src/.../run/GwtCommandLineState.java` | Uses IDE `getOutputRoot(module)/run` for development-mode output | Keep this separate unless intentionally migrating development-run output too. |
| `src/.../junit/GwtJUnitPatcher.java` | Uses IDE `getOutputRoot(module)/test/{gen,www}` | Keep separate or explicitly include JUnit runtime storage in the scope decision. |
| `src/.../make/report/CompileReportsHistory.java` | Stores report metadata at IDE project cache `compile-reports-v2.xml`; actual report location arrives from JPS custom message | Metadata path need not move. New JPS report messages must contain the actual relocated report path; old history paths may remain valid until their reports are cleaned. |
| `src/.../psi/GwtSourcePathsRefresher.java` | IDE cache `gwt-source-paths.xml` | No inherent reason to relocate this IDE index cache. |
| `src/.../make/GwtModulesIndexBuildTask.java` | Writes module discovery cache in project build-data `targets/gwt/gwt-modules.xml` | Can remain as an optimization only if model-only substitution no longer depends on receiving this path; confirm both discovery paths agree. |

The IDE has no current direct reader of the JPS `gwt-output` directory: reports use their transmitted path, and development/JUnit outputs use their own cache. Thus updating every IDE `GwtCompilerPaths` caller is a larger optional unification, not a prerequisite shown by present callers. If a shared compiler-output API is added, obtain the IDE production path through the public compiler-output API and share the same pure path/key helper; preserve the distinct run/test/metadata caches unless that broader migration is explicitly chosen.

Material risks: changed output path invalidates old incremental mappings; the first build must force or prove a complete recompile rather than copy stale old-cache output; old outputs and report history need a bounded ownership-safe cleanup policy; Java output relocation, shared output directories, null/root output paths, module rename, project moves, IDE exclusion/indexing behavior, write permissions outside the IDE system cache, and cross-project output sharing need tests. Maven/Gradle clean may now remove GWT output under their normal build directories. Registering a root under project content can alter watcher/indexing behavior. Compile-report retention and failed-compile cleanup must remain explicit.

The relocation alternative is technically plausible because it removes the unavailable build-data-path input, but has **not** been implemented or proven. It is a concrete scope choice, not a drop-in API replacement.

## Minimal real JPS smoke harness

The existing `jps-plugin/test/.../GwtBuilderTest.java` provides useful scenario outlines but is not currently registered as a Gradle test source. Its `com.google.gwt.dev.Compiler` is a repository-local stub that merely creates HTML files; running it would exercise JPS orchestration without establishing real GWT compilation, deployment output, or reports. The downloaded SDK contains `BuildRunner` but not `Standalone` or `ArtifactBuilderTestCase`; enabling those old tests without obtaining additional framework artifacts is not an immediate path.

A small dedicated integration harness can follow JetBrains' matching-tag [Standalone reference](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/standalone-builder/src/org/jetbrains/jps/build/Standalone.java): construct `BuildRunner` with a `JpsModelLoader`; call `setBuilderParams`; load a descriptor with an actual cache directory and `new BuildFSState(true)`; call `runBuild` with `CanceledStatus.NULL`, a collecting message handler, explicit protobuf target scopes, and dependency inclusion enabled; release the descriptor in `finally`. The exact binary exposes `BUILD`, `PROJECT_REBUILD`, `CLEAN`, and `UP_TO_DATE_CHECK` enum values, but `BuildRunner` implements `CLEAN` as a no-op and has no `PROJECT_REBUILD` dispatch branch; enum availability does not establish behavior. Use `BUILD` with forced target scopes for a standalone rebuild. See [BuildRunner](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/cmdline/BuildRunner.java). This is test orchestration, not a proposed production dependency.

Use real GWT developer/user JARs and their actual dependencies, with a minimal valid EntryPoint fixture, and include the plugin JPS resources so Java services discover its builders and serializers. No real `gwt-dev` or `gwt-user` dependency was found in this checkout's Gradle artifact cache during the bounded check; dependency acquisition may therefore be required. Keep the local fake compiler off the classpath.

Run a build with the exact artifact, GWT, and Java scopes supplied by the IDE provider; separately test an artifact-only scope to expose the standard dummy-index limitation. Make an actual client source edit and rerun with retained cache state, then assert an unchanged run has no GWT compile. Exercise both regular and deployment output, nested placement and module rename-to, and CLEAN/rebuild. For reports pass the existing `GWT_COMPILE_REPORT_SOURCE` builder parameter using `GwtBuilderParameters.generateCompileReportParameter(moduleName, gwtQualifiedName)` and verify real report output files. Capture compiler messages, created files, modification times or content hashes, and failure exits; an empty expected filename alone is inadequate evidence. These are proposed checks and have not been run by this research task.


## Exact clean and rebuild validation semantics

A real standalone smoke assertion that `BuildType.CLEAN` deletes artifact files is invalid for this SDK. Matching-tag `BuildRunner.runBuild` has an empty `CLEAN` case with a TODO; exact bytecode jumps directly to completion without an `IncProjectBuilder` call. `PROJECT_REBUILD` likewise has no branch in that switch. These results describe the SDK runner, not a plugin implementation failure. See [BuildRunner](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/cmdline/BuildRunner.java).

The IDE artifact menu's Clean action follows a different path: `BuildArtifactAction.CleanArtifactItem` resolves each artifact output file/directory, deletes it using `FileUtil.delete` in an IDE background task, and refreshes the VFS. It checks for source-root overlap before deleting. It does not invoke JPS `BuildType.CLEAN`. Exact SDK `intellij.java.compiler.impl.jar` nested-class bytecode agrees with matching-tag [BuildArtifactAction](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/java/compiler/impl/src/com/intellij/compiler/actions/BuildArtifactAction.java).

For a standalone JPS regression baseline:

1. Build with `BuildType.BUILD` and force flags false; check real generated artifacts, compiler output, and messages.
2. Repeat unchanged to prove no-op incremental behavior; edit actual client source and verify compiled JavaScript content changes and artifact updates.
3. Rebuild using `BuildType.BUILD` with `TargetTypeBuildScope.setForceBuild(true)` for the affected artifact/GWT/Java scopes. Verify registered output cleanup and a real recompile without changing sources, followed by a no-op build. Do not use `PROJECT_REBUILD` as a substitute.
4. Separately emulate IDE artifact Clean by deleting only the fixture-owned artifact output path, retaining compiler outputs and build caches, then run normal `BUILD` and verify artifact output is reconstructed. Label this explicitly as an emulation of the IDE filesystem step; it does not exercise the actual UI action or its overlap/cancellation dialogs.

`IncProjectBuilder.cleanOutputRoots` clears forced targets during `BUILD`; non-module targets use their `SourceToOutputMapping` to delete registered outputs. Thus a forced rebuild need not delete an arbitrary untracked sentinel in the artifact directory. Use a tracked output or deletion events to establish JPS cleanup. Whole-directory deletion belongs to the IDE Clean action, and unrelated user files must not be used as expected cleanup targets. See matching-tag [IncProjectBuilder](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/IncProjectBuilder.java).

## Partial implementation and real-build validation

The safe part of #50 retains the existing artifact builder and its service registration. It replaces the internal build-root descriptor with a plugin-owned public descriptor, reads the established `targets/<type>/gwt-modules.xml` cache path directly, parses the persisted facet ID through the public module-reference factory, and overrides the public legacy copy method. The packaging element XML IDs and cache layout remain compatible.

A proposed repository-manager acquisition replacement was rejected by Plugin Verifier: `JpsRemoteRepositoryService`, its repository/configuration types, `JpsPathVariablesConfiguration`, and the relevant path-variable accessor are internal too. Replacing `DependencyResolvingBuilder` with those APIs removed four findings but added fourteen, producing 35 overall rather than the expected 21. That replacement and its helper/tests were removed. Native repository resolution is deliberately retained, including project repositories, local-repository settings, authentication, retries, progress and cancellation. The owning implementation is matching-tag [DependencyResolvingBuilder](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/dependencies/DependencyResolvingBuilder.java). A public IDE-to-JPS configuration bridge or an upstream public acquisition API needs a separate design decision; discarding custom repositories/authentication is not an acceptable substitute.

Three focused tests cover production root identity/test exclusion, cache path and metadata roundtrip/fallback, and both persisted packaging IDs with independent copy references. The public `createCopy()` contract is deprecated for removal; it removes an internal override while retaining copying, with an explicit maintenance qualification.

The retained packaging implementation also passed a separate JVM running **real JPS and the real GWT 2.13.1 compiler**, with actual local SDK JARs. Checks cover an artifact-only fresh compilation, regular JavaScript and deployment symbol maps, an unchanged no-op build, an incremental client edit changing packaged JavaScript, real HTML compiler reports, `rename-to` with stale artifact removal, emulated IDE artifact cleanup followed by forced JPS rebuild, and a GWT-only JSNI compilation failure. It uses cached SDK dependency metadata; native repository acquisition remains unchanged. No interactive IDE action or complete remote-repository configuration matrix is claimed.

The standalone clean qualification matters: `BuildRunner` does nothing for its `CLEAN` enum. The fixture emulates the actual IDE clean action by deleting only its owned artifact output directory, then uses supported forced `BUILD` scopes for artifact, GWT and Java production targets. Source-launched fixture code lives in `testData/jps/GwtJpsSmoke.java`; the old repository-local fake GWT compiler is not on its classpath.

Reproduce with a complete local GWT 2.13.1 repository:

```shell
GWT_JPS_SMOKE_REPOSITORY=/path/to/local/repository \
  ./gradlew --offline --no-daemon --max-workers=1 test --tests '*GwtJpsSdkSmokeTest'
```

Without the optional repository path, that test explicitly skips via the SDK's JUnit 3 assumption-support runner. Fixture files and optional SDK environment paths are tracked as Gradle test inputs. The Marketplace guide records the complete-suite/verifier results. #50 remains open because nine artifact instruction-builder findings and four repository-helper findings are deliberately retained pending the output-location/configuration-bridge/public-hook decision.

Final partial validation: **208 normal + 45 free-mode tests pass**, with no failures, errors or skips when both optional SDK inputs are supplied. Package/structure checks pass. Plugin Verifier remains binary compatible with IU-263.7307.200, no override-only violations; internal findings fall **32 → 25**, with seven removed and zero new findings. Scheduled-removal usages increase **7 → 8** for the public legacy copy override; deprecated/experimental counts remain 59/62. Strict verification intentionally still fails on the 25 retained internal usages (13 in #50, nine in #51, three in #52).
