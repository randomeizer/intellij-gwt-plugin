package org.randombits.intellij.gwt.maven

import com.intellij.maven.testFramework.fixtures.awaitConfiguration
import com.intellij.maven.testFramework.fixtures.importProjectAsync
import com.intellij.maven.testFramework.fixtures.mavenGeneralSettings
import com.intellij.maven.testFramework.fixtures.mavenImportingFixture
import com.intellij.maven.testFramework.fixtures.updateAllProjectsFullSync
import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.xmlb.XmlSerializer
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProject
import org.jetbrains.idea.maven.project.MavenProjectsTree
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.randombits.intellij.gwt.sdk.GwtDependenciesStorage
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@TestApplication
class GwtCodehausSourcesImportTest {
  private val maven by mavenImportingFixture(mavenVersion = "bundled", skipPluginResolution = true)

  @Test
  fun `plain Maven resolves transitive dependencies without GWT plugin configuration`() = runBlocking {
    prepareRepository()
    maven.importProjectAsync("""
      <groupId>fixture</groupId><artifactId>plain-app</artifactId><version>1</version>
      <dependencies><dependency><groupId>org.gwtproject</groupId><artifactId>gwt-dev</artifactId>
        <version>2.13.0</version></dependency></dependencies>
    """.trimIndent())
    maven.awaitConfiguration()
    assertTrue(classRoots().any { it.endsWith("transitive-1.jar!/") }, "The Maven server must resolve transitive dependencies")
  }

  @Test
  fun `source jars survive reload and cached import and disappear when configuration is removed`() = runBlocking {
    val repository = prepareRepository()
    val sourceUrl = VfsUtil.getUrlForLibraryRoot(repository.resolve("fixture/library/1/library-1-sources.jar").toFile())
    val resolvedEvents = AtomicInteger()
    val connection = maven.project.messageBus.connect()
    connection.subscribe(MavenProjectsTree.Listener.TOPIC, object : MavenProjectsTree.Listener {
      override fun projectsResolved(projects: List<MavenProject>) {
        resolvedEvents.incrementAndGet()
      }
    })
    try {
      maven.importProjectAsync(pom(requestSources = true))
      maven.awaitConfiguration()
      assertTrue(classRoots().contains(sourceUrl), "GWT source jar must be a compiler classpath root")
      val dependencies = GwtDependenciesStorage.getInstance(maven.project)
        .getDependenciesPaths("org.gwtproject", "gwt-dev", "2.13.0")
      assertTrue(dependencies.any { it.endsWith("transitive-1.jar") }, "Maven transitive SDK dependencies must be retained")

      maven.updateAllProjectsFullSync()
      maven.awaitConfiguration()
      assertTrue(classRoots().contains(sourceUrl))
      assertNoDuplicateSourceEntries(sourceUrl)

      val storage = GwtMavenSourceArtifacts.getInstance(maven.project)
      val restored = GwtMavenSourceArtifacts()
      restored.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(storage.state), GwtMavenSourceArtifacts.ArtifactsState::class.java))
      storage.loadState(restored.state)
      val beforeCachedImport = resolvedEvents.get()
      // Test-only SDK orchestration: reapply cached models without another Maven resolve.
      maven.projectsManager.importMavenProjects(maven.projectsManager.projects)
      maven.awaitConfiguration()
      assertEquals(beforeCachedImport, resolvedEvents.get(), "Cached import must not need a fresh resolution event")
      assertTrue(classRoots().contains(sourceUrl))

      maven.importProjectAsync(pom(requestSources = false))
      maven.awaitConfiguration()
      assertFalse(classRoots().contains(sourceUrl), "Removed source configuration must not leave stale compiler roots")
      assertTrue(classRoots().any { it.endsWith("library-1.jar!/") }, "The declared binary dependency must remain")
    }
    finally {
      connection.disconnect()
    }
  }

  @Test
  fun `explicit sources dependency is retained without duplicate plugin entries`() = runBlocking {
    val repository = prepareRepository()
    val sourceUrl = VfsUtil.getUrlForLibraryRoot(repository.resolve("fixture/library/1/library-1-sources.jar").toFile())
    maven.importProjectAsync(pom(requestSources = true, explicitSources = true))
    maven.awaitConfiguration()
    assertTrue(classRoots().contains(sourceUrl))
    assertNoDuplicateSourceEntries(sourceUrl)

    maven.importProjectAsync(pom(requestSources = false, explicitSources = true))
    maven.awaitConfiguration()
    assertTrue(classRoots().contains(sourceUrl), "An explicit POM source dependency must not be removed")
    assertNoDuplicateSourceEntries(sourceUrl)
  }

  @Test
  fun `missing sources remain unresolved without losing binary dependencies`() = runBlocking {
    prepareRepository(includeSources = false)
    maven.importProjectAsync(pom(requestSources = true))
    maven.awaitConfiguration()
    val sources = GwtMavenSourceArtifacts.getInstance(maven.project).state.artifacts
    assertTrue(sources.any { it.classifier == "sources" && !it.resolved }, "Missing sources must retain unresolved artifact metadata")
    assertTrue(classRoots().any { it.endsWith("library-1.jar!/") }, "Missing sources must not remove the declared binary")
  }

  private fun prepareRepository(includeSources: Boolean = true): Path {
    val repository = maven.dir.resolve("offline-repository")
    maven.repositoryPath = repository
    maven.mavenGeneralSettings.isWorkOffline = true
    installJar(repository, "fixture", "transitive", "1")
    installJar(repository, "org.gwtproject", "gwt-user", "2.13.0")
    installJar(repository, "org.gwtproject", "gwt-dev", "2.13.0", dependencies = """
      <dependencies><dependency><groupId>fixture</groupId><artifactId>transitive</artifactId><version>1</version></dependency></dependencies>
    """.trimIndent())
    installJar(repository, "fixture", "library", "1")
    if (includeSources) installJar(repository, "fixture", "library", "1", classifier = "sources")
    return repository
  }

  private suspend fun classRoots(): Set<String> = readAction {
    ModuleManager.getInstance(maven.project).modules.flatMap { module ->
      ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()
        .flatMap { it.library?.getUrls(OrderRootType.CLASSES)?.toList().orEmpty() }
    }.toSet()
  }

  private suspend fun assertNoDuplicateSourceEntries(sourceUrl: String) = readAction {
    for (module in ModuleManager.getInstance(maven.project).modules) {
      val entries = ModuleRootManager.getInstance(module).orderEntries.filterIsInstance<LibraryOrderEntry>()
        .count { it.library?.getUrls(OrderRootType.CLASSES)?.contains(sourceUrl) == true }
      assertTrue(entries <= 1, "Repeated imports must not duplicate source libraries in ${module.name}")
    }
  }

  private fun pom(requestSources: Boolean, explicitSources: Boolean = false): String = """
    <groupId>fixture</groupId><artifactId>sample-app</artifactId><version>1</version>
    <dependencies>
      <dependency><groupId>org.gwtproject</groupId><artifactId>gwt-user</artifactId><version>2.13.0</version></dependency>
      <dependency><groupId>org.gwtproject</groupId><artifactId>gwt-dev</artifactId><version>2.13.0</version></dependency>
      <dependency><groupId>fixture</groupId><artifactId>library</artifactId><version>1</version></dependency>
      ${if (explicitSources) "<dependency><groupId>fixture</groupId><artifactId>library</artifactId><version>1</version><classifier>sources</classifier></dependency>" else ""}
    </dependencies>
    <build><plugins><plugin><groupId>org.codehaus.mojo</groupId><artifactId>gwt-maven-plugin</artifactId><version>2.10.0</version>
      <configuration><gwtVersion>2.13.0</gwtVersion>
        ${if (requestSources) "<compileSourcesArtifacts><artifact>fixture:library</artifact></compileSourcesArtifacts>" else ""}
      </configuration>
    </plugin></plugins></build>
  """.trimIndent()

  private fun installJar(repository: Path, group: String, artifact: String, version: String,
                         classifier: String? = null, dependencies: String = "") {
    val directory = repository.resolve(group.replace('.', '/')).resolve(artifact).resolve(version)
    Files.createDirectories(directory)
    val pom = directory.resolve("$artifact-$version.pom")
    if (!Files.exists(pom)) Files.writeString(pom, """
      <project><modelVersion>4.0.0</modelVersion><groupId>$group</groupId><artifactId>$artifact</artifactId>
        <version>$version</version>$dependencies</project>
    """.trimIndent())
    val suffix = classifier?.let { "-$it" } ?: ""
    JarOutputStream(Files.newOutputStream(directory.resolve("$artifact-$version$suffix.jar"))).use {
      it.putNextEntry(JarEntry(if (classifier == "sources") "fixture/Example.java" else "fixture.txt"))
      it.write((if (classifier == "sources") "package fixture; public class Example {}" else "fixture").toByteArray())
      it.closeEntry()
    }
  }
}
