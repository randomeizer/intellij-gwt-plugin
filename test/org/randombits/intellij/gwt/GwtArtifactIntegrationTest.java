package org.randombits.intellij.gwt;

import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.CompilerProjectExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.compiler.ArtifactCompileScope;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ui.UIUtil;
import org.jdom.Element;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.model.MavenModel;
import org.jetbrains.idea.maven.model.MavenPlugin;
import org.jetbrains.idea.maven.project.MavenProject;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.facet.GwtFacetArtifactValidator;
import org.randombits.intellij.gwt.maven.GwtFacetImporter;
import org.randombits.intellij.gwt.packaging.GwtCompilerOutputElementType;
import org.randombits.intellij.gwt.packaging.GwtCompilerDeployOutputElement;
import org.randombits.intellij.gwt.build.GwtBuildTargetScopeProvider;
import org.randombits.intellij.gwt.jps.model.GwtRepositoryConfiguration;
import com.intellij.jarRepository.RemoteRepositoriesConfiguration;
import com.intellij.jarRepository.RemoteRepositoryDescription;

import java.nio.file.Path;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Platform-only artifact behavior must work with or without Web/Servlets. */
public class GwtArtifactIntegrationTest extends LightJavaCodeInsightFixtureTestCase {
  private GwtFacet facet;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    facet = GwtTestCase.addGwtFacet(getModule(), null);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      WriteAction.run(() -> {
        var model = ArtifactManager.getInstance(getProject()).createModifiableModel();
        for (Artifact artifact : model.getArtifacts()) model.removeArtifact(artifact);
        model.commit();
      });
    }
    finally {
      super.tearDown();
    }
  }

  public void testMavenImportsPlainOutputArtifactAndReusesIt() throws Exception {
    String output = new java.io.File(FileUtil.getTempDirectory(), "gwt-generated-web").getAbsolutePath();
    reimport(output);
    Artifact artifact = ArtifactManager.getInstance(getProject()).findArtifact(
      GwtFacetArtifactValidator.suggestArtifactName(getModule().getName()));
    assertNotNull("A Maven GWT output directory must create a plain artifact", artifact);
    assertSame(PlainArtifactType.getInstance(), artifact.getArtifactType());
    assertEquals(output, artifact.getOutputPath());
    assertContainsGwtOutput(artifact);

    reimport(output + "-updated");
    var artifacts = ArtifactManager.getInstance(getProject()).getArtifacts();
    assertEquals(1, artifacts.length);
    assertEquals(output + "-updated", artifacts[0].getOutputPath());
    assertContainsGwtOutput(artifacts[0]);
  }

  public void testMavenWithoutOutputDirectoryDoesNotInventArtifact() throws Exception {
    reimport(null);
    assertEmpty(ArtifactManager.getInstance(getProject()).getArtifacts());
  }

  public void testPlainOutputValidationWithoutWebModel() throws Exception {
    var manager = ArtifactManager.getInstance(getProject());
    var resolvingContext = manager.getResolvingContext();
    var context = (ArtifactsStructureConfigurableContext)Proxy.newProxyInstance(
      ArtifactsStructureConfigurableContext.class.getClassLoader(), new Class<?>[]{ArtifactsStructureConfigurableContext.class},
      (proxy, method, arguments) -> switch (method.getName()) {
        case "getArtifactModel" -> manager;
        case "getProject" -> getProject();
        case "getModulesProvider" -> resolvingContext.getModulesProvider();
        case "getRootElement" -> ((Artifact)arguments[0]).getRootElement();
        default -> throw new UnsupportedOperationException(method.getName());
      });
    var validator = new GwtFacetArtifactValidator(facet, () -> context, () -> null);
    var missingOutput = validator.check();
    assertFalse(missingOutput.isOk());
    assertNotNull("Missing compiler output should offer a plain-artifact quick fix", missingOutput.getQuickFix());

    reimport(new java.io.File(FileUtil.getTempDirectory(), "gwt-validated-output").getAbsolutePath());
    assertTrue(validator.check().isOk());
  }

  public void testDeploymentOnlyArtifactIncludesGwtAndJavaBuildScopes() {
    WriteAction.run(() -> {
      var manager = ArtifactManager.getInstance(getProject());
      var model = manager.createModifiableModel();
      var root = PackagingElementFactory.getInstance().createArtifactRootElement();
      root.addOrFindChild(new GwtCompilerDeployOutputElement(getProject(), facet));
      model.addArtifact("deploy-only", PlainArtifactType.getInstance(), root);
      model.commit();
    });
    var artifact = ArtifactManager.getInstance(getProject()).findArtifact("deploy-only");
    assertNotNull(artifact);
    var scope = ArtifactCompileScope.createArtifactsScope(getProject(), List.of(artifact), true);
    var scopes = new GwtBuildTargetScopeProvider().getBuildTargetScopes(scope, getProject(), false);
    assertEquals(2, scopes.size());
    assertTrue(scopes.stream().anyMatch(s -> s.getTypeId().equals("gwt") && s.getTargetIdList().contains(getModule().getName()) && s.getForceBuild()));
    assertTrue(scopes.stream().anyMatch(s -> s.getTypeId().equals("java-production") && s.getTargetIdList().contains(getModule().getName())));
  }

  public void testRepositorySnapshotRefreshesOnEveryBuildAndExportsWithoutGwtScope() {
    var configuration = RemoteRepositoriesConfiguration.getInstance(getProject());
    var original = configuration.getRepositories();
    var scope = ArtifactCompileScope.createArtifactsScope(getProject(), List.of());
    var provider = new GwtBuildTargetScopeProvider();
    try {
      for (String id : List.of("first-build", "second-build")) {
        configuration.setRepositories(List.of(new RemoteRepositoryDescription(id, "Fixture", "file:///fixture/" + id)));
        assertEmpty(provider.getBuildTargetScopes(scope, getProject(), false));
        String encoded = scope.exportUserData().entrySet().stream()
          .filter(entry -> entry.getKey().toString().equals(GwtRepositoryConfiguration.BUILDER_PARAMETER))
          .map(entry -> entry.getValue().toString()).findFirst().orElseThrow();
        var repositories = GwtRepositoryConfiguration.decode(encoded);
        assertEquals(1, repositories.size());
        assertEquals(id, repositories.getFirst().id);
        assertEquals("file:///fixture/" + id, repositories.getFirst().url);
      }
    }
    finally {
      configuration.setRepositories(original);
    }
  }

  public void testGeneratedContainerIsExcludedAfterCompilerOutputChanges() throws Exception {
    checkGeneratedContainerAfterOutputChanges(false);
  }

  public void testInheritedGeneratedContainerIsExcludedAfterCompilerOutputChanges() throws Exception {
    checkGeneratedContainerAfterOutputChanges(true);
  }

  private void checkGeneratedContainerAfterOutputChanges(boolean inheritOutput) throws Exception {
    var projectExtension = CompilerProjectExtension.getInstance(getProject());
    assertNotNull(projectExtension);
    String originalProjectOutput = projectExtension.getCompilerOutputUrl();
    var extension = CompilerModuleExtension.getInstance(getModule());
    assertNotNull(extension);
    String original = extension.getCompilerOutputUrl();
    boolean inherited = extension.isCompilerOutputPathInherited();
    var directory = FileUtil.createTempDirectory("gwt-output-index", "");
    Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory));
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.getAbsolutePath());
    var content = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(directory);
    assertNotNull(content);
    WriteAction.run(() -> {
      var model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      model.addContentEntry(content);
      model.commit();
    });
    try {
      for (String output : List.of("first-production", "second-production")) {
        var classesFile = new java.io.File(directory, output + "/classes");
        FileUtil.createDirectory(classesFile);
        var classes = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(classesFile);
        assertNotNull(classes);
        if (inheritOutput) {
          WriteAction.run(() -> projectExtension.setCompilerOutputUrl(classes.getUrl()));
        }
        configureCompilerOutput(classes.getUrl(), inheritOutput);
        var production = Path.of(VfsUtilCore.urlToPath(
          CompilerModuleExtension.getInstance(getModule()).getCompilerOutputUrl()));
        FileUtil.createDirectory(production.getParent().toFile());
        var parent = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(production.getParent());
        assertNotNull(parent);
        // Establish known absence so refresh emits a create event instead of initial discovery.
        parent.getChildren();
        var containerFile = production.getParent().resolve(".intellij-gwt").toFile();
        FileUtil.createDirectory(containerFile);
        var container = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(containerFile);
        assertNotNull(container);
        var generatedFile = new java.io.File(containerFile, "key/generated.js");
        FileUtil.writeToFile(generatedFile, "generated");
        var generated = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(generatedFile);
        assertNotNull(generated);
        // Creation of a previously missing excluded root delivers deferred VFS/index updates.
        VfsTestUtil.syncRefresh();
        assertTrue("Generated output must be excluded at production paths: " + output,
                   ProjectFileIndex.getInstance(getProject()).isExcluded(generated));
        if (output.equals("first-production")) {
          WriteAction.run(() -> {
            var model = com.intellij.facet.FacetManager.getInstance(getModule()).createModifiableModel();
            model.removeFacet(facet);
            model.commit();
          });
        }
      }
    }
    finally {
      WriteAction.run(() -> projectExtension.setCompilerOutputUrl(originalProjectOutput));
      configureCompilerOutput(original, inherited);
      WriteAction.run(() -> {
        var model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
        for (var entry : model.getContentEntries()) if (entry.getUrl().equals(content.getUrl())) model.removeContentEntry(entry);
        model.commit();
      });
    }
  }

  private void configureCompilerOutput(String url, boolean inherited) {
    WriteAction.run(() -> {
      var model = ModuleRootManager.getInstance(getModule()).getModifiableModel();
      var extension = model.getModuleExtension(CompilerModuleExtension.class);
      extension.inheritCompilerOutputPath(inherited);
      extension.setCompilerOutputPath(url);
      model.commit();
    });
  }

  private void assertContainsGwtOutput(Artifact artifact) {
    int[] count = {0};
    ArtifactUtil.processPackagingElements(artifact, GwtCompilerOutputElementType.getInstance(), element -> {
      if (facet.equals(element.getFacet())) count[0]++;
      return true;
    }, ArtifactManager.getInstance(getProject()).getResolvingContext(), false);
    assertEquals("Import must include the facet output exactly once", 1, count[0]);
  }

  private void reimport(String output) throws Exception {
    var directory = FileUtil.createTempDirectory("gwt-import", "pom");
    Disposer.register(getTestRootDisposable(), () -> FileUtil.delete(directory));
    var pomFile = new java.io.File(directory, "pom.xml");
    FileUtil.writeToFile(pomFile, "<project/>");
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), directory.getAbsolutePath());
    var pom = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(pomFile);
    assertNotNull(pom);
    var project = new MavenProject(pom);
    var model = new MavenModel();
    model.setMavenId(new MavenId("sample", "client", "1"));
    model.setPackaging("jar");
    var configuration = new Element("configuration");
    if (output != null) configuration.addContent(new Element("webappDirectory").setText(output));
    model.setPlugins(List.of(new MavenPlugin("org.codehaus.mojo", "gwt-maven-plugin", "2.10.0",
      false, false, configuration, List.of(), List.of())));
    project.updateState(model, List.of(), null, List.of(), MavenExplicitProfiles.NONE, Set.of(), Map.of(),
      Path.of(directory.getAbsolutePath(), "repository"), false);
    var importer = new GwtFacetImporter("org.codehaus.mojo", "gwt-maven-plugin") {
      @Override
      protected void setupGwtSdk(GwtFacet target, MavenProject mavenProject) {}

      void importProject() {
        reimportFacet(null, getModule(), null, facet, null, project, null, Map.of(), List.of());
      }
    };
    importer.importProject();
    UIUtil.dispatchAllInvocationEvents();
  }
}
