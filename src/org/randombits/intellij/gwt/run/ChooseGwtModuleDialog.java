package org.randombits.intellij.gwt.run;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.GwtBundle;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

final class ChooseGwtModuleDialog extends DialogWrapper {
  private final ComboBox<String> myModules;

  ChooseGwtModuleDialog(Project project, String[] modules) {
    super(project, true);
    myModules = new ComboBox<>(modules);
    setTitle(GwtBundle.message("dialog.title.choose.gwt.module"));
    init();
  }

  int getSelectedIndex() {
    return myModules.getSelectedIndex();
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    JLabel label = new JLabel(GwtBundle.message("choose.text.select.gwt.module"));
    label.setLabelFor(myModules);
    panel.add(label, BorderLayout.NORTH);
    panel.add(myModules, BorderLayout.CENTER);
    return panel;
  }

  @Override
  public @NotNull JComponent getPreferredFocusedComponent() {
    return myModules;
  }
}
