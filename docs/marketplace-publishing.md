# JetBrains Marketplace publishing

Research checked on **12 November 2026** for [issue #1](https://github.com/randomeizer/intellij-gwt-plugin/issues/1). This is a release checklist, not confirmation of a live listing or Marketplace approval.

## Current validation and migration

- The publisher chose **`org.randombits.gwtplugin`** for the first Marketplace listing. The final Plugin Verifier 1.411 run reports binary compatibility with **IU-263.7307.200** and no override-only violations, but **104 internal-API usages still fail the strict release gate**. Those usages have not been muted. The report also identifies 59 deprecated usages, including seven scheduled for removal, and 62 experimental usages to track during maintenance.
- Existing manual ZIP installations with the old ID must be uninstalled before installing the new ID. The IDE treats the IDs as distinct plugins, so this is not an automatic update of an old installation. The four existing extension-point IDs retain their old fully qualified names to preserve runtime registrations.
- Full suites passed with the final ID/email and explicit Java/XML dependencies: **192 normal tests and 45 free-edition tests**, no failures, errors, or skips. `buildPlugin` and `verifyPluginStructure` passed. The ZIP's final ID, email, `263.*` range, four preserved extension-point names, and bundled license/notice bytes were checked. Test evidence is in `build/test-results/test/` and `build/test-results/testFreeEdition/`; verifier findings are in `build/reports/pluginVerifier/IU-263.7307.200/plugins/org.randombits.gwtplugin/263.1.1/`.
- The publisher supplied **info@randombits.org** as the public vendor email. Root `LICENSE.txt` and complete protobuf BSD attribution in `THIRD_PARTY_NOTICES.txt` are bundled, with ZIP bytes checked. Vendor/trader setup, signing credentials, and the remaining redistribution audit still precede external publication. No Marketplace upload has been performed.

## Cost and publisher requirements

Publish this plugin as a **Free Plugin**. The current upload instructions and Developer Agreement do not specify an upfront registration or listing charge; the agreement's sales remuneration provisions concern Paid Plugins. A no-fee listing is the supported route inferred from those documents, rather than a guarantee that Marketplace terms will never change. Paid/freemium sales configuration is outside this release's scope. [Upload instructions](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html), [Developer Agreement, §§3–5](https://www.jetbrains.com/legal/docs/plugins_site/developer-agreement/).

A personal JetBrains Account and a vendor profile are required. An individual can be a vendor; incorporation is not required. The vendor ID cannot subsequently be edited, so choose it deliberately. The publisher must accept the Developer Agreement and declare trader/non-trader status. [Vendor profiles](https://plugins.jetbrains.com/docs/marketplace/organizations.html).

**Free does not automatically mean non-trader.** Status depends on the publisher's activity. JetBrains currently requires identity/contact verification and banking details from all traders, even without Marketplace sales; some contact information becomes public. The publisher must make the declaration themselves. [Trader-status requirements](https://plugins.jetbrains.com/docs/marketplace/trader-status.html).

## Identity, license, and remaining decisions

- Publish **Randombits GWT Support**, XML ID **`org.randombits.gwtplugin`**. The existing [JetBrains GWT listing](https://plugins.jetbrains.com/plugin/14590-gwt) is a separate plugin. Preserve the independent-maintenance explanation in the README and description. A distinct ID does not transfer the other listing's users or ownership.
- The public vendor email is **info@randombits.org**. Check that the vendor/project URLs and email are valid and functional before uploading; approval requires them. The current SVG icon is 40 × 40, the required format and dimensions. Confirm rights to its artwork and the final name before submission. “Support” is discouraged in names, rather than expressly prohibited. [Approval guidelines, §1](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html).
- Choose the existing **Apache License 2.0** as the listing license and provide the public [source repository](https://github.com/randomeizer/intellij-gwt-plugin). Marketplace accepts open-source license choices and requires a source URL for them; do not paste its unmodified example proprietary EULA over this project's license. [Upload license fields](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html), [EULA guidance](https://plugins.jetbrains.com/docs/marketplace/eula.html).
- Before release, audit redistribution notices for the complete ZIP, including bundled libraries. Apache 2.0 permits modified source/binary redistribution subject to its conditions: supply the license, mark modified files, retain applicable source notices, and reproduce relevant upstream NOTICE attribution where present. It does not grant general trademark rights. [Apache License 2.0, §§2, 4, 6](https://www.apache.org/licenses/LICENSE-2.0).

Repository evidence: `LICENSE.txt` is Apache 2.0 and source headers retain JetBrains attribution. `lib/superdevmode-launcher-legacy.jar` contains an Apache 2.0 redistribution statement in `LICENSE.md`, Google copyright/license headers in embedded Java sources, and a README attributing Manuel Carrasco Moñino and identifying GWT 2.5.x/2.6.x compatibility. Its exact upstream revision/build provenance is not recorded there. The build also ships `protobuf-javalite:3.25.5` and project runtime/JPS modules. The protobuf binary JAR has no license/notice file; the distribution therefore supplies its full Google copyright, conditions, and disclaimer in `THIRD_PARTY_NOTICES.txt`, as required by its [versioned BSD-style license](https://github.com/protocolbuffers/protobuf/blob/v25.5/LICENSE). These checks do not establish a complete audit: preserve existing notices and confirm the origin and licenses of every distributed component before release.

## Local release preparation

The publishing setup uses IntelliJ Platform Gradle Plugin tasks, without an automatic Marketplace publication workflow. GitHub Releases remain a separate distribution channel.

Implemented preparation adds the project/vendor URL and independent-maintenance description, publisher-supplied public email, `263.*` upper bound, explicit Java/XML plugin dependencies, Plugin Verifier/ZIP Signer dependencies, current-platform verifier target, packaged root `LICENSE.txt` and `THIRD_PARTY_NOTICES.txt`, and the aggregate gate below. UiBinder's attribute descriptor lookup now delegates to its public name/tag overload instead of calling an override-only method. `publishPlugin` depends on that gate. IntelliJ 2026.3 moved XML/HTML into a separate bundled plugin, requiring an explicit dependency when its APIs are used. [2026.3 API changes](https://plugins.jetbrains.com/docs/intellij/api-changes-list-2026.html#2026-3).

1. Set the release version in `gradle.properties` and add matching `change-notes/<pluginVersion>.html`. Confirm the public vendor email and URLs in the descriptor before packaging.
2. Run the aggregate local gate:

   ```shell
   ./gradlew prepareMarketplaceRelease
   ```

   It runs tests (including the free-edition sandbox), packaging, plugin structure checks, and Plugin Verifier. Read `build/reports/pluginVerifier/` and resolve reported release blockers. A skipped check, missing verifier/IDE download, or failed tool resolution is not successful verification.
3. Inspect the ZIP in `build/distributions/`: patched ID/version/vendor details, compatibility, change notes, license, and bundled library notices. Install that exact ZIP in a fresh supported IDE and exercise core GWT behavior in free mode and the optional integrations in Ultimate. Existing sandbox tests do not replace an interactive installation/GWT application smoke test.

The compatibility policy is IntelliJ IDEA **2026.3 / build 263**, bounded to `263.*`; Java 25 is used for this platform. Omitting `until-build` would advertise all future IDEs. The configured verifier target is the current build platform, so a single passing run does not prove every 263 patch release: check additional available builds before expanding claims. [Build-number ranges](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html).

Plugin Verifier checks binary compatibility, including invalid API use; it does not prove feature correctness. Marketplace requires compatibility verification for each upload. [Compatibility verification](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html), [Approval guidelines, §2](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html).

### API migration work

The migration stack is tracked as native sub-issues of [#1](https://github.com/randomeizer/intellij-gwt-plugin/issues/1), in serial order: [#45 output paths/icons](https://github.com/randomeizer/intellij-gwt-plugin/issues/45), [#46 settings/report UI](https://github.com/randomeizer/intellij-gwt-plugin/issues/46), [#47 Gradle delegation](https://github.com/randomeizer/intellij-gwt-plugin/issues/47), [#48 Maven resolution](https://github.com/randomeizer/intellij-gwt-plugin/issues/48), [#49 Remote UI](https://github.com/randomeizer/intellij-gwt-plugin/issues/49), [#50 JPS compilation/artifacts](https://github.com/randomeizer/intellij-gwt-plugin/issues/50), [#51 debugger integration](https://github.com/randomeizer/intellij-gwt-plugin/issues/51), and [#52 client language levels](https://github.com/randomeizer/intellij-gwt-plugin/issues/52).

#45 removes all three targeted findings (136 → 133) using supported module-directory/macro APIs and `IconLoader`. The icon holder is now maintained in `src/`, outside the generated-source tree. Cache directory hashes retain their historical values for conventional `<module-name>.iml` filenames; unusual module filenames can get regenerated cache directories. Project sources and configured output destinations are unchanged. Five new integration checks cover cache identity/location, test paths without a persisted module file, launch-directory macro semantics, and actual icon loading in both editions. The first combined run encountered the previously observed free-mode inspection-test failure; the complete free-mode suite passed when repeated separately.

#46 removes all 20 assigned UI findings (133 → 113): public renderer subclasses preserve icons/empty selections, a plugin-owned module chooser preserves default selection/selection/cancel behavior, and user-visible tab labels live in the plugin bundle. The indexing guard and its error remain; only internal telemetry is removed. Three new chooser tests pass alongside both full suites. Compromise: Configuration/Logs tab labels no longer inherit IDE language-pack translations automatically; English is unchanged and plugin-bundle translations can be added independently.

#47 removes the delegated Gradle run's remaining internal source-level formatter (113 → 112). Compiler option spelling is preserved: `1.3`/`1.4` for pre-5 Java and feature numbers such as `5`, `8`, `17`, and `25` thereafter. Two compatibility tests cover these outputs. Task selection, generated arguments and explicit `-sourceLevel` overrides are unchanged; no compromise was required.

#48 removes all eight Maven/SDK findings (112 → 104). A public resolved-project listener completes Maven-native resolution before import, source artifacts are retained in plugin-owned workspace state and attached as compiler class roots through the public importer adapter, and the native launcher uses public repository-library properties. Ten focused tests cover metadata/snapshot persistence, filtering/removal, unresolved artifacts, plain Maven transitives, first import/reload, cached model reapplication, and explicit source dependencies. Both complete suites pass. Test-only telemetry JAR ordering fixes Maven server startup; no telemetry JAR is added to the plugin ZIP.

Qualifications for #48: the third-party Maven embedder API is public but marked `Obsolete`; resolution is synchronous and serial; cached artifact state preserves effective resolution without cloning the private stub flag. Cached model reapplication and state round-trip are tested, while full IDE close/reopen and live authenticated/mirrored repositories remain outside this validation. See [Maven public API investigation](maven-public-api-investigation.md).

The verifier's internal-API findings match direct source dependencies, rather than demonstrated false positives. Address them by feature, preserving behavior and tests; do not mute them to obtain a passing release gate. Start with these concrete groups from the report:

| Feature and source | Reported dependency / next action |
| --- | --- |
| Remote UI: `RemoteUiConnection`, `RemoteUiLogManager`, `RemoteUiView` | Replace internal `socketConnection` transports, handlers, and externalizers behind the existing GWT message protocol. |
| JPS compilation and packaging: `GwtBuilder`, `GwtBuildTarget`, `GwtCompilerOutputLayoutElementBuilder`, `JpsGwtModulesCache` | Review internal dependency resolution, build-root/data-path implementations, artifact builder/instruction APIs, and facet serialization; find supported extension APIs or seek JetBrains guidance where no public replacement exists. |
| Maven/SDK resolution: `GwtCodehausMavenProjectResolutionContributor`, `GwtCommandLineState` | Replace the internal Maven resolution callback and repository-library descriptor. |
| Facet/run/report UI | Replace internal `LcrJavaHelper` and `RendererPresentation` usages with supported renderers; review dialogs, module-path access, and internal message bundles flagged elsewhere. |
| Optional debugging: `RemoteUiConnection`, `GwtFileUrlMapper` | Migrate internal JavaScript debugger starters and `SourceFileResolver` integration. |

JSNI completion separately uses scheduled-for-removal `AllClassesGetter` in `GwtJsniClassNameCompletionContributor`; include it in deprecation cleanup. After each migration, rerun the relevant feature tests and full verifier, preserving the complete reports to distinguish fixed issues from new ones.

## Signing

Use a signed ZIP for publication. The SDK publishing guide instructs authors to sign before publishing. Gradle's signing task is conditional and is skipped when signing material is absent, so a successful unsigned `buildPlugin` is not proof of signed-release readiness. Marketplace's own signature is distinct from the author's signature; a self-signed local ZIP may require trusting the author's certificate when installing directly. The signing guide also notes that author public-key upload is not available yet. [Publishing guide](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html), [Signing behavior](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html).

Generate and retain the private key/certificate using the official signing instructions, outside the repository. Provide `PRIVATE_KEY`, `CERTIFICATE_CHAIN`, and, for an encrypted key, `PRIVATE_KEY_PASSWORD` through your local secret manager/environment. The Gradle plugin already reads these variables. Use an invocation without the configuration cache when handling release secrets:

```shell
./gradlew --no-configuration-cache prepareMarketplaceRelease signPlugin verifyPluginSignature
```

Verify that `signPlugin` and `verifyPluginSignature` actually ran and select the signed output in `build/distributions/` for upload. Keep tokens, private keys, passwords, and secret-bearing logs out of Git and shared artifacts. [Gradle signing/publishing properties](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#signing).

## First publication — publisher action

The **first upload must be manual**. Log into [JetBrains Marketplace](https://plugins.jetbrains.com/) with the publisher account and select **Upload plugin**. Create/select the vendor, complete its verification, accept the agreement, and upload the reviewed signed ZIP. Select **Free**, Apache 2.0, the source URL, relevant tags, and accurate ads/privacy declarations. Add support/documentation links and useful screenshots; use a custom channel or hidden release if a public stable release is not intended. [Initial upload](https://plugins.jetbrains.com/docs/marketplace/uploading-a-new-plugin.html), [Listing fields](https://plugins.jetbrains.com/docs/marketplace/best-practices-for-listing.html).

Every initial release and update undergoes automated and manual review. Upload success is not approval or public availability; monitor the account/email for questions. No review deadline is guaranteed. After approval, inspect the published page and install through Marketplace in a supported IDE; record the new listing URL/numeric ID here and in the README. [Approval process](https://plugins.jetbrains.com/docs/marketplace/jetbrains-marketplace-approval-guidelines.html).

## Subsequent updates and maintenance

For each update, change the version and notes, repeat the preparation/signature/smoke checks, and upload through the existing listing. Versions must be unique. After the initial manual publication, an explicitly authorized update can instead use Gradle:

```shell
./gradlew --no-configuration-cache publishPlugin
```

The publishing task runs the local preparation gate. Supply `PUBLISH_TOKEN` from **My Tokens** in the publisher's Marketplace account, along with the signing environment above. Treat this command as an external upload; it is not part of routine validation. Default publication targets the normal Marketplace channel. [Gradle publication lifecycle](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html), [Token and channel defaults](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#publishing).

Retain versioned source, release notes, tested ZIPs, and verification reports. Review new platform releases and [API incompatibilities](https://plugins.jetbrains.com/docs/intellij/api-changes-list.html), repeat edition checks, and update the compatibility range only with supporting results. Check Marketplace review/compatibility notices, maintain working public contact links, rotate credentials when needed, and keep signing material backed up privately. Keep vendor/trader details accurate when circumstances change.

No publisher account, agreement acceptance, trader classification, live listing, signing credential, or complete redistribution audit is established by this document. Those remain publisher/release prerequisites; no external publication was performed while preparing it.
