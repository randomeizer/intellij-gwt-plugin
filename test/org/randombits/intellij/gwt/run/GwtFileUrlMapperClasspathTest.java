package org.randombits.intellij.gwt.run;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.Urls;
import org.randombits.intellij.gwt.GwtTestCase;

import java.nio.file.Files;
import java.nio.file.Path;

/** Multiple module classpaths require the heavyweight project fixture. */
public class GwtFileUrlMapperClasspathTest extends JavaCodeInsightFixtureTestCase {
  private final GwtFileUrlMapper mapper = new GwtFileUrlMapper();

  public void testDuplicateClassAndModuleNamesStayInTheirClasspath() throws Exception {
    GwtTestCase.addGwtFacet(getModule(), null);
    myFixture.addFileToProject("sample/App.gwt.xml", "<module rename-to='first'><source path='client'/></module>");
    var first = myFixture.addFileToProject("sample/client/Probe.java", "package sample.client; public class Probe {}").getVirtualFile();
    Path directory = FileUtil.createTempDirectory("gwt-other-module", "").toPath();
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.toString());
    Files.createDirectories(directory.resolve("sample/client"));
    Files.writeString(directory.resolve("sample/App.gwt.xml"), "<module rename-to='second'><source path='client'/></module>");
    Files.writeString(directory.resolve("sample/client/Probe.java"), "package sample.client; public class Probe {}");
    var root = com.intellij.openapi.vfs.LocalFileSystem.getInstance().refreshAndFindFileByNioFile(directory);
    assertNotNull(root);
    var moduleManager = com.intellij.openapi.module.ModuleManager.getInstance(getProject());
    var other = WriteAction.compute(() -> moduleManager.newModule(directory.resolve("other.iml"), "JAVA_MODULE"));
    try {
      WriteAction.run(() -> {
        var model = ModuleRootManager.getInstance(other).getModifiableModel();
        model.addContentEntry(root).addSourceFolder(root, false);
        model.commit();
      });
      com.intellij.testFramework.VfsTestUtil.syncRefresh();
      IndexingTestUtil.waitUntilIndexesAreReady(getProject());
      var second = root.findFileByRelativePath("sample/client/Probe.java");
      assertNotNull(second);
      String firstUrl = "http://localhost:9876/sourcemaps/first/sample/client/Probe.java";
      String secondUrl = "http://localhost:9876/sourcemaps/second/sample/client/Probe.java";
      assertContainsElements(mapper.getUrls(first, getProject(), null).stream().map(com.intellij.util.Url::toExternalForm).toList(), firstUrl);
      assertFalse(mapper.getUrls(first, getProject(), null).stream().map(com.intellij.util.Url::toExternalForm).toList().contains(secondUrl));
      assertContainsElements(mapper.getUrls(second, getProject(), null).stream().map(com.intellij.util.Url::toExternalForm).toList(), secondUrl);
      assertFalse(mapper.getUrls(second, getProject(), null).stream().map(com.intellij.util.Url::toExternalForm).toList().contains(firstUrl));
      assertEquals(first, mapper.getFile(Urls.parseEncoded(firstUrl), getProject(), null));
      assertEquals(second, mapper.getFile(Urls.parseEncoded(secondUrl), getProject(), null));
    }
    finally {
      WriteAction.run(() -> moduleManager.disposeModule(other));
      FileUtil.delete(directory.toFile());
    }
  }

}
