package org.randombits.intellij.gwt.inspections;

import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;
import com.siyeh.ig.inheritance.InterfaceNeverImplementedInspection;

public class GwtClientBundleHighlightingTest extends GwtCodeInsightTestCase {
  public void testDoNotHighlightCssResourceInterfaceAsNotImplemented() {
    allowTreeAccessToGwtJreEmulationLibrary();
    myCodeInsightFixture.configureByFile("src/client/MyCssResource.java");
    myCodeInsightFixture.enableInspections(InterfaceNeverImplementedInspection.class);
    myCodeInsightFixture.checkHighlighting();
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "codeInsight/clientBundle";
  }
}
