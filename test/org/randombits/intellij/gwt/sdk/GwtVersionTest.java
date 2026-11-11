package org.randombits.intellij.gwt.sdk;

import org.randombits.intellij.gwt.sdk.impl.GwtVersionDetector;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;
import com.intellij.pom.java.LanguageLevel;
import junit.framework.TestCase;

import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_1_6;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_0;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_4;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_5;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_6;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_7;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_8;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_9;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_10;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_11;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_12;
import static org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl.VERSION_2_13;

public class GwtVersionTest extends TestCase {

  public void testModuleDocType() {
    String docType28 = VERSION_2_8.getGwtModuleDocTypeString();
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.8.0//EN\" " +
                 "\"http://gwtproject.org/doctype/2.8.0/gwt-module.dtd\">", docType28);

    String docType27 = VERSION_2_7.getGwtModuleDocTypeString();
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.7.0//EN\" " +
                 "\"http://gwtproject.org/doctype/2.7.0/gwt-module.dtd\">", docType27);

    String docType26 = VERSION_2_6.getGwtModuleDocTypeString();
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.6.0//EN\" " +
                 "\"http://gwtproject.org/doctype/2.6.0/gwt-module.dtd\">", docType26);

    String docType20 = VERSION_2_0.getGwtModuleDocTypeString();
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.0//EN\" " +
                 "\"http://google-web-toolkit.googlecode.com/svn/releases/2.0/distro-source/core/src/gwt-module.dtd\">", docType20);

    String docType16 = VERSION_1_6.getGwtModuleDocTypeString();
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 1.6//EN\" " +
                 "\"http://google-web-toolkit.googlecode.com/svn/releases/1.6/distro-source/core/src/gwt-module.dtd\">", docType16);
  }

  public void testHighestSupportedLanguageLevel() {
    // GWT 2.10 supports Java 11; GWT 2.11 through 2.13 support Java 17 language features.
    // The facet must not cap those versions at Java 8 (IDEA-383876, IDEA-283112).
    assertEquals(LanguageLevel.JDK_17, VERSION_2_13.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_17, VERSION_2_12.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_17, VERSION_2_11.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_11, VERSION_2_10.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_8, VERSION_2_9.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_8, VERSION_2_8.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_7, VERSION_2_7.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_7, VERSION_2_6.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, VERSION_2_5.getHighestSupportedLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_6, VERSION_2_4.getHighestSupportedLanguageLevel());
  }

  public void testVersionParsing() {
    assertEquals(VERSION_2_8, GwtVersionDetector.getGwtVersionFromString("2.8.2"));
    assertEquals(VERSION_2_9, GwtVersionDetector.getGwtVersionFromString("2.9.0"));
    assertEquals(VERSION_2_10, GwtVersionDetector.getGwtVersionFromString("2.10.0"));
    assertEquals(VERSION_2_11, GwtVersionDetector.getGwtVersionFromString("2.11.0"));
    assertEquals(VERSION_2_12, GwtVersionDetector.getGwtVersionFromString("2.12.0"));
    assertEquals(VERSION_2_12, GwtVersionDetector.getGwtVersionFromString("2.12.1"));
    assertEquals(VERSION_2_12, GwtVersionDetector.getGwtVersionFromString("2.12.2"));
    assertEquals(VERSION_2_12, GwtVersionDetector.getGwtVersionFromString("2.12.99"));
    assertEquals(VERSION_2_13, GwtVersionDetector.getGwtVersionFromString("2.13.snapshot"));
    assertEquals(VERSION_2_13, GwtVersionDetector.getGwtVersionFromString("2.13.0-SNAPSHOT"));
    assertEquals(VERSION_2_13, GwtVersionDetector.getGwtVersionFromString("2.13.0"));
    assertEquals(VERSION_2_13, GwtVersionDetector.getGwtVersionFromString("2.13.1"));
    assertEquals(VERSION_2_13, GwtVersionDetector.getGwtVersionFromString("2.13.99"));
    // Until the next explicit profile is added, newer SDKs use the newest known profile.
    assertEquals(VERSION_2_13, GwtVersionDetector.getGwtVersionFromString("2.14.0"));
  }

  public void testGwt212UsesLatestKnownCompilerAndDevModeEntrypoints() {
    assertEquals(VERSION_2_11.getCompilerClassName(), VERSION_2_12.getCompilerClassName());
    assertEquals(VERSION_2_11.getDevModeClass(), VERSION_2_12.getDevModeClass());
  }

  public void testGwt213CompilerAndDevModeCapabilities() {
    assertSame(VERSION_2_13, GwtVersionImpl.LATEST);
    assertEquals("com.google.gwt.dev.Compiler", VERSION_2_13.getCompilerClassName());
    assertEquals("com.google.gwt.dev.DevMode", VERSION_2_13.getDevModeClass());
    assertEquals("-war", VERSION_2_13.getCompilerOutputDirParameterName());
    assertTrue(VERSION_2_13.isSuperDevModeSupported());
    assertTrue(VERSION_2_13.isSuperDevModeUsedByDefault());
    assertFalse(VERSION_2_13.isLegacyJarForNewSuperDevModeRequired());
    assertTrue(VERSION_2_13.isUiBinderSupported());
    assertTrue(VERSION_2_13.isUiRendererSupported());
  }

  public void testGwt213ModuleDoctypeMatchesSdkGenerator() {
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.13.1//EN\" " +
                 "\"http://gwtproject.org/doctype/2.13.1/gwt-module.dtd\">",
                 VERSION_2_13.getGwtModuleDocTypeString());
  }

  public void testGwt212ModuleDoctypeMatchesSdkGenerator() {
    assertEquals("<!DOCTYPE module PUBLIC \"-//Google Inc.//DTD Google Web Toolkit 2.12.2//EN\" " +
                 "\"http://gwtproject.org/doctype/2.12.2/gwt-module.dtd\">",
                 VERSION_2_12.getGwtModuleDocTypeString());
  }
}
