package org.randombits.intellij.gwt.jps.model.impl;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;

@Tag("module")
public class GwtModulePackagingProperties {
  @Attribute("name")
  public String myName;

  @Attribute("path")
  public String myPath;

  @Attribute("enabled")
  public boolean myEnabled = true;
}
