package org.randombits.intellij.gwt.jps.index;

import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.jps.model.JpsGwtModuleExtension;

import java.util.Collection;

public interface JpsGwtModuleIndex {
  @NotNull
  Collection<JpsGwtModule> getModulesToCompile(@NotNull JpsGwtModuleExtension extension, boolean includeTests);

  @NotNull
  Collection<JpsGwtModule> getModules(@NotNull JpsGwtModuleExtension extension, boolean includeTests, final boolean compilableOnly);

  boolean isCompilable(JpsGwtModule module, JpsGwtModuleExtension extension);

  @NotNull
  Collection<JpsGwtModule> getInheritedModules(@NotNull JpsGwtModule module);

  Collection<JpsGwtModule> getModulesByName(String qualifiedName);
}
