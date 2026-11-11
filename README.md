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

## License and contributions

The source is licensed under the Apache License 2.0; see [LICENSE.txt](LICENSE.txt). Existing
JetBrains copyright notices are retained.

Contributions are welcome. Consistent with the original repository's guidance, contributions
should be licensed under an open-source license with no copyleft effect. Pull requests are
voluntary and do not imply compensation or a commitment to accept or release the changes.

The plugin is provided "AS IS" and "AS AVAILABLE". Compatibility and maintenance are not
guaranteed.
