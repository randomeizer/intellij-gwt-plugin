package org.randombits.intellij.gwt.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.randombits.intellij.gwt.jps.index.JpsGwtModuleIndex;
import org.randombits.intellij.gwt.jps.index.impl.JpsGwtModuleIndexImpl;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.JpsGwtModuleExtension;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JpsGwtExtensionServiceImpl extends JpsGwtExtensionService {
  private final ConcurrentMap<JpsModel, JpsGwtModuleIndex> myCachedGwtIndices = new ConcurrentHashMap<>();

  @Override
  public @Nullable JpsGwtModuleExtension getExtension(@Nullable  JpsModule module) {
    return module != null ? module.getContainer().getChild(JpsGwtModuleExtensionImpl.ROLE) : null;
  }

  @Override
  public void setExtension(@NotNull JpsModule module, @NotNull JpsGwtModuleExtension extension) {
    module.getContainer().setChild(JpsGwtModuleExtensionImpl.ROLE, extension);
  }

  @Override
  public @NotNull JpsGwtModuleIndex getGwtModuleIndex(@NotNull JpsModel model, @NotNull BuildDataPaths paths) {
    JpsGwtModuleIndex index = myCachedGwtIndices.get(model);
    if (index == null) {
      index = new JpsGwtModuleIndexImpl(model, paths);
      myCachedGwtIndices.put(model, index);
    }
    return index;
  }

  @TestOnly
  public void clearCache() {
    myCachedGwtIndices.clear();
  }
}
