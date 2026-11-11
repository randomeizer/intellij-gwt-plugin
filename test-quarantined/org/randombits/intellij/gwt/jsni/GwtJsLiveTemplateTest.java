package org.randombits.intellij.gwt.jsni;

import org.randombits.intellij.gwt.GwtTestCase;
import org.randombits.intellij.gwt.javascript.GwtJsTestUtil;
import com.intellij.lang.javascript.JSLiveTemplatesTestBase;

public class GwtJsLiveTemplateTest extends JSLiveTemplatesTestBase {
  public void testItar() {
    doTest("itar", "GwtJavaScript", "JavaScript");
  }

  @Override
  protected String getBasePath() {
    return "/jsni/liveTemplate/";
  }

  @Override
  protected String getTestDataPath() {
    return GwtTestCase.getGwtTestDataPath();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    GwtJsTestUtil.setUpGwtDialect();
  }
}
