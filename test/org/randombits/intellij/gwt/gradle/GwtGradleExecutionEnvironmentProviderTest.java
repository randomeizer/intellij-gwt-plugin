package org.randombits.intellij.gwt.gradle;

import com.intellij.pom.java.LanguageLevel;
import junit.framework.TestCase;

public class GwtGradleExecutionEnvironmentProviderTest extends TestCase {
  public void testLegacySourceLevelOptions() {
    assertEquals("1.3", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_1_3));
    assertEquals("1.4", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_1_4));
  }

  public void testModernSourceLevelOptions() {
    assertEquals("5", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_1_5));
    assertEquals("8", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_1_8));
    assertEquals("9", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_1_9));
    assertEquals("17", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_17));
    assertEquals("25", GwtGradleExecutionEnvironmentProvider.sourceLevelOption(LanguageLevel.JDK_25));
  }
}
