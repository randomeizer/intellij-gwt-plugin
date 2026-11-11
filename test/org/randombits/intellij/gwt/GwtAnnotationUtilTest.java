package org.randombits.intellij.gwt;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;

import java.util.List;

public class GwtAnnotationUtilTest extends LightJavaCodeInsightFixtureTestCase {
  private static final LightProjectDescriptor DESCRIPTOR = new ProjectDescriptor(LanguageLevel.JDK_25) {
    @Override
    public Sdk getSdk() {
      return JavaSdk.getInstance().createJdk("GWT annotation test JDK", System.getProperty("java.home"), false);
    }
  };

  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return DESCRIPTOR;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("public @interface Resource { String[] value(); boolean provided() default false; String tagname() default \"\"; }");
  }

  public void testConstantsAndArrayShorthand() {
    PsiAnnotation annotation = annotation("@Resource(\".css\") class View {}");
    assertEquals(List.of(".css"), GwtAnnotationUtil.getStringArrayValue(annotation, "value"));
    assertEquals(Boolean.FALSE, GwtAnnotationUtil.getValue(annotation, "provided"));
    assertEquals("", GwtAnnotationUtil.getValue(annotation, "tagname"));
  }

  public void testArrayAndConstantExpressions() {
    myFixture.addClass("public class Constants { public static final String EXT = \".css\"; }");
    PsiAnnotation annotation = annotation("@Resource(value = {Constants.EXT, \".gss\"}, provided = true, tagname = \"head\" + \"er\") " +
                                          "class View {}");
    assertEquals(List.of(".css", ".gss"), GwtAnnotationUtil.getStringArrayValue(annotation, "value"));
    assertEquals(Boolean.TRUE, GwtAnnotationUtil.getValue(annotation, "provided"));
    assertEquals("header", GwtAnnotationUtil.getValue(annotation, "tagname"));
  }

  public void testUnresolvedAndMissingValues() {
    PsiAnnotation annotation = annotation("@Resource({missing, \".css\"}) class View {}");
    assertEquals(List.of(".css"), GwtAnnotationUtil.getStringArrayValue(annotation, "value"));
    assertNull(GwtAnnotationUtil.getValue(annotation, "missing"));
    assertEmpty(GwtAnnotationUtil.getStringArrayValue(annotation, "missing"));
  }

  private PsiAnnotation annotation(String source) {
    PsiClass view = myFixture.addClass(source);
    PsiAnnotation annotation = view.getModifierList().findAnnotation("Resource");
    assertNotNull(annotation);
    return annotation;
  }
}
