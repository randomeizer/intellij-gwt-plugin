package org.randombits.intellij.gwt.html;

import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;

public class CssClassReferencesTest extends GwtCodeInsightTestCase {
  public void testCssReferenceToCssFileUnderWebRootByAbsolutePath() {
    assertResolvesToCssClass("src/ppp/client/Absolute.java", "by-absolute-class");
  }

  public void testCssReferenceToCssFileUnderWebRootByRelativePath() {
    assertResolvesToCssClass("src/ppp/client/Relative.java", "by-relative-class");
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "references/cssRefToWebRoot";
  }
}
