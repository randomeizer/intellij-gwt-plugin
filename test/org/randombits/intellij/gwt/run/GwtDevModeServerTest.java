package org.randombits.intellij.gwt.run;

import com.intellij.execution.configurations.JavaParameters;
import org.randombits.intellij.gwt.GwtTestCase;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

import java.util.List;

public class GwtDevModeServerTest extends GwtTestCase {
  private GwtFacet myFacet;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFacet = addGwtFacet(myModule, GwtVersionImpl.VERSION_2_13);
  }

  @Override
  protected void tearDown() throws Exception {
    myFacet = null;
    super.tearDown();
  }

  public void testDefaultAndLegacyJettyAreRegistered() {
    List<? extends GwtDevModeServer> servers = new DefaultDevModeServerProvider().getServers();
    assertEquals(DefaultDevModeServer.SERVER_ID, servers.get(0).getId());
    assertTrue(servers.stream().anyMatch(server -> LegacyJettyDevModeServer.SERVER_ID.equals(server.getId())));
    boolean registered = false;
    for (GwtDevModeServerProvider provider : GwtDevModeServerProvider.EP_NAME.getExtensions()) {
      registered |= provider.getServers().stream().anyMatch(server -> LegacyJettyDevModeServer.SERVER_ID.equals(server.getId()));
    }
    assertTrue(registered);
  }

  public void testDefaultDoesNotOverrideSdkServer() {
    JavaParameters parameters = new JavaParameters();
    new DefaultDevModeServer().patchParameters(parameters, "/war", myFacet);
    assertEmpty(parameters.getProgramParametersList().getList());
  }

  public void testLegacyJettyAddsServletLauncherOnce() {
    JavaParameters parameters = new JavaParameters();
    parameters.getProgramParametersList().add("-superDevMode");
    GwtDevModeServer server = new LegacyJettyDevModeServer();
    server.patchParameters(parameters, "/war", myFacet);
    server.patchParameters(parameters, "/war", myFacet);
    assertEquals(List.of("-superDevMode", "-server", "com.google.gwt.dev.shell.jetty.JettyLauncher"),
                 parameters.getProgramParametersList().getList());
  }

  public void testExplicitServerTakesPrecedence() {
    assertPreservedArguments(List.of("-server", "example.CustomLauncher"));
  }

  public void testNoServerTakesPrecedence() {
    assertPreservedArguments(List.of("-noserver"));
  }

  public void testExplicitServerAndNoServerAreNotRewritten() {
    assertPreservedArguments(List.of("-noserver", "-server", "example.CustomLauncher"));
  }

  private void assertPreservedArguments(List<String> arguments) {
    JavaParameters parameters = new JavaParameters();
    parameters.getProgramParametersList().addAll(arguments);
    new LegacyJettyDevModeServer().patchParameters(parameters, "/war", myFacet);
    assertEquals(arguments, parameters.getProgramParametersList().getList());
  }
}
