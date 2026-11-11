package org.randombits.intellij.gwt;

import com.intellij.execution.JUnitPatcher;
import com.intellij.execution.configurations.JavaParameters;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ui.UIUtil;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.facet.GwtFacetEditor;
import org.randombits.intellij.gwt.junit.GwtJUnitPatcher;
import org.randombits.intellij.gwt.make.GwtCompilerPaths;
import org.randombits.intellij.gwt.module.GwtModulesManager;
import org.randombits.intellij.gwt.run.GwtClasspathUtil;
import org.randombits.intellij.gwt.web.GwtWebUtil;

import javax.swing.JComponent;
import javax.swing.JTable;
import java.awt.Component;
import java.lang.reflect.Proxy;

/** Exercise Web-independent integrations in both the full and free IDEA runtimes. */
public class GwtCoreIntegrationTest extends LightJavaCodeInsightFixtureTestCase {
  private GwtFacet facet;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    facet = GwtTestCase.addGwtFacet(getModule(), null);
    myFixture.addClass("package sample.client; public class Client {}");
    myFixture.addFileToProject("sample/App.gwt.xml",
      "<module><source path=\"client\"/><entry-point class=\"sample.client.Client\"/></module>");
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  public void testGwtJUnitParametersArePatched() {
    myFixture.addClass("package com.google.gwt.junit.client; public abstract class GWTTestCase {}");
    myFixture.addClass("package sample.client; public class ClientTest extends com.google.gwt.junit.client.GWTTestCase {}");
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
    JUnitPatcher patcher = JUnitPatcher.JUNIT_PATCHER_EP.getExtensionList().stream()
      .filter(GwtJUnitPatcher.class::isInstance).findFirst().orElse(null);
    assertNotNull("Core GWT JUnit patcher must be registered", patcher);

    JavaParameters parameters = new JavaParameters();
    patcher.patchJavaParameters(getModule(), parameters);

    var sources = GwtClasspathUtil.getSourceRootsOfGwtModules(getModule(), false).getPathList();
    assertFalse(sources.isEmpty());
    assertTrue(parameters.getClassPath().getPathList().containsAll(sources));
    assertContainsElements(parameters.getClassPath().getPathList(), facet.getConfiguration().getSdk().getDevJarPath());
    String arguments = parameters.getVMParametersList().getPropertyValue("gwt.args");
    assertNotNull(arguments);
    assertTrue(arguments, arguments.contains("-gen \"" + GwtCompilerPaths.getTestGenDirectory(getModule()).getAbsolutePath() + "\""));
    assertTrue(arguments, arguments.contains(facet.getSdkVersion().getCompilerOutputDirParameterName() + " \"" +
      GwtCompilerPaths.getTestOutputDirectory(getModule()).getAbsolutePath() + "\""));
  }

  public void testModuleCompilationControlsRemainAvailable() {
    var configuration = facet.getConfiguration();
    configuration.setModuleCompilationEnabled("sample.App", false);
    configuration.setPackagingRelativePath("sample.App", "custom-output");
    if (!GwtWebUtil.isWebSupportAvailable()) configuration.setWebFacetName("existing-web");

    GwtFacetEditor editor = new GwtFacetEditor(editorContext(), new FacetValidatorsManager() {
      @Override
      public void registerValidator(FacetEditorValidator validator, JComponent... components) {}
      @Override
      public void validate() {}
    }, configuration);
    try {
      editor.reset();
      JComponent component = editor.createComponent();
      JTable table = UIUtil.findComponentOfType(component, JTable.class);
      assertNotNull(table);
      for (Component ancestor = table; ancestor != null; ancestor = ancestor.getParent()) {
        assertTrue("Compilation controls are hidden by " + ancestor.getClass().getSimpleName(), ancestor.isVisible());
      }
      assertEquals(GwtWebUtil.isWebSupportAvailable() ? 3 : 2, table.getColumnCount());
      assertEquals(1, table.getRowCount());
      assertEquals("sample.App", table.getValueAt(0, 1));
      assertEquals(Boolean.FALSE, table.getValueAt(0, 0));
      assertTrue(table.isCellEditable(0, 0));
      table.setValueAt(Boolean.TRUE, 0, 0);
      assertTrue(editor.isModified());
      editor.apply();

      XmlFile file = (XmlFile)PsiManager.getInstance(getProject())
        .findFile(myFixture.findFileInTempDir("sample/App.gwt.xml"));
      var module = GwtModulesManager.getInstance(getProject()).getGwtModule(file);
      assertNotNull(module);
      assertTrue(configuration.isModuleCompilationEnabled(module));
      assertEquals("custom-output", configuration.getPackagingRelativePath(module));
      if (!GwtWebUtil.isWebSupportAvailable()) assertEquals("existing-web", configuration.getWebFacetName());
    }
    finally {
      editor.disposeUIResources();
    }
  }

  private FacetEditorContext editorContext() {
    return (FacetEditorContext)Proxy.newProxyInstance(FacetEditorContext.class.getClassLoader(),
      new Class<?>[]{FacetEditorContext.class}, (proxy, method, arguments) -> switch (method.getName()) {
        case "getProject" -> getProject();
        case "getModule" -> getModule();
        case "getFacet" -> facet;
        case "getFacetName" -> facet.getName();
        case "getFacetsProvider" -> DefaultFacetsProvider.INSTANCE;
        case "getRootModel" -> ModuleRootManager.getInstance(getModule());
        case "isNewFacet" -> false;
        default -> throw new UnsupportedOperationException(method.getName());
      });
  }
}
