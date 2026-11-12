package org.randombits.intellij.gwt.jps.build;

import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;
import org.jetbrains.jps.builders.BuildTarget;

import java.io.File;

/** GWT production source root, identified by the same normalized absolute path as JPS. */
final class GwtBuildRootDescriptor extends BuildRootDescriptor {
  private final GwtBuildTarget myTarget;
  private final File myRoot;

  GwtBuildRootDescriptor(GwtBuildTarget target, File root) {
    myTarget = target;
    myRoot = root;
  }

  @Override
  public @NotNull String getRootId() {
    return FileUtilRt.toSystemIndependentName(myRoot.getAbsolutePath());
  }

  @Override
  public @NotNull File getRootFile() {
    return myRoot;
  }

  @Override
  public @NotNull BuildTarget<?> getTarget() {
    return myTarget;
  }
}
