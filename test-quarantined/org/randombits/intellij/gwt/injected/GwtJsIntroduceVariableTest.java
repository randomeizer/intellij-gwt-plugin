package org.randombits.intellij.gwt.injected;

import org.randombits.intellij.gwt.GwtTestCase;
import com.intellij.lang.javascript.refactoring.introduceVariable.JSIntroduceVariableTestCase;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

public class GwtJsIntroduceVariableTest extends JSIntroduceVariableTestCase {

  public void testIntroduceVar() {
    doTest("a", false, ".java");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return GwtTestCase.getGwtTestDataPath() + FileUtil.toSystemDependentName("jsni/editor/");
  }
}
