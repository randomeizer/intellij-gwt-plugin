package org.randombits.intellij.gwt.sdk;

import com.intellij.testFramework.LightPlatformTestCase;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionDetector;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

public class GwtSdkDetectionTest extends LightPlatformTestCase {
  public void testDetectVersionFromDevJarMetadata() throws Exception {
    assertSdkVersion("2.12.2", GwtVersionImpl.VERSION_2_12);
    assertSdkVersion("2.13.0", GwtVersionImpl.VERSION_2_13);
    assertSdkVersion("2.13.1", GwtVersionImpl.VERSION_2_13);
  }

  private static void assertSdkVersion(String version, GwtVersionImpl expected) throws Exception {
    Path sdk = Files.createTempDirectory("gwt-sdk-detection");
    Path devJar = sdk.resolve("gwt-dev.jar");
    try {
      try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(devJar))) {
        jar.putNextEntry(new JarEntry("com/google/gwt/dev/About.properties"));
        jar.write(("gwt.version=" + version + "\n").getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
      }
      assertSame(expected, GwtVersionDetector.detectGwtVersion(sdk.toString()));
    }
    finally {
      Files.deleteIfExists(devJar);
      Files.deleteIfExists(sdk);
    }
  }
}
