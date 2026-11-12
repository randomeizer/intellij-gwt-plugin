# JPS public API migration (#50)

Implemented against IDEA **263.7307.200** after the publisher approved the broader generated-output/configuration redesign. The earlier partial PR retained the internal packaging and repository helpers; the completed design replaces both. Exact SDK source, bytecode and runtime checks underpin the decisions below.

## Why direct API substitutions failed

Artifact input roots are computed during `BuildRunner.load`, before `Builder.buildStarted` supplies a compile context. Preparing standard copies in `buildStarted` produced an apparently successful build with no artifact files. Separately, the early dependency-scope pass supplies a dummy output index, so directory-copy elements cannot implicitly include the GWT compilation target. Sources: [BuildRunner](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/cmdline/BuildRunner.java), [BuildRootIndexImpl](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/builders/impl/BuildRootIndexImpl.java), [ArtifactBuildTarget](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/jps-builders/src/org/jetbrains/jps/incremental/artifacts/ArtifactBuildTarget.java).

Replacing the repository helper with its native model APIs was also insufficient: `JpsRemoteRepositoryService`, its model types and the path-variable configuration interface are Internal. That prototype removed four findings but introduced fourteen and was discarded. Public visibility alone did not establish supported API status.

## Model-known output and public packaging

JPS compilation now writes beside the module's production class directory:

```text
<production-output-parent>/.intellij-gwt/<module-key>/war/
<production-output-parent>/.intellij-gwt/<module-key>/extra/
```

`JpsGwtCompilerPaths` derives the key from project identity, the normalized production output path and module name. Programmatically constructed models without a project directory use their source-root URLs as the project identity fallback. Modules sharing production output remain distinct. Missing production output is an explicit build error. The public [JPS Java service](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/java/JpsJavaExtensionService.java) supplies the directory before a build context exists.

`JpsGwtCompilerOutputPackagingElement` implements public `JpsComplexPackagingElement`. It discovers GWT modules through the existing model/source scanning logic and immediately returns fresh standard directory-copy trees through public factory methods. Normal/deploy XML IDs, module rename-to, compilation-enabled flags and configured artifact destinations are retained. The internal layout builder and its service registration are removed. Sources: [complex element](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsComplexPackagingElement.java), [packaging factory](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsPackagingElementFactory.java).

A marker outside `war` identifies plugin-owned output. Existing unmarked data and symbolic output containers are rejected before JPS cleanup; compiler cleanup never deletes the Java output parent. Missing generated output forces recompilation even when source stamps survive an upgrade or external clean. Old system-cache output is disposable and is not copied into the new location. The legacy-to-current real build test retains the old cache and unchanged source files.

The public exclusion policy reserves `.intellij-gwt` below local Java output parents, including after a GWT facet is removed. Root-model changes invalidate the policy automatically. The policy supports physical file URLs; it does not invent physical locations for in-memory test filesystems. See [scope/indexing investigation](jps-public-scope-investigation.md).

## Explicit compilation scopes

The IDE scope provider includes GWT and Java production targets for both regular and deployment-only artifacts. Standalone JPS callers must explicitly include those whole-target scopes. A public PRE_PROCESSING artifact task rejects missing GWT scopes with an actionable error before artifact copying, including fresh, stale and unchanged requests. Referenced artifact layouts and nested elements are traversed.

This is a deliberate standalone contract change: artifact-only requests no longer implicitly add the GWT target through an internal instruction-builder dependency. Programmatic callers should use type IDs `gwt` and `java-production` with the corresponding module ID. Persisted packaging settings remain compatible. The exact public SPI and lifecycle are documented in [the scope investigation](jps-public-scope-investigation.md).

## Public repository handoff

The existing IDE scope provider snapshots ordered repository IDs/URLs into a versioned, bounded payload in scope user data. `CompileDriver` exports it to per-build parameters; the build process reads a fresh snapshot per compile context, so process reuse does not retain old settings. Malformed or unsupported payloads fail without echoing their contents. Authentication remains in JPS.

A public project serializer reads the existing `RemoteRepositoriesConfiguration` component for standalone directory and `.ipr` projects. Explicit per-build data takes precedence. Programmatically constructed standalone models can set `GwtRepositoryConfiguration` or pass its encoded builder parameter; absent both, the native repository defaults apply. The plugin never accesses the Internal JPS repository model. See [repository bridge investigation](jps-repository-bridge-investigation.md) for the owning public APIs and matching-tag sources.

The resolver preserves the global `MAVEN_REPOSITORY` override through the public string accessor, then native Maven option/settings fallback, property/provider/settings authentication precedence, retry options, progress and cancellation. `GwtJpsMavenSettings` retains upstream attribution and the matching-SDK lookup policy. This policy/configuration-format dependency must be checked when upgrading IDEA. The authentication provider is public Experimental. Native snapshot-policy behavior is retained; the bridge does not introduce a new snapshot policy.

## Copying and persisted formats

Production-root IDs/filters retain their old values. IDE module-discovery cache access still uses `targets/<type>/gwt-modules.xml`. Persisted facet IDs are parsed through the public module-reference factory. The packaging element implements public legacy `createCopy()` so the platform's internal default copy method dispatches correctly without a plugin override of that Internal method. The copy contract is deprecated for removal; this remains a maintenance qualification. Sources: [packaging element copy contract](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/artifact/elements/JpsPackagingElement.java), [JpsElement](https://github.com/JetBrains/intellij-community/blob/idea/263.7307.200/jps/model-api/src/org/jetbrains/jps/model/JpsElement.java).

## Validation and practical consequences

Focused checks cover path identity, ownership/symlinks, pre-callback substitutions, inherited/test module discovery, disabled output, both persisted packaging IDs, copy independence, per-build IDE snapshots, standalone project loading, Maven options/settings/authentication precedence, cancellation, deployment-only IDE scopes and indexing after output changes/facet removal.

The isolated real GWT 2.13.1/JPS scenario covers retained-cache upgrade from the previous plugin JAR, explicit scopes and missing-scope errors, no-op/incremental builds, reports, rename-to/stale cleanup, Java-output and module-name changes, deployment-only output, forced rebuild, GWT-only compilation failure, disabled-output cleanup and malformed per-build repository configuration. The old repository-local fake GWT compiler is not used.

IDE artifact Clean deletes the artifact output directory; standalone `BuildRunner.CLEAN` is a no-op in this SDK. The fixture emulates the IDE deletion only within its temporary output and then uses supported forced BUILD scopes. This is not an interactive IDE smoke test. Real authenticated corporate repositories, remote build agents and every auto-make integration have not been exercised.

The first relocated build recompiles. Build-tool cleans can now remove generated GWT files under `build`/`target`; report files in `extra` survive ordinary JPS war-output cleanup but not deletion of their enclosing build area. Old report history retains its absolute locations until those files are removed. DevMode/JUnit runtime output and IDE index/report metadata remain separate caches.

Reproduce the real scenario with local SDK artifacts:

```shell
GWT_JPS_SMOKE_REPOSITORY=/path/to/GWT-2.13.1/repository \
GWT_JPS_SMOKE_LEGACY_PLUGIN_JAR=/path/to/pre-relocation/randombits-gwt-jps.jar \
  ./gradlew --offline --no-daemon --max-workers=1 test --tests '*GwtJpsSdkSmokeTest'
```

The legacy JAR is optional; supplying it enables the retained-cache upgrade phase. Without the repository input the SDK scenario explicitly skips. Test fixtures and optional SDK paths are Gradle inputs. Complete test/verifier results are recorded in [the Marketplace guide](marketplace-publishing.md).
