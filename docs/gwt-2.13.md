# GWT 2.13 compatibility

The plugin recognizes GWT 2.13.x as a separate version profile. It supports Java 17 client
source syntax, uses `com.google.gwt.dev.Compiler` and `com.google.gwt.dev.DevMode`, and enables
Super Dev Mode by default. New module descriptors reference the 2.13.1 DTD.

Use the `org.gwtproject:gwt-user` and `org.gwtproject:gwt-dev` artifacts at the same version
(for example, 2.13.1). Both Maven repository and Gradle cache SDK layouts are supported.

## Java runtime

GWT 2.13's compiler and development tools require Java 11 or newer. The Java 17 client source
ceiling is separate from that runtime requirement. Configure a suitable JDK for the GWT module
and run configuration. The IntelliJ plugin itself is built with Java 25 for IDEA 2026.3; that does
not raise GWT's client source ceiling.

## DevMode server behavior

With GWT 2.13, the run configuration's **Default** server serves static files. It does not run
`web.xml` servlets or GWT RPC endpoints. Projects with a Java backend must choose a servlet
container or run their backend separately.

To use the bundled legacy Jetty 9 launcher, add this to the GWT run configuration's DevMode
parameters:

```text
-server com.google.gwt.dev.shell.jetty.JettyLauncher
```

This launcher is deprecated and scheduled for removal by GWT. For an external backend, use
`-noserver`, start the backend separately, and configure the run page to point to it. A custom
`ServletContainerLauncher` can also be selected with `-server <fully.qualified.ClassName>` when
its implementation and dependencies are on the runtime classpath. The plugin does not silently
switch existing configurations to Jetty.

## Verification

Version tests cover the 2.12/2.13 boundary, 2.13 snapshots and patch releases, compiler/DevMode
capabilities, and the module DTD. SDK metadata tests read `gwt.version` from a fixture dev JAR;
resolver tests cover `org.gwtproject` coordinates for Maven and Gradle layouts.

Real 2.13.1 Maven artifacts were checked on JDK 17:

- The compiler successfully compiled a client entry point using records, text blocks, pattern
  matching, and switch expressions with `-sourceLevel 17`.
- A headless `DevMode` subclass served a static page with HTTP 200 and returned 404 for a mapped
  servlet with the default server. Adding the legacy Jetty `-server` argument returned HTTP 200
  from both the static page and the servlet.

The subclass calls GWT's protected `setHeadless(true)` hook; the normal `DevMode.main` creates a
Swing UI even with `java.awt.headless=true`. The smoke check validates server behavior through
GWT's testing hook rather than the plugin's remote-UI connection.

This is separate from running a complete application inside the IDE: browser debugging, a full
Maven/Gradle project import, and every supported runtime JDK are not covered by that smoke check.

Sources: [GWT release notes](https://www.gwtproject.org/release-notes.html#Release_Notes_2_13_0)
and the sources and metadata packaged in the 2.13.1 SDK JARs.
