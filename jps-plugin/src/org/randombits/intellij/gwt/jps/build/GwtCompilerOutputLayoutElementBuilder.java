package org.randombits.intellij.gwt.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTarget;
import org.jetbrains.jps.builders.TargetOutputIndex;
import org.randombits.intellij.gwt.jps.index.JpsGwtModule;
import org.randombits.intellij.gwt.jps.index.JpsGwtModuleIndex;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.JpsGwtModuleExtension;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtCompilerOutputPackagingElement;
import org.jetbrains.jps.incremental.artifacts.builders.LayoutElementBuilderService;
import org.jetbrains.jps.incremental.artifacts.impl.JpsArtifactPathUtil;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactCompilerInstructionCreator;
import org.jetbrains.jps.incremental.artifacts.instructions.ArtifactInstructionsBuilderContext;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

public final class GwtCompilerOutputLayoutElementBuilder extends LayoutElementBuilderService<JpsGwtCompilerOutputPackagingElement> {
  public GwtCompilerOutputLayoutElementBuilder() {
    super(JpsGwtCompilerOutputPackagingElement.class);
  }

  @Override
  public Collection<? extends BuildTarget<?>> getDependencies(@NotNull JpsGwtCompilerOutputPackagingElement element,
                                                              TargetOutputIndex outputIndex) {
    JpsModule module = element.getModuleReference().resolve();
    JpsGwtModuleExtension extension = JpsGwtExtensionService.getInstance().getExtension(module);
    if (extension != null) {
      return Collections.singletonList(new GwtBuildTarget(extension));
    }
    return Collections.emptyList();
  }

  @Override
  public void generateInstructions(JpsGwtCompilerOutputPackagingElement element,
                                   ArtifactCompilerInstructionCreator instructionCreator,
                                   ArtifactInstructionsBuilderContext builderContext) {
    JpsModule module = element.getModuleReference().resolve();
    JpsGwtModuleExtension extension = JpsGwtExtensionService.getInstance().getExtension(module);
    if (extension == null) return;

    File outputRoot = JpsGwtCompilerPaths.getCompilerOutputRoot(new GwtBuildTarget(extension), builderContext.getDataPaths());
    JpsGwtModuleIndex index = JpsGwtExtensionService.getInstance().getGwtModuleIndex(builderContext.getModel(), builderContext.getDataPaths());
    for (JpsGwtModule gwtModule : index.getModulesToCompile(extension, true)) {
      String outputName = gwtModule.getOutputName();
      String relativePath = extension.getPackagingRelativePath(gwtModule);
      File outputDir;
      switch (element.getOutputKind()) {
        case REGULAR:
          outputDir = new File(outputRoot, outputName);
          instructionCreator.subFolderByRelativePath(relativePath).addDirectoryCopyInstructions(outputDir);
          break;
        case DEPLOY:
          outputDir = new File(outputRoot, JpsArtifactPathUtil.appendToPath("WEB-INF/deploy", outputName));
          instructionCreator.subFolderByRelativePath(JpsArtifactPathUtil.appendToPath("WEB-INF/deploy", relativePath)).addDirectoryCopyInstructions(outputDir);
          break;
      }
    }
  }
}
