package org.randombits.intellij.gwt.maven;

import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.facet.GwtFacetConfiguration;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.execution.ParametersListUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.randombits.intellij.gwt.jps.model.GwtJavaScriptOutputStyle;
import org.randombits.intellij.gwt.jps.model.impl.sdk.GwtMavenSdkPaths;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GwtCodehausFacetImporter extends GwtFacetImporter {
  private static final Logger LOG = Logger.getInstance(GwtCodehausFacetImporter.class);

  public GwtCodehausFacetImporter() {
    this("org.codehaus.mojo", "gwt-maven-plugin");
  }

  private GwtCodehausFacetImporter(@NonNls String pluginGroupId, @NonNls String pluginArtifactId) {
    super(pluginGroupId, pluginArtifactId);
  }

  @Override
  public void resolve(Project project, MavenProject mavenProject,
                      MavenEmbedderWrapper embedder) throws MavenProcessCanceledException {
    super.resolve(project, mavenProject, embedder);
    List<GwtMavenSourceArtifacts.SourceArtifact> sources = new ArrayList<>();
    String pomUrl = mavenProject.getFile().getUrl();
    List<MavenRemoteRepository> repos = mavenProject.getRemoteRepositories();
    for (MavenArtifact artifact : sourceDependencies(mavenProject.getDependencies(), getConfig(mavenProject, "compileSourcesArtifacts"))) {
      MavenArtifactInfo artifactInfo =
        new MavenArtifactInfo(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), "jar", "sources");
      MavenArtifact source = embedder.resolve(artifactInfo, repos);
      sources.add(GwtMavenSourceArtifacts.SourceArtifact.from(pomUrl, GwtMavenSourceArtifacts.coordinates(artifact), source,
                                                           mavenProject.getLocalRepositoryPath().toFile()));
    }
    GwtMavenSourceArtifacts.getInstance(project).replace(pomUrl, sources);
  }

  static List<MavenArtifact> sourceDependencies(List<MavenArtifact> dependencies, @Nullable Element compileSourcesArtifacts) {
    if (compileSourcesArtifacts == null) return List.of();
    List<Element> artifactTags = JDOMUtil.getChildren(compileSourcesArtifacts);
    Map<String, MavenArtifact> requested = new LinkedHashMap<>();
    for (Element artifactTag : artifactTags) {
      List<String> parts = StringUtil.split(artifactTag.getTextTrim(), ":");
      if (parts.size() < 2) continue;
      String groupId = parts.get(0);
      String artifactId = parts.get(1);

      for (MavenArtifact artifact : dependencies) {
        if (artifact.getMavenId().equals(groupId, artifactId)) {
          requested.putIfAbsent(GwtMavenSourceArtifacts.coordinates(artifact), artifact);
        }
      }
    }
    return new ArrayList<>(requested.values());
  }

  @Override
  protected void setupAdditionalDependencies(IdeModifiableModelsProvider modelsProvider, Module module, MavenRootModelAdapter rootModel,
                                            MavenProject project) {
    List<String> requested = sourceDependencies(project.getDependencies(), getConfig(project, "compileSourcesArtifacts"))
      .stream().map(GwtMavenSourceArtifacts::coordinates).toList();
    for (MavenArtifact source : GwtMavenSourceArtifacts.getInstance(module.getProject())
      .getArtifacts(project.getFile().getUrl(), requested)) {
      boolean alreadyDeclared = project.getDependencies().stream().anyMatch(dependency ->
        source.getLibraryName().equals(dependency.getLibraryName())
        && (dependency.getScope() == null || "compile".equals(dependency.getScope())));
      if (!alreadyDeclared) rootModel.addLibraryDependency(source, DependencyScope.COMPILE, modelsProvider, project);
    }
  }

  @Override
  protected void setupGwtCompilerOptions(final GwtFacetConfiguration configuration, final Module module, MavenProject project) {
    String extraJvmArgs = findConfigValue(project, "extraJvmArgs");
    List<String> additionalCompilerVmParameters = new ArrayList<>();
    if (extraJvmArgs != null) {
      List<String> jvmArgs = ParametersListUtil.parse(extraJvmArgs);
      for (@NonNls String arg : jvmArgs) {
        if (arg.startsWith("-Xmx")) {
          configuration.setCompilerMaxHeapSize(getSizeInMegabytes(arg));
        }
        else {
          additionalCompilerVmParameters.add(arg);
        }
      }
    }
    GwtVersionImpl version = (GwtVersionImpl)configuration.getSdk().getVersion();
    for (GwtCodehausParameter property : GwtCodehausParameter.PROPERTIES) {
      property.append(version, additionalCompilerVmParameters, findConfigValue(project, property.getTagName()));
    }
    configuration.setAdditionalCompilerVMParameters(ParametersListUtil.join(additionalCompilerVmParameters));

    GwtJavaScriptOutputStyle outputStyle = GwtJavaScriptOutputStyle.byId(findConfigValue(project, "style"));
    if (outputStyle != null) {
      configuration.setOutputStyle(outputStyle);
    }
    List<String> compilerParameters = new ArrayList<>();
    for (GwtCodehausParameter parameter : GwtCodehausParameter.PARAMETERS) {
      parameter.append(version, compilerParameters, findConfigValue(project, parameter.getTagName()));
    }
    configuration.setCompilerParameters(StringUtil.join(compilerParameters, " "));

    final Set<String> enabledModules = new HashSet<>();
    String singleModuleName = findConfigValue(project, "module");
    if (singleModuleName != null) {
      enabledModules.add(singleModuleName);
    }
    else {
      List<Element> moduleTags = JDOMUtil.getChildren(getConfig(project, "modules"), "module");
      for (Element tag : moduleTags) {
        enabledModules.add(tag.getTextTrim());
      }
    }
    setEnabledGwtModulesDumbAware(module, enabledModules, configuration);
  }

  public static int getSizeInMegabytes(String xmxArg) {
    @NonNls String sizeString = StringUtil.trimStart(xmxArg, "-Xmx");
    if (!sizeString.isEmpty()) {
      try {
        int size = Integer.parseInt(sizeString.substring(0, sizeString.length() - 1));
        if (StringUtil.endsWithIgnoreCase(sizeString, "g")) {
          return size * 1024;
        }
        return size;
      }
      catch (NumberFormatException e) {
        LOG.info(e);
      }
    }
    return 0;
  }

  @Override
  protected void setupGwtSdk(GwtFacet facet, MavenProject project) {
    GwtPath path = resolveGwtPath(project);
    if (path == null) return;

    final GwtFacetConfiguration configuration = facet.getConfiguration();
    configuration.setGwtSdkUrl(VfsUtilCore.pathToUrl(FileUtil.toSystemIndependentName(path.path)));
    configuration.setGwtSdkType(path.isInstalled ? null : GwtMavenSdkPaths.TYPE_ID);
  }

  @Override
  protected @Nullable GwtPath resolveGwtPath(MavenProject project) {
    String sdkPath = findGwtPath(project);
    if (sdkPath != null) return new GwtPath(sdkPath, null, true);

    String version = findConfigValue(project, "gwtVersion");
    if (version != null) {
      return new GwtPath(getGwtDevPath(project, version), version, false);
    }

    return super.resolveGwtPath(project);
  }
}
