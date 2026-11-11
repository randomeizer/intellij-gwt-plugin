package org.randombits.intellij.gwt.run;

import com.intellij.execution.configurations.JavaParameters;
import com.intellij.execution.configurations.ParametersList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.GwtBundle;
import org.randombits.intellij.gwt.facet.GwtFacet;

/** Explicit opt-in to GWT's deprecated servlet-capable launcher. */
public final class LegacyJettyDevModeServer extends GwtDevModeServer {
  public static final @NonNls String SERVER_ID = "legacy-jetty";
  private static final @NonNls String LAUNCHER_CLASS = "com.google.gwt.dev.shell.jetty.JettyLauncher";

  public LegacyJettyDevModeServer() {
    super(SERVER_ID, GwtBundle.message("gwt.dev.mode.server.legacy.jetty"));
  }

  @Override
  public void patchParameters(@NotNull JavaParameters parameters, String originalOutputDir, @NotNull GwtFacet gwtFacet) {
    ParametersList arguments = parameters.getProgramParametersList();
    if (!arguments.hasParameter("-server") && !arguments.hasParameter("-noserver")) {
      arguments.add("-server", LAUNCHER_CLASS);
    }
  }
}
