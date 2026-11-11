package org.randombits.intellij.gwt.run;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.Collections;

public class GwtSuperDevModeUrlBuilderTest extends TestCase {

  public void testBuildsRelativeRunPageWithDefaultPort() {
    assertEquals("http://localhost:8888/index.html",
                 GwtSuperDevModeUrlBuilder.buildUrl("index.html", Collections.emptyList()));
  }

  public void testBuildsRelativeRunPageWithConfiguredPort() {
    assertEquals("http://localhost:9999/app/index.html",
                 GwtSuperDevModeUrlBuilder.buildUrl("/app/index.html", Arrays.asList("-port", "9999")));
  }

  public void testSupportsPortAssignmentSyntax() {
    assertEquals("http://localhost:9876/index.html",
                 GwtSuperDevModeUrlBuilder.buildUrl("index.html", Arrays.asList("-port=9876")));
  }

  public void testPreservesAbsoluteRunPageUrl() {
    String url = "https://example.test:8443/app/index.html?mode=dev";
    assertEquals(url, GwtSuperDevModeUrlBuilder.buildUrl(url, Collections.emptyList()));
  }

  public void testDoesNotBuildUrlForEmptyRunPage() {
    assertNull(GwtSuperDevModeUrlBuilder.buildUrl("  ", Collections.emptyList()));
  }
}
