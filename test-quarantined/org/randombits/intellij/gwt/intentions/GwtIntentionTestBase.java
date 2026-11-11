package org.randombits.intellij.gwt.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import org.randombits.intellij.gwt.references.GwtCodeInsightTestCase;

public abstract class GwtIntentionTestBase extends GwtCodeInsightTestCase {

  protected void doTest(String uiXmlFile, String componentFile, String componentFileAfter, IntentionAction intention) {
    myCodeInsightFixture.configureByFiles(uiXmlFile, componentFile);

    if (intention.isAvailable(myCodeInsightFixture.getProject(), myCodeInsightFixture.getEditor(), myCodeInsightFixture.getFile())) {
      myCodeInsightFixture.launchAction(intention);
    }

    myCodeInsightFixture.openFileInEditor(myCodeInsightFixture.findFileInTempDir(componentFile));
    myCodeInsightFixture.checkResultByFile(componentFileAfter);
  }
}
