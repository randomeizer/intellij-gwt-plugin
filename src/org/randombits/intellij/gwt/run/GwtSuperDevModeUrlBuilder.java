package org.randombits.intellij.gwt.run;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;

final class GwtSuperDevModeUrlBuilder {
  private static final String DEFAULT_PORT = "8888";

  private GwtSuperDevModeUrlBuilder() {
  }

  static @Nullable String buildUrl(@Nullable String runPage, @NotNull List<String> shellParameters) {
    if (runPage == null || runPage.isBlank()) {
      return null;
    }

    if (isAbsoluteUrl(runPage)) {
      return runPage;
    }

    String port = getPort(shellParameters);
    return "http://localhost:" + port + (runPage.startsWith("/") ? runPage : "/" + runPage);
  }

  private static boolean isAbsoluteUrl(@NotNull String value) {
    try {
      return URI.create(value).isAbsolute();
    }
    catch (IllegalArgumentException ignored) {
      return false;
    }
  }

  private static @NotNull String getPort(@NotNull List<String> shellParameters) {
    String port = null;
    for (int i = 0; i < shellParameters.size(); i++) {
      String parameter = shellParameters.get(i);
      if (parameter.startsWith("-port=")) {
        port = parameter.substring("-port=".length());
      }
      else if (parameter.equals("-port") && i + 1 < shellParameters.size()) {
        port = shellParameters.get(++i);
      }
    }
    return port == null ? DEFAULT_PORT : port;
  }
}
