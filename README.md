# Randombits GWT Support

Standalone source for an independently maintained IntelliJ IDEA GWT plugin
(`org.randombits.intellij.gwt`), extracted from
[JetBrains/intellij-obsolete-plugins](https://github.com/JetBrains/intellij-obsolete-plugins/tree/master/gwt).
The plugin adds Google Web Toolkit support to IntelliJ IDEA Ultimate.

The [JetBrains Marketplace GWT listing](https://plugins.jetbrains.com/plugin/14590-gwt) is the
separate JetBrains plugin; this repository's plugin is not that listing or an official JetBrains
support channel.

## Compatibility and build

The current build targets IntelliJ IDEA 2026.3 (build 263) and uses Java 25.

```shell
./gradlew buildPlugin
```

The distributable ZIP is written to `build/distributions/`.

For GWT 2.13 SDK support, runtime requirements, and the changed DevMode server default, see
[GWT 2.13 compatibility](docs/gwt-2.13.md).

## Continuous integration and releases

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
./gradlew verifyReleaseTag check buildPlugin -PreleaseTag=v263.0.0
```

After merging, push an annotated tag for that version (for example,
`git tag -a v263.0.0 -m "Release v263.0.0"` followed by `git push origin v263.0.0`). The tag
triggers the release workflow, which repeats the Gradle version check and build, creates a draft
GitHub Release with the plugin ZIP and versioned change notes, then publishes it. Gradle manages
the version check and plugin package; GitHub Actions/CLI creates the GitHub Release.

The workflows need no repository secrets: the release job uses the automatically provided
`GITHUB_TOKEN` with `contents: write`, while other jobs are read-only. Enabling immutable releases
in repository settings is recommended; the release workflow attaches files before publishing.

## License and contributions

The source is licensed under the Apache License 2.0; see [LICENSE.txt](LICENSE.txt). Existing
JetBrains copyright notices are retained.

Contributions are welcome. Consistent with the original repository's guidance, contributions
should be licensed under an open-source license with no copyleft effect. Pull requests are
voluntary and do not imply compensation or a commitment to accept or release the changes.

The plugin is provided "AS IS" and "AS AVAILABLE". Compatibility and maintenance are not
guaranteed.
