package org.randombits.intellij.gwt.jps.build;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jdom.Element;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;
import org.randombits.intellij.gwt.jps.index.impl.JpsGwtModulesCache;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.impl.GwtModuleExtensionProperties;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtCompilerOutputPackagingElement;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtExtensionServiceImpl;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtModelSerializerExtension;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtModuleExtensionImpl;

import java.nio.file.Files;
import java.nio.file.Path;

public class GwtJpsPublicApiTest extends LightPlatformTestCase {
  private Path directory;
  private JpsModel model;
  private JpsModule module;
  private GwtModuleExtensionProperties properties;
  private BuildDataPathsImpl paths;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    directory = FileUtil.createTempDirectory("gwt-jps-api", "").toPath();
    model = JpsElementFactory.getInstance().createModel();
    module = model.getProject().addModule("client", JpsJavaModuleType.INSTANCE);
    Path sources = Files.createDirectories(directory.resolve("src"));
    Files.writeString(sources.resolve("Probe.gwt.xml"), "<module rename-to='renamed'><entry-point class='client.App'/></module>");
    module.addSourceRoot(JpsPathUtil.pathToUrl(sources.toString()), JavaSourceRootType.SOURCE);
    properties = new GwtModuleExtensionProperties();
    JpsGwtExtensionService.getInstance().setExtension(module, new JpsGwtModuleExtensionImpl(properties));
    paths = new BuildDataPathsImpl(directory.resolve("cache"));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ((JpsGwtExtensionServiceImpl)JpsGwtExtensionService.getInstance()).clearCache();
      if (directory != null) FileUtil.delete(directory.toFile());
    }
    finally {
      super.tearDown();
    }
  }

  public void testProductionRootsRetainIdentityAndExcludeTestRoots() {
    module.addSourceRoot(JpsPathUtil.pathToUrl(directory.resolve("test").toString()), JavaSourceRootType.TEST_SOURCE);
    var target = new GwtBuildTarget(JpsGwtExtensionService.getInstance().getExtension(module));
    var exclusions = (org.jetbrains.jps.indices.ModuleExcludeIndex)java.lang.reflect.Proxy.newProxyInstance(
      getClass().getClassLoader(), new Class<?>[]{org.jetbrains.jps.indices.ModuleExcludeIndex.class},
      (proxy, method, args) -> { throw new AssertionError("Unexpected exclusion query: " + method); });
    var roots = target.computeRootDescriptors(model, exclusions, name -> false, paths);
    assertEquals(1, roots.size());
    var root = roots.getFirst();
    assertEquals(directory.resolve("src").toString(), root.getRootFile().getAbsolutePath());
    assertEquals(FileUtil.toSystemIndependentName(root.getRootFile().getAbsolutePath()), root.getRootId());
    assertSame(target, root.getTarget());
    assertTrue(root.createFileFilter().accept(directory.resolve("src/Probe.gwt.xml").toFile()));
  }

  public void testCachePathMatchesJpsAndRoundTripsPersistedModuleMetadata() throws Exception {
    var configuration = new JpsGwtModulesCache.GwtModulesConfiguration();
    configuration.myGwtModules.add(new JpsGwtModulesCache.GwtModuleData(directory.resolve("src/Probe.gwt.xml").toString(),
      directory.resolve("src").toString(), false, module.getName()));
    Path expected = paths.getTargetTypeDataRootDir(GwtBuildTargetType.INSTANCE).resolve("gwt-modules.xml");
    Files.createDirectories(expected.getParent());
    var cache = new JpsGwtModulesCache(directory.resolve("cache"));
    cache.saveGwtModules(configuration);
    assertTrue(Files.isRegularFile(expected));
    assertEquals("renamed", new JpsGwtModulesCache(paths).loadGwtModules(model).getFirst().getOutputName());
    cache.clear();
    assertFalse(Files.exists(expected));
    assertEquals("Probe", cache.loadGwtModules(model).getFirst().getQualifiedName());
  }

  public void testBothPersistedFacetIdsAndDefaultCopyDispatchRemainCompatible() {
    var serializers = new JpsGwtModelSerializerExtension().getPackagingElementSerializers();
    assertEquals(2, serializers.size());
    for (int i = 0; i < serializers.size(); i++) {
      var element = (JpsGwtCompilerOutputPackagingElement)serializers.get(i)
        .load(new Element("element").setAttribute("facet", "client/gwt/GWT"));
      assertEquals("client", element.getModuleReference().getModuleName());
      assertEquals(JpsGwtCompilerOutputPackagingElement.OutputKind.values()[i], element.getOutputKind());
      var copy = (JpsGwtCompilerOutputPackagingElement)((JpsPackagingElement)element).createElementCopy();
      assertNotSame(element.getModuleReference(), copy.getModuleReference());
      assertEquals(element.getOutputKind(), copy.getOutputKind());
      assertEquals(element.getModuleReference().getModuleName(), copy.getModuleReference().getModuleName());
    }
  }

}
