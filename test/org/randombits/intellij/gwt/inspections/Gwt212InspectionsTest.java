package org.randombits.intellij.gwt.inspections;

import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

public class Gwt212InspectionsTest extends GwtInspectionsTestCase {
  @Override
  protected GwtVersionImpl getVersion() {
    return GwtVersionImpl.VERSION_2_12;
  }

  public void testJava17SyntaxInClientCode() {
    doTest(new NonJREEmulationClassesInClientCodeInspection());
  }
}
