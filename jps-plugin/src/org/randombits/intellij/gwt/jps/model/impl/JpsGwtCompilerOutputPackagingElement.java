package org.randombits.intellij.gwt.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElement;
import org.jetbrains.jps.model.ex.JpsCompositeElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;
import org.jetbrains.jps.model.module.JpsModuleReference;

public class JpsGwtCompilerOutputPackagingElement extends JpsCompositeElementBase<JpsGwtCompilerOutputPackagingElement> implements
                                                                                                                        JpsPackagingElement {
  public enum OutputKind {REGULAR, DEPLOY}

  private final OutputKind myOutputKind;
  private static final JpsElementChildRole<JpsModuleReference> MODULE_REFERENCE_CHILD_ROLE = JpsElementChildRoleBase.create("module reference");

  public JpsGwtCompilerOutputPackagingElement(JpsModuleReference moduleReference, OutputKind outputKind) {
    myOutputKind = outputKind;
    myContainer.setChild(MODULE_REFERENCE_CHILD_ROLE, moduleReference);
  }

  @Override
  public @NotNull JpsGwtCompilerOutputPackagingElement createElementCopy() {
    JpsModuleReference moduleReference = getModuleReference();
    return new JpsGwtCompilerOutputPackagingElement(
      JpsElementFactory.getInstance().createModuleReference(moduleReference.getModuleName()),
      myOutputKind);
  }

  public OutputKind getOutputKind() {
    return myOutputKind;
  }

  public JpsModuleReference getModuleReference() {
    return myContainer.getChild(MODULE_REFERENCE_CHILD_ROLE);
  }
}
