package org.randombits.intellij.gwt.web;

import org.randombits.intellij.gwt.module.model.GwtModule;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class GwtWebUtil {
  private GwtWebUtil() {}

  public static boolean isWebSupportAvailable() {
    return GwtWebSupport.getInstance() != null;
  }

  public static @NotNull @NlsSafe String getOutputPath(@NotNull GwtModule module, @NotNull String relativePath) {
    return module.getOutputName() + "/" + relativePath;
  }

  public static @Nullable @NlsSafe String getRelativeToWebRootPath(@NotNull VirtualFile file, @NotNull Project project) {
    GwtWebSupport support = GwtWebSupport.getInstance();
    return support == null ? null : support.getRelativeToWebRootPath(file, project);
  }
}
