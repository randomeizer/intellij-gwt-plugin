package org.randombits.intellij.gwt.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.jps.model.JpsGwtCompilerProjectExtension;
import org.randombits.intellij.gwt.jps.model.JpsGwtModuleExtension;
import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.ex.JpsElementBase;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public class JpsGwtCompilerProjectExtensionImpl extends JpsElementBase<JpsGwtCompilerProjectExtensionImpl> implements JpsGwtCompilerProjectExtension {
  public static final JpsElementChildRole<JpsGwtCompilerProjectExtension> ROLE = JpsElementChildRoleBase.create("GWT Compiler Configuration");
  private final Set<String> myModulesToShowCompilerOutput;

  public JpsGwtCompilerProjectExtensionImpl(Collection<String> modulesToShowCompilerOutput) {
    myModulesToShowCompilerOutput = new LinkedHashSet<>(modulesToShowCompilerOutput);
  }

  @Override
  public @NotNull JpsGwtCompilerProjectExtensionImpl createCopy() {
    return new JpsGwtCompilerProjectExtensionImpl(myModulesToShowCompilerOutput);
  }

  @Override
  public boolean isShowCompilerOutput(JpsGwtModuleExtension extension) {
    return myModulesToShowCompilerOutput.contains(extension.getModule().getName());
  }
}
