package org.randombits.intellij.gwt;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.codeInspection.LocalInspectionEP;
import org.randombits.intellij.gwt.run.GwtDevModeServerProvider;
import org.randombits.intellij.gwt.run.GwtRunConfigurationType;
import org.randombits.intellij.gwt.run.GwtRunConfigurationEditor;
import org.randombits.intellij.gwt.web.GwtWebUtil;
import org.randombits.intellij.gwt.web.GwtWebSupport;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.io.File;

public class GwtOptionalIntegrationTest extends LightJavaCodeInsightFixtureTestCase {
  public void testCopiesWebXmlForGwtTests() throws Exception {
    var facet = GwtTestCase.addGwtFacet(getModule(), null);
    var directory = FileUtil.createTempDirectory("gwt-tests", "web-xml");
    try {
      var source = new File(directory, "web/WEB-INF/web.xml");
      FileUtil.writeToFile(source, "<web-app/>");
      VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.getAbsolutePath());
      assertNotNull(LocalFileSystem.getInstance().refreshAndFindFileByIoFile(source));
      GwtWebTestUtil.addWebFacet(facet, directory.getAbsolutePath(), "web/WEB-INF/web.xml");
      var support = GwtWebSupport.getInstance();
      assertNotNull(support);
      var output = new File(directory, "output");
      support.copyTestWebXml(facet, output);
      assertEquals(FileUtil.loadFile(source), FileUtil.loadFile(new File(output, "WEB-INF/web.xml")));
    }
    finally {
      FileUtil.delete(directory);
    }
  }

  public void testOptionalClassesAreVisibleToPluginClassLoader() throws Exception {
    var plugin = PluginManagerCore.getPlugin(PluginId.getId("org.randombits.intellij.gwt"));
    assertNotNull(plugin);
    ClassLoader loader = plugin.getPluginClassLoader();
    assertNotNull(Class.forName("com.intellij.javaee.ui.packaging.ExplodedWarArtifactType", true, loader));
    assertNotNull(Class.forName("org.jetbrains.debugger.sourcemap.SourceFileResolver", true, loader));
  }

  public void testRunConfigurationRemainsAvailableWithWebSupport() {
    assertTrue(GwtWebUtil.isWebSupportAvailable());
    assertTrue(LocalInspectionEP.LOCAL_INSPECTION.getExtensionList().stream()
                 .anyMatch(inspection -> "RandombitsGwtServiceNotRegistered".equals(inspection.shortName)));
    assertFalse(GwtDevModeServerProvider.EP_NAME.getExtensionList().isEmpty());
    var configuration = GwtRunConfigurationType.getFactory().createTemplateConfiguration(getProject());
    assertNotNull(configuration);
    var editor = new GwtRunConfigurationEditor(getProject());
    try {
      assertNotNull(editor.getComponent());
    }
    finally {
      Disposer.dispose(editor);
    }
  }
}
