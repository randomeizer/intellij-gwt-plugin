package org.randombits.intellij.gwt;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Annotation constants needed by GWT, using the Java PSI available without Ultimate. */
public final class GwtAnnotationUtil {
  private GwtAnnotationUtil() {
  }

  public static @Nullable Object getValue(@NotNull PsiAnnotation annotation, @NotNull String attribute) {
    return evaluate(annotation.findAttributeValue(attribute));
  }

  public static @NotNull List<String> getStringArrayValue(@NotNull PsiAnnotation annotation, @NotNull String attribute) {
    PsiAnnotationMemberValue value = annotation.findAttributeValue(attribute);
    PsiAnnotationMemberValue[] values = value instanceof PsiArrayInitializerMemberValue array
                                       ? array.getInitializers() : new PsiAnnotationMemberValue[]{value};
    List<String> result = new ArrayList<>();
    for (PsiAnnotationMemberValue item : values) {
      if (evaluate(item) instanceof String text) {
        result.add(text);
      }
    }
    return result;
  }

  private static @Nullable Object evaluate(@Nullable PsiAnnotationMemberValue value) {
    return value instanceof PsiExpression expression
           ? JavaPsiFacade.getInstance(value.getProject()).getConstantEvaluationHelper().computeConstantExpression(expression)
           : null;
  }
}
