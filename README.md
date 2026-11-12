# Randombits GWT Support

Standalone source for an independently maintained IntelliJ IDEA GWT plugin
(`org.randombits.gwtplugin`), extracted from
[JetBrains/intellij-obsolete-plugins](https://github.com/JetBrains/intellij-obsolete-plugins/tree/master/gwt).
The plugin adds Google Web Toolkit support to IntelliJ IDEA, including free mode in the unified product.

The [JetBrains Marketplace GWT listing](https://plugins.jetbrains.com/plugin/14590-gwt) is the
separate JetBrains plugin; this repository's plugin is not that listing or an official JetBrains
support channel.

## Compatibility and build

The current build targets IntelliJ IDEA 2026.3 (build 263) and uses Java 25.

Free mode supports GWT facets and module XML, Java/RPC inspections, UiBinder, ClientBundle,
JSNI, SDK discovery, Maven/Gradle import, compiler integration, and plain compiler-output artifact
import, validation, and setup quick fixes. JavaScript, CSS, Java
internationalization, and the bundled Jakarta EE platform API module must remain enabled.

The following integrations require an **Ultimate subscription** and their corresponding bundled
plugins enabled. Web/Servlets inspections, web roots, servlet registration, and WAR artifact import
and validation require the optional Web/Servlets plugin. Dedicated GWT DevMode/Super Dev Mode run configurations and sample
web-application setup additionally require Application Servers support. JavaScript debugging also
requires the JavaScript Debugger plugin. These features load according to available plugins;
free mode users can run applications through Maven goals or Gradle tasks. Existing web-facet
associations are preserved when editing a project without Web/Servlets support.

Older standalone Community Edition distributions are outside the supported build range. This
change supports the free unified 2026.3 product; it does not backport the plugin to older IDE APIs.

```shell
./gradlew buildPlugin
```

The distributable ZIP is written to `build/distributions/`.

For GWT 2.13 SDK support, runtime requirements, and the changed DevMode server default, see
[GWT 2.13 compatibility](docs/gwt-2.13.md).

`./gradlew check` includes `testFreeEdition`, which uses a separate IDEA sandbox with Ultimate
markers, Web/Servlets, Application Servers, and JavaScript Debugger disabled. It exercises core
module/Java assistance and checks that optional extensions are absent. The normal `test` task
checks full integration, including optional API visibility and run-configuration editor creation.
Run the focused edition checks with:

```shell
./gradlew testFreeEdition test --tests '*GwtOptionalIntegrationTest' --tests '*GwtAnnotationUtilTest'
```

These are IDE test-runtime checks, not a licensed GUI or end-to-end GWT application launch.

The optional-integration design builds on
[craigmit's implementation](https://github.com/craigmit/intellij-obsolete-plugins/commit/56d3ed5e3a123209ca7166e256f72eef5e0a8634)
and [Bohusz's 263 branch](https://github.com/Bohusz/intellij-obsolete-plugins/tree/intellij-263-upgrade_xbr/gwt),
adapted to this plugin's namespace, optional build-tool integrations, and isolated test sandboxes.
Unlike those forks, the core descriptor does not require Jakarta EE Web/Application Server API
modules: in 263 they transitively depend on subscription-only modules. An optional `GwtWebSupport`
adapter keeps those types out of core facet, editor, action, reference, and importer signatures.

## Continuous integration and releases

For the Marketplace investigation, submission requirements, and local preparation commands,
see [Marketplace publishing](docs/marketplace-publishing.md). GitHub releases and Marketplace
uploads are separate; the first Marketplace upload must be made manually.

The plugin ID is now `org.randombits.gwtplugin`. If you installed an earlier ZIP using
`org.randombits.intellij.gwt`, uninstall that copy before installing the new one to avoid
loading both integrations. The old ID is rejected for a new Marketplace listing.

GitHub Actions runs `./gradlew check buildPlugin` for pull requests and pushes to `main`, using
Java 25. Successful `main` builds upload the plugin ZIP as the `latest-main-build` workflow
artifact; pull requests do not publish artifacts. This is event-driven rather than nightly: it
tracks the most recent successful build of changed `main` code. Workflow artifacts expire based on
the repository's Actions retention setting (GitHub's default is 90 days).

The plugin version is managed by Gradle in `gradle.properties` as `pluginVersion`. The root build
uses it as the project version and requires a matching `change-notes/<pluginVersion>.html` file.
To prepare a release, update both in a pull request and merge it. The release tag must be
`v<pluginVersion>`; for example, the current version can be checked and packaged with:

```shell
./gradlew verifyReleaseTag check buildPlugin -PreleaseTag=v263.1.0
```

After merging, push an annotated tag for that version (for example,
`git tag -a v263.1.0 -m "Release v263.1.0"` followed by `git push origin v263.1.0`). The tag
triggers the release workflow, which repeats the Gradle version check and build, creates a draft
GitHub Release with the plugin ZIP and versioned change notes, then publishes it. Gradle manages
the version check and plugin package; GitHub Actions/CLI creates the GitHub Release.

The workflows need no repository secrets: the release job uses the automatically provided
`GITHUB_TOKEN` with `contents: write`, and the release monitors use `issues: write`.
Other jobs are read-only. Enabling immutable releases
in repository settings is recommended; the release workflow attaches files before publishing.

### GWT release monitoring

The **Check GWT releases** workflow checks the official
[GWT release notes](https://www.gwtproject.org/release-notes.html) daily at 08:17 Brisbane time.
It opens one issue as `github-actions[bot]` for each stable release newer than
`latestVerifiedVersion` in [the monitor configuration](.github/gwt-release-monitor.json),
initially **2.13.1**, based on the [documented SDK verification](docs/gwt-2.13.md).
Patch releases are included; release candidates, betas, and snapshots are excluded. All newer
releases are checked so releases published between scheduled runs are not missed.

The record means compatibility has been explicitly verified through that version. The monitor
does not infer support from version detection code: future SDKs can fall back to the latest
profile without having been tested. When completing a compatibility ticket, update the record
in the same pull request after reviewing all intervening stable releases. Detection does not
prove that plugin changes are required, and the record describes the checked-in source rather
than confirming that a plugin release has been published.

Open and closed issues are checked before creation. A hidden version marker survives ticket
renaming; an exact `Add support for GWT <version>` title also counts for manually created issues.
Closing a ticket keeps it from being recreated even if the verification record has not advanced.
The workflow never advances the record or closes tickets automatically. Network errors and an
unrecognized page format fail the run rather than reporting that no releases exist.

The schedule becomes active once the workflow is on the default branch. It can also be run
manually from Actions. GitHub may delay scheduled runs and disables schedules in public
repositories after 60 days without repository activity; see
[GitHub's schedule documentation](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).
Pull requests affecting the monitor run its tests without creating issues. Local checks need only
Python 3; the dry run fetches the release page but never accesses GitHub:

```shell
python3 -m unittest discover -s .github/scripts -p 'test_*.py' -v
python3 .github/scripts/check_gwt_releases.py --dry-run
```

### IntelliJ IDEA release monitoring

The **Check IntelliJ releases** workflow checks the official
[JetBrains release feed](https://data.services.jetbrains.com/products/releases?code=IIU&type=release)
daily at 08:37 Brisbane time. It tracks stable major/minor lines such as `2026.3` and `2027.1`,
ignoring patch changes such as `2026.3.1`. EAP, beta, and release candidate builds are excluded.
The feed's `IIU` product code covers the unified IntelliJ IDEA product used by this build.

The baseline comes directly from `platformVersion` in [gradle.properties](gradle.properties),
currently `2026.3`. Its patch component, if present, is ignored. Each newer stable line gets one
`Add support for IntelliJ IDEA <year.minor>` issue as `github-actions[bot]`, with release notes
and a compatibility checklist. If the first observed release for a new line is already a patch,
the monitor still creates the line's ticket. It checks all lines rather than only the newest.

As with GWT, open and closed tickets prevent duplicates, including renamed tickets retaining
their hidden version marker. An exact manual ticket title also counts. Upgrade `platformVersion`
as part of completing the compatibility work; the monitor does not change it automatically.
This tracks the checked-in platform target and does not prove that a compatible plugin package
has already been published. Both monitors share the GitHub issue lookup and pagination code.

The same default-branch scheduling and inactivity limitations described above apply. Manual
runs are available in Actions, and pull requests affecting the monitor or `gradle.properties`
run the tests without creating tickets. Run a local check without accessing GitHub with:

```shell
python3 .github/scripts/check_intellij_releases.py --dry-run
```

## License and contributions

The source is licensed under the Apache License 2.0; see [LICENSE.txt](LICENSE.txt). Existing
JetBrains copyright notices are retained.

Contributions are welcome. Consistent with the original repository's guidance, contributions
should be licensed under an open-source license with no copyleft effect. Pull requests are
voluntary and do not imply compensation or a commitment to accept or release the changes.

The plugin is provided "AS IS" and "AS AVAILABLE". Compatibility and maintenance are not
guaranteed.
