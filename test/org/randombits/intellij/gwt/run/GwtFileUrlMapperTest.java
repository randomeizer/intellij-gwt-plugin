package org.randombits.intellij.gwt.run;

import com.google.gson.Gson;
import com.intellij.execution.RunManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.debugger.sourcemap.SourceMapDecoderKt;
import org.jetbrains.debugger.sourcemap.SourceResolver;
import org.randombits.intellij.gwt.GwtTestCase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GwtFileUrlMapperTest extends LightJavaCodeInsightFixtureTestCase {
  private final GwtFileUrlMapper mapper = new GwtFileUrlMapper();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    GwtTestCase.addGwtFacet(getModule(), null);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      var manager = RunManager.getInstance(getProject());
      for (var settings : manager.getAllSettings()) if (settings.getName().startsWith("Mapping fixture")) manager.removeConfiguration(settings);
      WriteAction.run(() -> {
        var roots = ModuleRootManager.getInstance(getModule()).getModifiableModel();
        for (var library : roots.getModuleLibraryTable().getLibraries()) {
          if ("Mapping fixture".equals(library.getName())) roots.getModuleLibraryTable().removeLibrary(library);
        }
        roots.commit();
      });
    }
    finally { super.tearDown(); }
  }

  public void testDirectAndTransitiveRenamedModulesBindWithoutFallbackResolver() {
    module("lib/Library.gwt.xml", "<module rename-to='library'><source path='client'/></module>");
    module("mid/Middle.gwt.xml", "<module rename-to='middle'><inherits name='lib.Library'/></module>");
    module("app/App.gwt.xml", "<module rename-to='renamed'><inherits name='mid.Middle'/></module>");
    var file = myFixture.addFileToProject("lib/client/Probe.java", "package lib.client; public class Probe {}").getVirtualFile();
    ready();
    var urls = urls(file);
    assertContainsElements(urls, "http://localhost:9876/sourcemaps/library/lib/client/Probe.java",
      "http://localhost:9876/sourcemaps/middle/lib/client/Probe.java", "http://localhost:9876/sourcemaps/renamed/lib/client/Probe.java");
    assertExactLookup(file, "http://localhost:9876/sourcemaps/renamed/lib/client/Probe.java");
    assertEquals(file, mapper.getFile(Urls.parseEncoded("http://localhost:9876/sourcemaps/renamed/lib/client/Probe.java"), getProject(), null));
  }

  public void testSuperSourcePhysicalPathAndFilters() {
    module("lib/Library.gwt.xml", "<module><super-source path='super' excludes='**/Excluded.java'/></module>");
    module("app/App.gwt.xml", "<module rename-to='app'><inherits name='lib.Library'/></module>");
    var source = myFixture.addFileToProject("lib/super/probe/Shared.java", "package probe; public class Shared {}").getVirtualFile();
    var excluded = myFixture.addFileToProject("lib/super/probe/Excluded.java", "package probe; public class Excluded {}").getVirtualFile();
    ready();
    String expected = "http://localhost:9876/sourcemaps/app/lib/super/probe/Shared.java";
    assertContainsElements(urls(source), expected);
    assertFalse(urls(source).contains("http://localhost:9876/sourcemaps/app/probe/Shared.java"));
    assertEmpty(urls(excluded));
    assertExactLookup(source, expected);
    assertEquals(source, mapper.getFile(Urls.parseEncoded(expected), getProject(), null));
    assertNotNull(mapper.getNavigatable(Urls.parseEncoded(expected), getProject(), null));
  }

  public void testConfiguredOriginsRefreshAndDoNotUseWebServerPort() {
    module("app/App.gwt.xml", "<module rename-to='app'><source path='client'/></module>");
    var file = myFixture.addFileToProject("app/client/Probe.java", "package app.client; public class Probe {}").getVirtualFile();
    var manager = RunManager.getInstance(getProject());
    var settings = manager.createConfiguration("Mapping fixture", GwtRunConfigurationType.getFactory());
    var configuration = (GwtRunConfiguration)settings.getConfiguration();
    configuration.getGwtState().SHELL_PARAMETERS = "-port 8880 -codeServerPort 9017 -bindAddress 127.0.0.2";
    manager.addConfiguration(settings);
    ready();
    var first = mapper.getUrls(file, getProject(), "page.example:8880").stream().map(Url::toExternalForm).toList();
    assertContainsElements(first, "http://127.0.0.2:9017/sourcemaps/app/app/client/Probe.java", "http://page.example:9017/sourcemaps/app/app/client/Probe.java");
    assertFalse(first.contains("http://localhost:8880/sourcemaps/app/app/client/Probe.java"));
    assertEquals(first.size(), first.stream().distinct().count());
    configuration.getGwtState().SHELL_PARAMETERS = "-codeServerPort=9018";
    assertContainsElements(urls(file), "http://localhost:9018/sourcemaps/app/app/client/Probe.java");
    assertFalse(urls(file).contains("http://localhost:9017/sourcemaps/app/app/client/Probe.java"));
  }

  public void testEmbeddedJarSourcesAndAttachedSources() throws Exception {
    String xml = "<module><source path='client'/></module>";
    String java = "package lib.client; public class Probe {}";
    var library = addLibrary(Map.of("lib/Library.gwt.xml", xml, "lib/client/Probe.java", java),
      Map.of("lib/client/Probe.java", java));
    module("app/App.gwt.xml", "<module rename-to='app'><inherits name='lib.Library'/></module>");
    ready();
    var embedded = library.get(0).findFileByRelativePath("lib/client/Probe.java");
    var attached = library.get(1).findFileByRelativePath("lib/client/Probe.java");
    assertNotNull(embedded);
    assertNotNull(attached);
    String expected = "http://localhost:9876/sourcemaps/app/lib/client/Probe.java";
    assertContainsElements(urls(embedded), expected);
    assertContainsElements(urls(attached), expected);
    assertExactLookup(attached, expected);
    assertEquals(attached, mapper.getFile(Urls.parseEncoded(expected), getProject(), null));
  }

  public void testNonClientAndInvalidPathsAreNotMapped() {
    module("app/App.gwt.xml", "<module rename-to='app'><source path='client'/></module>");
    var server = myFixture.addFileToProject("app/server/Server.java", "package app.server; public class Server {}").getVirtualFile();
    var text = myFixture.addFileToProject("app/client/readme.txt", "text").getVirtualFile();
    ready();
    assertEmpty(urls(server));
    assertEmpty(urls(text));
    assertNull(mapper.getFile(Urls.parseEncoded("http://localhost:9876/sourcemaps/app/../app/client/Probe.java"), getProject(), null));
  }

  private void module(String path, String xml) { myFixture.addFileToProject(path, xml); }
  private void ready() { IndexingTestUtil.waitUntilIndexesAreReady(getProject()); }
  private List<String> urls(VirtualFile file) {
    return mapper.getUrls(file, getProject(), null).stream().map(Url::toExternalForm).toList();
  }

  private void assertExactLookup(VirtualFile file, String sourceUrl) {
    String json = new Gson().toJson(Map.of("version", 3, "sources", List.of(sourceUrl), "names", List.of(), "mappings", ";AAEA"));
    var map = SourceMapDecoderKt.decodeSourceMap(json, sources -> new SourceResolver(sources,
      Urls.parseEncoded("http://localhost:9876/app.js.map"), true, false));
    assertNotNull(map);
    var alias = mapper.getUrls(file, getProject(), null).stream().filter(url -> url.toExternalForm().equals(sourceUrl)).findFirst().orElseThrow();
    var locations = map.getSourceMappingsInLine(alias, 2, null, null, false);
    assertTrue("Native lookup must match exact URL without a source file or custom resolver", locations.iterator().hasNext());
    assertEquals(1, locations.iterator().next().getGeneratedLine());
  }

  private List<VirtualFile> addLibrary(Map<String, String> binary, Map<String, String> sources) throws Exception {
    Path directory = FileUtil.createTempDirectory("gwt-map-library", "").toPath();
    Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory.toFile()));
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.toString());
    List<VirtualFile> roots = new ArrayList<>();
    for (var entries : List.of(binary, sources)) {
      Path path = directory.resolve("fixture-" + roots.size() + ".jar");
      try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
        for (var entry : entries.entrySet()) {
          zip.putNextEntry(new ZipEntry(entry.getKey()));
          zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
          zip.closeEntry();
        }
      }
      var root = JarFileSystem.getInstance().refreshAndFindFileByPath(path + "!/");
      assertNotNull(root);
      roots.add(root);
    }
    WriteAction.run(() -> {
      var model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      var library = model.getModuleLibraryTable().createLibrary("Mapping fixture");
      var changes = library.getModifiableModel();
      changes.addRoot(roots.get(0), OrderRootType.CLASSES);
      changes.addRoot(roots.get(1), OrderRootType.SOURCES);
      changes.commit();
      model.commit();
    });
    return roots;
  }
}
