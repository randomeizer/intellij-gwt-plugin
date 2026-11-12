package org.randombits.intellij.gwt.jps.build;

import org.jetbrains.jps.builders.artifacts.ArtifactBuildTaskProvider;
import org.jetbrains.jps.incremental.BuildTask;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.ProjectBuildException;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.elements.JpsArtifactOutputPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsComplexPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsCompositePackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtCompilerOutputPackagingElement;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Prevent artifact-only standalone requests from silently packaging unbuilt GWT output. */
public final class GwtArtifactBuildTaskProvider extends ArtifactBuildTaskProvider {
  @Override
  public List<? extends BuildTask> createArtifactBuildTasks(JpsArtifact artifact, ArtifactBuildPhase phase) {
    if (phase != ArtifactBuildPhase.PRE_PROCESSING) return List.of();
    return List.of(new BuildTask() {
      @Override
      public void build(CompileContext context) throws ProjectBuildException {
        Set<JpsPackagingElement> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        check(artifact.getRootElement(), context, visited);
      }
    });
  }

  private static void check(JpsPackagingElement element, CompileContext context, Set<JpsPackagingElement> visited)
    throws ProjectBuildException {
    if (!visited.add(element)) return;
    if (element instanceof JpsGwtCompilerOutputPackagingElement) {
      var module = ((JpsGwtCompilerOutputPackagingElement)element).getModuleReference().resolve();
      var service = JpsGwtExtensionService.getInstance();
      var extension = service.getExtension(module);
      if (extension == null || service.getGwtModuleIndex(module.getProject().getModel()).getModulesToCompile(extension, true).isEmpty()) return;
      if (!context.getScope().isWholeTargetAffected(new GwtBuildTarget(extension))) {
        throw new ProjectBuildException("GWT artifact output for module '" + module.getName() +
          "' requires the complete 'gwt' build target in the compilation scope. " +
          "Build through IDEA or include the module's GWT and Java production targets in the standalone JPS request.");
      }
    }
    else if (element instanceof JpsArtifactOutputPackagingElement) {
      var artifact = ((JpsArtifactOutputPackagingElement)element).getArtifactReference().resolve();
      if (artifact != null) check(artifact.getRootElement(), context, visited);
    }
    else if (element instanceof JpsCompositePackagingElement) {
      for (var child : ((JpsCompositePackagingElement)element).getChildren()) check(child, context, visited);
    }
    else if (element instanceof JpsComplexPackagingElement) {
      for (var child : ((JpsComplexPackagingElement)element).getSubstitution()) check(child, context, visited);
    }
  }
}
