package org.randombits.intellij.gwt.gradle;

import org.randombits.intellij.gwt.GwtBundle;
import org.randombits.intellij.gwt.sdk.GwtSdk;
import org.randombits.intellij.gwt.sdk.GwtSdkType;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.jps.model.GwtSdkPaths;
import org.randombits.intellij.gwt.jps.model.impl.sdk.GwtGradleSdkPaths;

import java.io.File;

import static org.randombits.intellij.gwt.jps.model.GwtSdkPaths.isGwtMavenGroupId;

public final class GwtGradleSdkType extends GwtSdkType {
  public GwtGradleSdkType() {
    super(GwtGradleSdkPaths.TYPE_ID);
  }

  @Override
  public @NotNull GwtSdk createSdk(String homeDirectoryUrl) {
    final String homePath = VfsUtilCore.urlToPath(homeDirectoryUrl);
    return new GwtGradleSdk(homePath, getVersion(homePath));
  }

  @Override
  public boolean isValidSdkHomeDirectory(File directory) {
    File gwtDev = directory.getParentFile();
    if (gwtDev == null || !gwtDev.getName().equals(GwtSdkPaths.GWT_DEV_ARTIFACT_ID)) return false;

    File base = gwtDev.getParentFile();
    return base != null && isGwtMavenGroupId(base.getName());
  }

  @Override
  public boolean isEditable() {
    return false;
  }

  private static String getVersion(String homePath) {
    return PathUtil.getFileName(homePath);
  }

  @Override
  public String getPresentableName(String sdkPath) {
    return GwtBundle.message("label.text.gwt.0.sdk.from.gradle.repository", getVersion(sdkPath));
  }
}
