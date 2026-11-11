package org.randombits.intellij.gwt.inspections;

import com.intellij.codeInspection.deadCode.UnusedDeclarationInspectionBase;
import org.randombits.intellij.gwt.GwtTestOptions;
import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

public class GwtSerializableTest extends GwtCodeInsightTestCase {

  public void testWithDefaultConstructor() {
    doTest();
  }

  @GwtTestOptions(version = GwtVersionImpl.VERSION_2_8)
  public void testWithPrivateDefaultConstructor28() {
    doTest();
  }

  @GwtTestOptions(version = GwtVersionImpl.VERSION_1_4)
  public void testWithPrivateDefaultConstructor14() {
    doTest();
  }

  private void doTest() {
    myCodeInsightFixture.configureByFile("client/" + getTestName(false) + ".java");
    myCodeInsightFixture.enableInspections(new UnusedDeclarationInspectionBase(true));
    allowTreeAccessToGwtJreEmulationLibrary();
    myCodeInsightFixture.checkHighlighting(true, false, true);
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "highlighting/serializable";
  }
}
