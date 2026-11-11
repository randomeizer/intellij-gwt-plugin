package org.randombits.intellij.gwt.jps;

import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.JpsGwtModuleExtension;
import org.randombits.intellij.gwt.jps.model.impl.GwtModuleExtensionProperties;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtModuleExtensionImpl;
import org.jetbrains.jps.model.module.JpsModule;

public final class JpsGwtTestUtil {
  public static JpsGwtModuleExtension addGwtExtension(JpsModule appModule, final GwtModuleExtensionProperties properties) {
    JpsGwtModuleExtension extension = new JpsGwtModuleExtensionImpl(properties);
    JpsGwtExtensionService.getInstance().setExtension(appModule, extension);
    return extension;
  }

  public static JpsGwtModuleExtension addGwtExtension(JpsModule module) {
    return addGwtExtension(module, new GwtModuleExtensionProperties());
  }
}
