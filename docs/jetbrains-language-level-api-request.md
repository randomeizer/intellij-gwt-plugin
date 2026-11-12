# Supported per-source Java language levels for external plugins

Target: JetBrains IntelliJ Platform / Java OpenAPI request. Prepared for review; **not submitted**.

## Environment and use case

IntelliJ IDEA 2026.3, build **263.7307.200**, Java 25. An extracted GWT plugin needs Java 8 for selected client and super-source roots while server files in the same module use Java 17 or later. The compiler's `-sourceLevel` setting must remain independent of editor behavior.

The current integration implements `JavaLanguageLevelPusherCustomizer`. Plugin Verifier reports three Internal API usages: the interface and its two callbacks. We have migrated the other 133 Internal findings in this project and retained these working features instead of suppressing findings or lowering the entire module's language level. This blocks our strict release gate; it is not a claim that Marketplace automatically rejects every Internal API warning.

Reproducer and evidence: [project issue #52](https://github.com/randomeizer/intellij-gwt-plugin/issues/52), `docs/language-level-public-api-investigation.md`, and active `GwtClientLanguageLevelTest` on branch `feature/52-marketplace-client-language-level`.

## Observed public API limitations

- The native `JavaLanguageLevelPusher` is final and owns the persistent property consumed by native resolve/indexing; that property is private. A separate public file-property pusher with its own property does not provide the native Java language level.
- `PsiUtil.FILE_LANGUAGE_LEVEL_KEY` is public. With the Internal customization masked in a test, setting this key on a PsiJavaFile makes its parser level Java 8, but `PsiResolveHelper.getEffectiveLanguageLevel(virtualFile)` still returns the module's Java 17. It is not an equivalent semantic/indexing override and does not supply native invalidation lifecycle.
- `JavaLanguageLevelProvider` customizes lazy syntax parsing. `LanguageFeatureProvider` supports selected feature checks. Neither replaces the full native per-file level used consistently by parsing, resolution and indexing.
- The legacy `getInconsistencyLanguageLevelMessage` callback remains in the Internal SPI, but current `JavaErrorKinds.UNSUPPORTED_FEATURE` builds its message directly. Runtime unsupported-feature highlighting does not call the legacy callback or show our GWT configuration suffix. We found no other caller in the inspected Java SDK.

Exact matching-tag source links, binary annotations and runtime probe details are in the investigation. The probes do not write the native private property, reflect into production internals or replace the built-in pusher.

## Requested supported contract

Please expose the existing per-source customization as a supported public OverrideOnly extension, or provide an equivalent API with these properties:

1. A project/file-or-directory callback can return a nullable Java language level. Null preserves the module/project default, with documented precedence for multiple providers.
2. The native platform uses the override consistently for physical Java PSI/lazy parsing, resolve, indexing and language-feature diagnostics.
3. A documented refresh operation handles provider-setting changes and source-root changes, invalidating persistent properties and loaded PSI safely.
4. Unsupported-feature diagnostics can identify the configuration supplying the overridden level, such as a GWT facet, without reimplementing localized Java diagnostics. A public configuration-origin/message-context hook would be sufficient.

A module-wide language-level change is not equivalent: either server sources are incorrectly restricted to Java 8, or client sources incorrectly accept features beyond the GWT compiler setting. Copying a private property identifier or installing a competing writer for the native key would rely on implementation details.

## Acceptance/reproduction matrix

- Module 17, client 8: client and super-source files use 8; server files use 17.
- Equal levels use the default; changing the client setting, module setting or GWT XML source paths updates existing native file levels.
- Masking the Internal customization demonstrates the public PSI-key parser/resolve split while the compiler setting remains 8.
- A real unsupported-feature highlighting pass confirms whether the configuration-origin diagnostic hook is invoked.

Run the active probes with Java 25:

```shell
./gradlew --no-daemon --max-workers=1 test --tests '*GwtClientLanguageLevelTest'
./gradlew --no-daemon --max-workers=1 testFreeEdition --tests '*GwtClientLanguageLevelTest'
```

The source/annotation investigation and tests target build 263.7307.200. We are asking for the supported contract or a supported alternative, not asserting that no future SDK can provide it.
