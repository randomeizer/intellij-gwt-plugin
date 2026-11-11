package org.randombits.intellij.gwt.clientBundle.css.language.psi.impl;

import org.randombits.intellij.gwt.clientBundle.css.language.GwtCssElementTypes;
import org.randombits.intellij.gwt.clientBundle.css.language.psi.GwtCssIfStatement;

public class GwtCssIfStatementImpl extends GwtCssRulesetWrappingElementBase implements GwtCssIfStatement {
  public GwtCssIfStatementImpl() {
    super(GwtCssElementTypes.CSS_IF_STATEMENT);
  }
}
