package org.randombits.intellij.gwt.jps.build;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import org.junit.Assume;
import org.junit.runner.RunWith;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryCopyPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsDirectoryPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.impl.GwtModuleExtensionProperties;
import org.randombits.intellij.gwt.jps.model.impl.GwtModulePackagingProperties;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtCompilerOutputPackagingElement;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtExtensionServiceImpl;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtModuleExtensionImpl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@RunWith(JUnit38AssumeSupportRunner.class)
public class GwtJpsOutputLayoutTest extends LightPlatformTestCase {
  private Path directory;
  private JpsModel model;
  private JpsModule module;
  private GwtModuleExtensionProperties properties;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    directory = FileUtil.createTempDirectory("gwt-output-layout", "").toPath();
    model = JpsElementFactory.getInstance().createModel();
    module = module(model, "client");
    Files.createDirectories(directory.resolve("src"));
    Files.writeString(directory.resolve("src/Probe.gwt.xml"), "<module rename-to='renamed'><entry-point class='client.App'/></module>");
    properties = new GwtModuleExtensionProperties();
    JpsGwtExtensionService.getInstance().setExtension(module, new JpsGwtModuleExtensionImpl(properties));
  }

  private JpsModule module(JpsModel owner, String name) {
    var result = owner.getProject().addModule(name, JpsJavaModuleType.INSTANCE);
    result.addSourceRoot(JpsPathUtil.pathToUrl(directory.resolve("src").toString()), JavaSourceRootType.SOURCE);
    JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(result)
      .setOutputUrl(JpsPathUtil.pathToUrl(directory.resolve("production/classes").toString()));
    return result;
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

  public void testLayoutIsKnownBeforeContextAndStaysOutsideJavaClassOutput() {
    Path base = JpsGwtCompilerPaths.getModuleOutputDirectory(module);
    assertEquals(directory.resolve("production/.intellij-gwt"), base.getParent());
    assertFalse(base.startsWith(directory.resolve("production/classes")));
    assertFalse(Files.exists(base));
    var target = new GwtBuildTarget(JpsGwtExtensionService.getInstance().getExtension(module));
    assertEquals(base.resolve("war").toFile(), JpsGwtCompilerPaths.getCompilerOutputRoot(target));
    assertEquals(base.resolve("extra").toFile(), JpsGwtCompilerPaths.getExtraOutputRoot(target));
  }

  public void testStableKeysSeparateModulesEvenWhenTheyShareJavaOutput() {
    var reloaded = module(JpsElementFactory.getInstance().createModel(), "client");
    var other = module(model, "another-module");
    assertEquals(JpsGwtCompilerPaths.getModuleOutputDirectory(module), JpsGwtCompilerPaths.getModuleOutputDirectory(reloaded));
    assertFalse(JpsGwtCompilerPaths.getModuleOutputDirectory(module).equals(JpsGwtCompilerPaths.getModuleOutputDirectory(other)));
    JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(reloaded)
      .setOutputUrl(JpsPathUtil.pathToUrl(directory.resolve("moved/classes").toString()));
    assertFalse(JpsGwtCompilerPaths.getModuleOutputDirectory(module).equals(JpsGwtCompilerPaths.getModuleOutputDirectory(reloaded)));
  }

  public void testMissingJavaOutputFailsWithModuleName() {
    var missing = model.getProject().addModule("missing-output", JpsJavaModuleType.INSTANCE);
    try {
      JpsGwtCompilerPaths.getModuleOutputDirectory(missing);
      fail("Missing output must not silently omit GWT files");
    }
    catch (IllegalStateException expected) {
      assertTrue(expected.getMessage().contains("missing-output"));
    }
  }

  public void testOutputOwnershipSurvivesWarCleanupAndPreservesSiblingData() throws Exception {
    Path sentinel = directory.resolve("production/java-owned.txt");
    Files.createDirectories(sentinel.getParent());
    Files.writeString(sentinel, "preserve");
    JpsGwtCompilerPaths.prepareOutputLocation(module);
    Path base = JpsGwtCompilerPaths.getModuleOutputDirectory(module);
    Files.createDirectories(base.resolve("war"));
    Files.writeString(base.resolve("war/generated.js"), "generated");
    FileUtil.delete(base.resolve("war").toFile());
    JpsGwtCompilerPaths.prepareOutputLocation(module);
    assertTrue(Files.isRegularFile(base.resolve(".owner")));
    assertEquals("preserve", Files.readString(sentinel));
  }

  public void testUnownedOutputDirectoryIsRejectedWithoutDeletingData() throws Exception {
    Path base = JpsGwtCompilerPaths.getModuleOutputDirectory(module);
    Files.createDirectories(base);
    Files.writeString(base.resolve("user-file.txt"), "preserve");
    try {
      JpsGwtCompilerPaths.prepareOutputLocation(module);
      fail("Unowned data must not be adopted for JPS cleanup");
    }
    catch (java.io.IOException expected) {
      assertTrue(expected.getMessage().contains("unowned"));
    }
    assertEquals("preserve", Files.readString(base.resolve("user-file.txt")));
    assertFalse(Files.exists(base.resolve(".owner")));
  }

  public void testSymbolicOutputContainerCannotRedirectCleanup() throws Exception {
    Path base = JpsGwtCompilerPaths.getModuleOutputDirectory(module);
    Path external = Files.createDirectories(directory.resolve("external"));
    Files.writeString(external.resolve("preserve.txt"), "preserve");
    Files.createDirectories(base.getParent().getParent());
    try {
      Files.createSymbolicLink(base.getParent(), external);
    }
    catch (UnsupportedOperationException | java.nio.file.FileSystemException unavailable) {
      Assume.assumeNoException("Symbolic links unavailable on this test filesystem", unavailable);
    }
    try {
      JpsGwtCompilerPaths.prepareOutputLocation(module);
      fail("Symbolic output location must not be adopted");
    }
    catch (java.io.IOException expected) {
      assertTrue(expected.getMessage().contains("symbolic link"));
    }
    assertEquals("preserve", Files.readString(external.resolve("preserve.txt")));
  }

  public void testStandardSubstitutionIsAvailableBeforeAnyBuildCallback() {
    var packaging = new GwtModulePackagingProperties();
    packaging.myName = "Probe";
    packaging.myPath = "/custom/nested";
    properties.myPackagingStates.add(packaging);
    var regular = element(JpsGwtCompilerOutputPackagingElement.OutputKind.REGULAR);
    var deploy = element(JpsGwtCompilerOutputPackagingElement.OutputKind.DEPLOY);
    Path base = JpsGwtCompilerPaths.getModuleOutputDirectory(module);
    assertEquals(Map.of("custom/nested", base.resolve("war/renamed").toString()), flatten(regular));
    assertEquals(Map.of("WEB-INF/deploy/custom/nested", base.resolve("war/WEB-INF/deploy/renamed").toString()), flatten(deploy));
    assertNotSame(regular.getSubstitution().getFirst(), regular.getSubstitution().getFirst());
    packaging.myPath = "";
    assertEquals(Map.of("", base.resolve("war/renamed").toString()), flatten(regular));
    packaging.myEnabled = false;
    assertEmpty(regular.getSubstitution());
    assertEmpty(deploy.getSubstitution());
    assertFalse(Files.exists(base));
  }

  public void testModelOnlyIndexPreservesInheritedEntryPointsAndTestSelection() throws Exception {
    Files.writeString(directory.resolve("src/Library.gwt.xml"),
      "<module><inherits name='com.google.gwt.user.User'/><entry-point class='client.Library'/></module>");
    Files.writeString(directory.resolve("src/Inherited.gwt.xml"), "<module><inherits name='Library'/></module>");
    Files.createDirectories(directory.resolve("test"));
    Files.writeString(directory.resolve("test/Test.gwt.xml"), "<module><entry-point class='test.App'/></module>");
    module.addSourceRoot(JpsPathUtil.pathToUrl(directory.resolve("test").toString()), JavaSourceRootType.TEST_SOURCE);
    var index = JpsGwtExtensionService.getInstance().getGwtModuleIndex(model);
    var extension = JpsGwtExtensionService.getInstance().getExtension(module);
    assertEquals(4, index.getModulesToCompile(extension, true).size());
    assertEquals(3, index.getModulesToCompile(extension, false).size());
    var inherited = index.getModulesByName("Inherited").iterator().next();
    assertEquals("Library", index.getInheritedModules(inherited).iterator().next().getQualifiedName());
  }

  private JpsGwtCompilerOutputPackagingElement element(JpsGwtCompilerOutputPackagingElement.OutputKind kind) {
    var result = new JpsGwtCompilerOutputPackagingElement(module.createReference(), kind);
    var root = JpsPackagingElementFactory.getInstance().createArtifactRoot();
    root.addChild(result);
    JpsArtifactService.getInstance().addArtifact(model.getProject(), kind.name(), root, DirectoryArtifactType.INSTANCE,
      JpsElementFactory.getInstance().createDummyElement());
    return result;
  }

  private static Map<String, String> flatten(JpsGwtCompilerOutputPackagingElement element) {
    var result = new LinkedHashMap<String, String>();
    for (var child : element.getSubstitution()) flatten(child, "", result);
    return result;
  }

  private static void flatten(JpsPackagingElement element, String path, Map<String, String> output) {
    if (element instanceof JpsDirectoryCopyPackagingElement copy) output.put(path, copy.getDirectoryPath());
    else if (element instanceof JpsDirectoryPackagingElement directory) {
      String childPath = path.isEmpty() ? directory.getDirectoryName() : path + "/" + directory.getDirectoryName();
      for (var child : directory.getChildren()) flatten(child, childPath, output);
    }
    else fail("Unexpected substitution " + element);
  }
}
