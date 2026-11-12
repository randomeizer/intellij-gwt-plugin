// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

fun properties(key: String) = project.findProperty(key).toString()

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

val pluginVersion = properties("pluginVersion")
val changeNotesFile = layout.projectDirectory.file("change-notes/$pluginVersion.html")
require(changeNotesFile.asFile.isFile) {
    "Missing change notes for plugin version $pluginVersion: ${changeNotesFile.asFile}"
}

group = "org.randombits.intellij.gwt"
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea(properties("platformVersion"))

        // hard dependencies (see resources/META-INF/plugin.xml <dependencies>)
        bundledPlugin("com.intellij.java")
        bundledPlugin("com.intellij.xml")
        bundledPlugin("JavaScript")
        bundledPlugin("com.intellij.css")
        bundledPlugin("com.intellij.java-i18n")
        // Free Jakarta EE API modules; Web/Servlets implementations remain optional below.
        bundledPlugin("com.intellij.javaee")

        // Optional integrations (compile against their APIs without requiring them at runtime).
        bundledPlugin("com.intellij.javaee.app.servers.integration")
        bundledPlugin("com.intellij.javaee.web")

        // optional dependencies
        bundledPlugin("org.jetbrains.idea.maven")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("org.jetbrains.plugins.gradle")
        bundledPlugin("JavaScriptDebugger")
        bundledModule("intellij.platform.scriptDebugger.backend")

        // plugin content modules
        pluginModule(project(":spring"))
        pluginModule(project(":dev"))

        // additional plugins required for testing
        // (org.jetbrains.idea.maven is already provided by the bundledPlugin declaration above)
        testBundledPlugin("com.intellij.spring")
        testBundledPlugin("com.intellij.spring.mvc")
        testBundledPlugin("com.intellij.javaee")

        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
        testFramework(TestFrameworkType.Plugin.Maven)
        testFramework(TestFrameworkType.Plugin.ExternalSystem)
        testFramework(TestFrameworkType.JUnit5)

        pluginVerifier()
        zipSigner()
    }

    // protobuf-lite runtime for the generated RemoteMessageProto sources
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")

    // main sources reference org.randombits.intellij.gwt.jps.* classes at compile time;
    // runtimeOnly ships gwt-jps into the distribution for the external build process.
    compileOnly(project(":jps-plugin"))
    runtimeOnly(project(":jps-plugin"))
    implementation(project(":runtime"))

    testImplementation("junit:junit:4.13.2")
    // SDK path tests exercise the Maven and Gradle resolvers from the JPS module.
    testImplementation(project(":jps-plugin"))
    // Match the JUnit runtime used by the 263 test framework.
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
}

kotlin {
    jvmToolchain(25)
}

java.sourceSets["main"].java {
    srcDir("src")
    srcDir("remoteUi/generated-source")
}

java.sourceSets["main"].resources {
    srcDir("resources")
}

// Active test sources. Tests that depend on JetBrains test frameworks not distributed with the
// released IDE (JavaEE-web, JavaScript, Spring, Grazie, artifact-compiler test bases) live under
// test-quarantined/ and are intentionally excluded from compilation. See test-quarantined/README.md.
java.sourceSets["test"].java {
    srcDir("test")
}

intellijPlatform {
    // GUI Designer form code is inlined in the dialog constructors (bound fields are final),
    // so bytecode form instrumentation is neither needed nor compatible.
    instrumentCode = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = properties("pluginSinceBuild")
            // Claim only the platform line we build and verify against.
            untilBuild = "${properties("pluginSinceBuild").substringBefore('.')}.*"
        }

        changeNotes = providers.fileContents(changeNotesFile).asText.get()
    }

    pluginVerification {
        ides {
            current()
        }
        failureLevel = listOf(
            VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
            VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
            VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
            VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
            VerifyPluginTask.FailureLevel.INVALID_PLUGIN,
        )
    }
}

val prepareMarketplaceRelease by tasks.registering {
    group = "distribution"
    description = "Tests, packages, and verifies the plugin for manual Marketplace submission."
    dependsOn("check", "buildPlugin", "verifyPluginStructure", "verifyPlugin")
}

tasks.named("publishPlugin") {
    dependsOn(prepareMarketplaceRelease)
}

tasks.register("verifyReleaseTag") {
    group = "verification"
    description = "Checks that the supplied release tag matches the current plugin version."

    val actualTag = providers.gradleProperty("releaseTag").orNull
    val expectedTag = "v$pluginVersion"
    inputs.property("releaseTag", actualTag ?: "")

    doLast {
        if (actualTag == null) {
            throw GradleException("Pass -PreleaseTag=$expectedTag to validate a release tag.")
        }
        if (actualTag != expectedTag) {
            throw GradleException("Release tag '$actualTag' does not match the expected tag '$expectedTag'.")
        }
    }
}

// Dedicated sandbox: exercise the same unified product without subscription-only integrations.
intellijPlatformTesting.testIde.register("testFreeEdition") {
    listOf(
        TestFrameworkType.Platform,
        TestFrameworkType.Plugin.Java,
        TestFrameworkType.Plugin.Maven,
        TestFrameworkType.Plugin.ExternalSystem,
        TestFrameworkType.JUnit5,
    ).forEach { testFramework(it) }
    plugins {
        disablePlugins(listOf(
            "com.intellij.modules.ultimate",
            "com.intellij.modules.idea.ultimate",
            "com.intellij.javaee.web",
            "com.intellij.javaee.app.servers.integration",
            "JavaScriptDebugger",
        ))
    }
    task {
        useJUnitPlatform()
        testClassesDirs = sourceSets["test"].output.classesDirs
        // Retain this task's SDK/sandbox classpath, then add the test dependencies.
        classpath += sourceSets["test"].runtimeClasspath
        systemProperty("gwt.test.data.path", layout.projectDirectory.dir("testData").asFile.absolutePath)
        filter {
            includeTestsMatching("org.randombits.intellij.gwt.GwtFreeEditionTest")
            includeTestsMatching("org.randombits.intellij.gwt.GwtCoreIntegrationTest")
            includeTestsMatching("org.randombits.intellij.gwt.GwtArtifactIntegrationTest")
            includeTestsMatching("org.randombits.intellij.gwt.GwtAnnotationUtilTest")
            includeTestsMatching("org.randombits.intellij.gwt.model.GwtModuleTest")
            includeTestsMatching("org.randombits.intellij.gwt.model.GwtSourceTagTest")
            includeTestsMatching("org.randombits.intellij.gwt.model.GwtSuperSourceElementFinderTest")
            includeTestsMatching("org.randombits.intellij.gwt.inspections.GwtInspectionsTest")
        }
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = properties("platformJavaVersion")
        targetCompatibility = properties("platformJavaVersion")
    }

    test {
        useJUnitPlatform()
        inputs.dir(layout.projectDirectory.dir("testData")).withPropertyName("gwtTestData")
        inputs.property("gwtRemoteUiSmokeJars", providers.environmentVariable("GWT_REMOTE_UI_SMOKE_JARS").orElse(""))
        inputs.property("gwtJpsSmokeRepository", providers.environmentVariable("GWT_JPS_SMOKE_REPOSITORY").orElse(""))
        inputs.property("gwtJpsLegacyPlugin", providers.environmentVariable("GWT_JPS_SMOKE_LEGACY_PLUGIN_JAR").orElse(""))
        classpath += sourceSets["test"].runtimeClasspath
        // Maven's forked server locates telemetry by owning JAR. Prefer the SDK's
        // merged JAR over the Maven test framework's split telemetry dependencies.
        classpath = files(intellijPlatform.platformPath.resolve("lib/intellij.libraries.opentelemetry.jar")) + classpath
        exclude("**/GwtFreeEditionTest.class")
        providers.gradleProperty("test.idea.home.path").orNull?.takeIf { it.isNotBlank() }?.let {
            systemProperty("idea.home.path", it)
        }
        // GWT tests resolve fixtures relative to this directory (see GwtTestCase.getGwtTestDataPath)
        systemProperty("gwt.test.data.path", layout.projectDirectory.dir("testData").asFile.absolutePath)
    }

    check {
        dependsOn("testFreeEdition")
    }

    // Include distribution licenses and the legacy SuperDevMode launcher.
    withType<PrepareSandboxTask> {
        from(listOf(
            layout.projectDirectory.file("LICENSE.txt"),
            layout.projectDirectory.file("THIRD_PARTY_NOTICES.txt"),
        )) {
            into(pluginName)
        }
        from(layout.projectDirectory.dir("lib")) {
            into(pluginName.map { "$it/lib/" })
            include("*.jar")
        }
    }
}
