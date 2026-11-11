package org.randombits.intellij.gwt.model;

import org.randombits.intellij.gwt.GwtTestOptions;
import org.randombits.intellij.gwt.psi.GwtSourcePathsRefresher;
import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;

public class GwtLanguageLevelPusherWithDefaultClientDirectoryTest extends GwtCodeInsightTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(myCodeInsightFixture.getProject(), LanguageLevel.JDK_1_8);
  }

  @GwtTestOptions(version = GwtVersionImpl.VERSION_2_7)
  public void testDefault27() {
    Project project = myCodeInsightFixture.getProject();
    PsiResolveHelper resolveHelper = PsiResolveHelper.getInstance(project);

    VirtualFile client = myCodeInsightFixture.findFileInTempDir("client/Client.java");

    waitForGwtSourcePathsRefresher(project);
    assertEquals(LanguageLevel.JDK_1_7, resolveHelper.getEffectiveLanguageLevel(client));
  }

  @Override
  protected String getBaseDirectoryPath() {
    return "model/languageLevelPusherForDefaultClientDir";
  }
}
