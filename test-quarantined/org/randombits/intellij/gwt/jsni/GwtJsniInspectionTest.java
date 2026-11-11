package org.randombits.intellij.gwt.jsni;

import org.randombits.intellij.gwt.GwtTestOptions;
import org.randombits.intellij.gwt.inspections.GwtJavaScriptReferencesInspection;
import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

public class GwtJsniInspectionTest extends GwtCodeInsightTestCase {
  @GwtTestOptions(version = GwtVersionImpl.VERSION_2_5)
  public void testQualifiedReferences() {
    myCodeInsightFixture.enableInspections(GwtJavaScriptReferencesInspection.class);
    myCodeInsightFixture.testHighlighting("src/client/QualifiedReferences.java", "src/client/Util.java");
  }

  @GwtTestOptions(version = GwtVersionImpl.VERSION_2_7)
  public void testShortReference() {
    myCodeInsightFixture.allowTreeAccessForAllFiles();
    myCodeInsightFixture.enableInspections(GwtJavaScriptReferencesInspection.class);
    myCodeInsightFixture.testHighlighting("src/client/ShortReferences.java", "src/client/Util.java",
                                          "src/client/pack/NotImported.java");
  }

  @GwtTestOptions(version = GwtVersionImpl.VERSION_2_7)
  public void testWildcardMethodReferences() {
    myCodeInsightFixture.allowTreeAccessForAllFiles();
    myCodeInsightFixture.enableInspections(GwtJavaScriptReferencesInspection.class);
    myCodeInsightFixture.testHighlighting("src/client/WildcardMethodReferences.java");
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "inspections/referencesInJSNI";
  }
}
