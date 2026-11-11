package org.randombits.intellij.gwt.templates;

import org.randombits.intellij.gwt.GwtBundle;
import org.randombits.intellij.gwt.icons.GwtIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;

public final class GwtFileTemplateGroupDescriptorFactory implements FileTemplateGroupDescriptorFactory {
  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor(GwtBundle.message("file.template.group.title.gwt"),
                                                                              GwtIcons.GoogleSmall);
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    for (String template : GwtTemplates.TEMPLATES) {
      group.addTemplate(new FileTemplateDescriptor(template, fileTypeManager.getFileTypeByFileName(template).getIcon()));
    }
    return group;
  }
}
