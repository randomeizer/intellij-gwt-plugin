package org.randombits.intellij.gwt.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsComplexPackagingElement;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModuleReference;
import org.randombits.intellij.gwt.jps.build.GwtBuildTarget;
import org.randombits.intellij.gwt.jps.build.JpsGwtCompilerPaths;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JpsGwtCompilerOutputPackagingElement extends JpsCompositeElementBase<JpsGwtCompilerOutputPackagingElement> implements
                                                                                                                        JpsComplexPackagingElement {
  public enum OutputKind {REGULAR, DEPLOY}

  private final OutputKind myOutputKind;
  private static final JpsElementChildRole<JpsModuleReference> MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("module reference");

  public JpsGwtCompilerOutputPackagingElement(JpsModuleReference moduleReference, OutputKind outputKind) {
    myOutputKind = outputKind;
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
  }

  @Override
  public @NotNull JpsGwtCompilerOutputPackagingElement createCopy() {
    JpsModuleReference moduleReference = getModuleReference();
    return new JpsGwtCompilerOutputPackagingElement(
      JpsElementFactory.getInstance().createModuleReference(moduleReference.getModuleName()),
      myOutputKind);
  }

  public OutputKind getOutputKind() {
    return myOutputKind;
  }

  @Override
  public @NotNull List<JpsPackagingElement> getSubstitution() {
    var module = getModuleReference().resolve();
    var service = JpsGwtExtensionService.getInstance();
    var extension = service.getExtension(module);
    if (extension == null) return List.of();
    var index = service.getGwtModuleIndex(module.getProject().getModel());
    File output = JpsGwtCompilerPaths.getCompilerOutputRoot(new GwtBuildTarget(extension));
    var factory = JpsPackagingElementFactory.getInstance();
    List<JpsPackagingElement> result = new ArrayList<>();
    for (var gwtModule : index.getModulesToCompile(extension, true)) {
      String source = gwtModule.getOutputName();
      String destination = extension.getPackagingRelativePath(gwtModule);
      if (myOutputKind == OutputKind.DEPLOY) {
        source = "WEB-INF/deploy/" + source;
        destination = "WEB-INF/deploy/" + destination;
      }
      result.add(factory.createParentDirectories(destination, factory.createDirectoryCopy(new File(output, source).getPath())));
    }
    return result;
  }

  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }
}
