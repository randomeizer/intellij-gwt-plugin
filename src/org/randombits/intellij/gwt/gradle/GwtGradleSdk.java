package org.randombits.intellij.gwt.gradle;

import org.randombits.intellij.gwt.sdk.GwtVersion;
import org.randombits.intellij.gwt.sdk.impl.GwtSdkBase;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionDetector;
import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.jps.model.impl.sdk.GwtGradleSdkPaths;

public class GwtGradleSdk extends GwtSdkBase {
  private final String myVersion;

  public GwtGradleSdk(String homeDirectoryPath, final String version) {
    super(new GwtGradleSdkPaths(homeDirectoryPath, version));
    myVersion = version;
  }

  @Override
  protected @NotNull GwtVersion detectVersion() {
    return GwtVersionDetector.getGwtVersionFromString(myVersion);
  }

  @Override
  public boolean isValid() {
    return true;
  }
}
