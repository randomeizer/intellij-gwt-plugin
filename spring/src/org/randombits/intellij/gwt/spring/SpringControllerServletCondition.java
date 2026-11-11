package org.randombits.intellij.gwt.spring;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

final class SpringControllerServletCondition implements Condition<PsiClass> {

  private static final String REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping";

  @Override
  public boolean value(@NotNull PsiClass psiClass) {
    final PsiModifierList modifierList = psiClass.getModifierList();
    return modifierList != null && modifierList.hasAnnotation(REQUEST_MAPPING);
  }
}
