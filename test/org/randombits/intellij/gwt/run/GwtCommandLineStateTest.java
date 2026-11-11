package org.randombits.intellij.gwt.run;

import com.intellij.execution.ShortenCommandLine;
import com.intellij.execution.configurations.JavaParameters;
import junit.framework.TestCase;

public class GwtCommandLineStateTest extends TestCase {

  public void testUsesArgumentFileOnCurrentModularJdk() {
    ShortenCommandLine defaultMethod =
      ShortenCommandLine.getDefaultMethodForJdkLevel(System.getProperty("java.home"));
    assertEquals(ShortenCommandLine.ARGS_FILE,
                 GwtCommandLineState.getDevModeShortenCommandLineMethod(defaultMethod));
  }

  public void testUsesClasspathFileInsteadOfManifestJar() {
    assertEquals(ShortenCommandLine.CLASSPATH_FILE,
                 GwtCommandLineState.getDevModeShortenCommandLineMethod(ShortenCommandLine.MANIFEST));
  }

  public void testArgumentFilePreservesDynamicClasspath() {
    JavaParameters parameters = new JavaParameters();
    parameters.setShortenCommandLine(GwtCommandLineState.getDevModeShortenCommandLineMethod(ShortenCommandLine.ARGS_FILE));

    assertTrue(parameters.isDynamicClasspath());
    assertTrue(parameters.isArgFile());
    assertFalse(parameters.isClasspathFile());
    assertFalse(parameters.isUseClasspathJar());
  }
}
