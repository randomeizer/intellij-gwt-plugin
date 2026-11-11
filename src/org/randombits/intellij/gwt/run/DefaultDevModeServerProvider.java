package org.randombits.intellij.gwt.run;

import java.util.List;

public final class DefaultDevModeServerProvider extends GwtDevModeServerProvider {
  @Override
  public List<? extends GwtDevModeServer> getServers() {
    return List.of(new DefaultDevModeServer(), new LegacyJettyDevModeServer());
  }
}
