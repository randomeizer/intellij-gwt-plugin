package org.randombits.intellij.gwt.clientBundle.css.language.psi.impl;

import org.randombits.intellij.gwt.clientBundle.css.language.GwtCssElementTypes;
import org.randombits.intellij.gwt.clientBundle.css.language.psi.GwtCssEval;

public class GwtCssEvalImpl extends GwtCssDeclarationElementBase implements GwtCssEval {
  public GwtCssEvalImpl() {
    super(GwtCssElementTypes.CSS_EVAL);
  }
}
