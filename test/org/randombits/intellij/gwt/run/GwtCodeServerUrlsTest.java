package org.randombits.intellij.gwt.run;

import com.intellij.execution.process.NopProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.testFramework.LightPlatformTestCase;

import java.util.List;
import java.util.Set;

public class GwtCodeServerUrlsTest extends LightPlatformTestCase {
  public void testDevModeWebPortDoesNotReplaceCodeServerPort() {
    var endpoints = GwtCodeServerUrls.fromParameters(List.of("-port", "8880", "-codeServerPort", "9017", "-bindAddress", "127.0.0.2"),
      "https://page.example:8443/index.html", "requestor.example:8880");
    assertContainsElements(endpoints, "http://localhost:9017", "http://127.0.0.2:9017", "http://page.example:9017", "http://requestor.example:9017");
    assertFalse(endpoints.contains("http://localhost:8880"));
    assertFalse(endpoints.contains("http://page.example:8443"));
  }

  public void testDefaultsLegacyPortIpv6AndInvalidValues() {
    assertContainsElements(GwtCodeServerUrls.fromParameters(List.of(), null, null), "http://localhost:9876", "http://[::1]:9876");
    assertContainsElements(GwtCodeServerUrls.fromParameters(List.of("-codeServerPort=9997"), null, null), "http://localhost:9876");
    assertContainsElements(GwtCodeServerUrls.fromParameters(List.of("-codeServerPort", "-1"), null, null), "http://localhost:9876");
    assertEmpty(GwtCodeServerUrls.fromParameters(List.of("-codeServerPort", "0"), null, null));
    assertEmpty(GwtCodeServerUrls.fromParameters(List.of("-codeServerPort", "auto"), null, null));
    assertEmpty(GwtCodeServerUrls.fromParameters(List.of("-codeServerPort", "65536"), null, null));
    assertEmpty(GwtCodeServerUrls.fromParameters(List.of("-codeServerPort", "invalid"), null, null));
    assertFalse(GwtCodeServerUrls.fromParameters(List.of("-bindAddress", "0.0.0.0"), null, null).contains("http://0.0.0.0:9876"));
  }

  public void testRunningSnapshotAndFragmentedAutoPortDiscoveryOnEitherStream() {
    var process = new NopProcessHandler();
    GwtCodeServerUrls.attach(process, List.of("-codeServerPort", "0"), null);
    process.startNotify();
    try {
      process.notifyTextAvailable("The code server is rea", ProcessOutputTypes.STDOUT);
      process.notifyTextAvailable("an unrelated error\n", ProcessOutputTypes.STDERR);
      assertEmpty(GwtCodeServerUrls.forProcess(process));
      process.notifyTextAvailable("dy at http://127.0.0.2:40123/\n", ProcessOutputTypes.STDOUT);
      assertEquals(Set.of("http://127.0.0.2:40123"), GwtCodeServerUrls.forProcess(process));
      process.notifyTextAvailable("[INFO] The code server is ready at http://[::1]:40124/\r\n", ProcessOutputTypes.STDERR);
      assertContainsElements(GwtCodeServerUrls.forProcess(process), "http://[::1]:40124");
      for (String bad : List.of("http://user:password@example.test:9876/", "http://example.test:0/", "file:///tmp/source", "http://example.test:9876/path")) {
        GwtCodeServerUrls.recordReadyLine(process, "The code server is ready at " + bad);
      }
      assertEquals(2, GwtCodeServerUrls.forProcess(process).size());
    }
    finally { process.destroyProcess(); }
  }
}
