package org.randombits.intellij.gwt.jsni;

import org.randombits.intellij.gwt.inspections.GwtJavaFromJSMethodCallsInspection;
import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;

public class GwtJavaFromJSMethodCallsInspectionTest extends GwtCodeInsightTestCase {
  public void testMethodCalls() {
    myCodeInsightFixture.allowTreeAccessForAllFiles();
    myCodeInsightFixture.enableInspections(GwtJavaFromJSMethodCallsInspection.class);
    myCodeInsightFixture.testHighlighting("src/client/MyClass.java");
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "inspections/javaFromJSMethodCalls";
  }
}
