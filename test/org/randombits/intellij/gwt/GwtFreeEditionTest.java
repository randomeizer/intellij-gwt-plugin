package org.randombits.intellij.gwt;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.execution.configurations.ConfigurationType;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.module.GwtModulesManager;
import org.randombits.intellij.gwt.module.model.GwtModule;
import org.randombits.intellij.gwt.web.GwtWebUtil;
import org.randombits.intellij.gwt.run.GwtDevModeServerProvider;
import org.randombits.intellij.gwt.sdk.GwtSdkType;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class GwtFreeEditionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testCoreLoadsWithoutUltimate() {
    assertTrue(PluginManagerCore.getLoadedPlugins().stream()
                 .anyMatch(plugin -> plugin.getPluginId().equals(PluginId.getId("org.randombits.gwtplugin"))));
    for (String id : new String[]{"com.intellij.modules.ultimate", "com.intellij.modules.idea.ultimate",
      "com.intellij.javaee.web", "com.intellij.javaee.app.servers.integration", "JavaScriptDebugger"}) {
      assertFalse(id, PluginManagerCore.getLoadedPlugins().stream()
        .anyMatch(plugin -> plugin.getPluginId().equals(PluginId.getId(id))));
    }
    assertFalse(GwtWebUtil.isWebSupportAvailable());
  }

  public void testOptionalExtensionsAreNotRegistered() {
    assertFalse(ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList().stream()
                  .anyMatch(type -> type.getId().equals("org.randombits.intellij.gwt.run.ConfigurationType")));
    assertFalse(LocalInspectionEP.LOCAL_INSPECTION.getExtensionList().stream()
                  .anyMatch(inspection -> "RandombitsGwtServiceNotRegistered".equals(inspection.shortName)));
    assertEmpty(GwtDevModeServerProvider.EP_NAME.getExtensionList());
    // Build-tool SDK discovery remains available without web or server services.
    assertNotNull(GwtSdkType.findType("maven"));
    assertNotNull(GwtSdkType.findType("gradle"));
  }

  public void testFacetAndModuleModelWithoutWebServices() {
    GwtFacet facet = GwtTestCase.addGwtFacet(getModule(), null);
    assertNull(org.randombits.intellij.gwt.web.GwtWebSupport.getInstance());
    XmlFile file = (XmlFile)myFixture.addFileToProject("sample/App.gwt.xml", "<module rename-to=\"sample\"><source path=\"client\"/></module>");
    GwtModule module = GwtModulesManager.getInstance(getProject()).getGwtModule(file);
    assertNotNull(module);
    assertEquals("sample.App", module.getQualifiedName());
    assertEquals("sample", module.getOutputName());
    assertNull(GwtWebUtil.getRelativeToWebRootPath(file.getVirtualFile(), getProject()));
  }
}
