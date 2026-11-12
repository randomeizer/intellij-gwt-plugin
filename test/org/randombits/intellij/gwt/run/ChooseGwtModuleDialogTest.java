package org.randombits.intellij.gwt.run;

import com.intellij.testFramework.LightPlatformTestCase;

import javax.swing.JComboBox;

public class ChooseGwtModuleDialogTest extends LightPlatformTestCase {
  private ChooseGwtModuleDialog myDialog;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myDialog = new ChooseGwtModuleDialog(getProject(), new String[]{"sample.First", "sample.Second"});
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myDialog != null) myDialog.doCancelAction();
    }
    finally {
      super.tearDown();
    }
  }

  public void testFirstModuleIsSelectedInitially() {
    assertEquals(0, myDialog.getSelectedIndex());
    assertEquals("GWT Module", myDialog.getTitle());
  }

  public void testUserCanSelectAnotherModule() {
    ((JComboBox<?>)myDialog.getPreferredFocusedComponent()).setSelectedIndex(1);
    assertEquals(1, myDialog.getSelectedIndex());
  }

  public void testCancelDoesNotAcceptModuleChoice() {
    var dialog = myDialog;
    myDialog = null;
    dialog.doCancelAction();
    assertFalse(dialog.isOK());
  }
}
