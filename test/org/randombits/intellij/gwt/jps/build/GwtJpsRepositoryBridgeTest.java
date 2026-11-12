package org.randombits.intellij.gwt.jps.build;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.idea.maven.aether.RetryProvider;
import org.jetbrains.jps.incremental.dependencies.DependencyAuthenticationDataProvider;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.model.serialization.JpsSerializationManager;
import org.randombits.intellij.gwt.jps.model.GwtRepositoryConfiguration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GwtJpsRepositoryBridgeTest extends LightPlatformTestCase {
  private Path directory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    directory = FileUtil.createTempDirectory("gwt-jps-repository", "").toPath();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (directory != null) FileUtil.delete(directory.toFile());
    }
    finally {
      super.tearDown();
    }
  }

  public void testVersionedBridgeRetainsOrderedIdsUrlsAndEmptyConfiguration() {
    var repositories = List.of(new GwtRepositoryConfiguration.Repository("first", "file:///fixture/one"),
      new GwtRepositoryConfiguration.Repository("second", "https://example.invalid/二?q=a&b=c"));
    var decoded = GwtRepositoryConfiguration.decode(GwtRepositoryConfiguration.encode(repositories));
    assertEquals(2, decoded.size());
    for (int i = 0; i < repositories.size(); i++) {
      assertEquals(repositories.get(i).id, decoded.get(i).id);
      assertEquals(repositories.get(i).url, decoded.get(i).url);
    }
    assertEmpty(GwtRepositoryConfiguration.decode(GwtRepositoryConfiguration.encode(List.of())));
    var project = JpsElementFactory.getInstance().createModel().getProject();
    GwtRepositoryConfiguration.set(project, repositories);
    GwtRepositoryConfiguration.set(project, List.of());
    assertEmpty(GwtRepositoryConfiguration.get(project));
  }

  public void testMalformedUnknownAndOversizedBridgeFailsWithoutEchoingPayload() {
    for (String payload : List.of("sensitive-invalid-value", Base64.getEncoder().encodeToString(new byte[]{0, 0, 0, 2, 0, 0, 0, 0}),
                                 "A".repeat(1500000))) {
      try {
        GwtRepositoryConfiguration.decode(payload);
        fail("Invalid configuration should not silently use public repositories");
      }
      catch (IllegalArgumentException expected) {
        assertFalse(expected.getMessage().contains(payload.substring(0, Math.min(payload.length(), 20))));
      }
    }
  }

  public void testStandaloneDirectoryAndFileBasedProjectsReadExistingRepositoryComponent() throws Exception {
    String component = "<component name='RemoteRepositoriesConfiguration'><remote-repository>" +
      "<option name='id' value='private-fixture'/><option name='name' value='Fixture'/>" +
      "<option name='url' value='file:///fixture/repository'/></remote-repository></component>";
    Path idea = Files.createDirectories(directory.resolve("project/.idea"));
    Files.writeString(idea.resolve("modules.xml"), "<project version='4'><component name='ProjectModuleManager'><modules/></component></project>");
    Files.writeString(idea.resolve("jarRepositories.xml"), "<project version='4'>" + component + "</project>");
    Path ipr = directory.resolve("fixture.ipr");
    Files.writeString(ipr, "<project version='4'>" + component + "</project>");
    for (Path location : List.of(idea.getParent(), ipr)) {
      var project = JpsSerializationManager.getInstance().loadProject(location.toString(), Map.of());
      var repositories = GwtRepositoryConfiguration.get(project);
      assertEquals(1, repositories.size());
      assertEquals("private-fixture", repositories.getFirst().id);
      assertEquals("file:///fixture/repository", repositories.getFirst().url);
    }
  }

  public void testModelLocalRepositoryVariableUsesPublicStringLookup() {
    var global = JpsElementFactory.getInstance().createModel().getGlobal();
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(global)
      .addPathVariable("MAVEN_REPOSITORY", directory.resolve("local").toString());
    assertEquals(directory.resolve("local").toFile(), GwtJpsRepositoryManager.localRepository(global));
  }

  public void testNativeMavenOptionsAndSettingsPrecedence() throws Exception {
    assertNull(GwtJpsMavenSettings.findMavenRepositoryProperty(null));
    assertEquals("/custom path/repo", GwtJpsMavenSettings.findMavenRepositoryProperty(
      "-Dmaven.repo.local=/first -Xmx1g -Dmaven.repo.local=\"/custom path/repo\""));
    Path global = directory.resolve("global.xml");
    Path user = directory.resolve("user.xml");
    Files.writeString(global, settings("http://maven.apache.org/SETTINGS/1.2.0", "global"));
    Files.writeString(user, settings("", "user"));
    assertEquals("user", GwtJpsMavenSettings.loadAuthenticationSettings(global, user).get("fixture").username);
    assertEquals("/fixture/local", GwtJpsMavenSettings.getRepositoryFromSettings(global));
    Files.writeString(user, settings("http://unrelated/schema", "ignored"));
    assertNull(GwtJpsMavenSettings.getRepositoryFromSettings(user));
    assertEquals("global", GwtJpsMavenSettings.loadAuthenticationSettings(global, user).get("fixture").username);
  }

  public void testRealResolverAuthenticationPrecedenceAndCancellation() throws Exception {
    String property = GwtJpsRepositoryManager.AUTH_PROPERTY_PREFIX + "fixture";
    String original = System.getProperty(property);
    var expected = new AtomicReference<String>();
    var requests = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      String authorization = "Basic " + Base64.getEncoder().encodeToString(expected.get().getBytes(StandardCharsets.UTF_8));
      if (!authorization.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
        exchange.getResponseHeaders().set("WWW-Authenticate", "Basic realm=\"GWT fixture\"");
        exchange.sendResponseHeaders(401, -1);
      }
      else {
        String path = exchange.getRequestURI().getPath();
        byte[] content = path.endsWith(".pom") ?
          "<project><modelVersion>4.0.0</modelVersion><groupId>fixture</groupId><artifactId>sdk</artifactId><version>1</version></project>"
            .getBytes(StandardCharsets.UTF_8) : path.endsWith(".jar") ? new byte[]{1, 2, 3} : null;
        if (content == null) exchange.sendResponseHeaders(404, -1);
        else {
          exchange.sendResponseHeaders(200, content.length);
          exchange.getResponseBody().write(content);
        }
      }
      exchange.close();
    });
    server.start();
    try {
      String url = "http://localhost:" + server.getAddress().getPort();
      var repo = new GwtRepositoryConfiguration.Repository("fixture", url);
      var settings = Map.of("fixture", new GwtJpsMavenSettings.RemoteRepositoryAuthentication("settings", "settings-password"));
      var provider = new DependencyAuthenticationDataProvider() {
        @Override
        public AuthenticationData provideAuthenticationData(String repositoryUrl) {
          assertEquals(url, repositoryUrl);
          return new AuthenticationData("provider", "provider-password");
        }
      };
      for (String source : List.of("property", "provider", "settings")) {
        if (source.equals("property")) System.setProperty(property, "property:property-password");
        else System.clearProperty(property);
        expected.set(source + ":" + source + "-password");
        var auth = GwtJpsRepositoryManager.authentication(repo, settings, source.equals("settings") ? List.of() : List.of(provider));
        var remote = ArtifactRepositoryManager.createRemoteRepository(repo.id, repo.url, auth);
        var resolver = new ArtifactRepositoryManager(directory.resolve(source).toFile(), List.of(remote), ProgressConsumer.DEAF, RetryProvider.disabled());
        int before = requests.get();
        var files = ApplicationManager.getApplication().executeOnPooledThread(() ->
          resolver.resolveDependency("fixture", "sdk", "1", true, List.of())).get(15, TimeUnit.SECONDS);
        assertEquals(1, files.size());
        assertTrue(files.iterator().next().isFile());
        assertTrue(requests.get() > before);
      }
      System.setProperty(property, "property:property-password");
      expected.set("property:property-password");
      var cancellationChecks = new AtomicInteger();
      var resolver = GwtJpsRepositoryManager.create(directory.resolve("cancelled").toFile(), List.of(repo), new ProgressConsumer() {
        @Override public void consume(String message) {}
        @Override public boolean isCanceled() { cancellationChecks.incrementAndGet(); return true; }
      });
      try {
        ApplicationManager.getApplication().executeOnPooledThread(() ->
          resolver.resolveDependency("fixture", "sdk", "1", true, List.of())).get(15, TimeUnit.SECONDS);
        fail("Cancelled resolution unexpectedly succeeded");
      }
      catch (ExecutionException expectedCancellation) {
        assertTrue("Resolver did not consult the cancellation callback", cancellationChecks.get() > 0);
      }
    }
    finally {
      if (original == null) System.clearProperty(property);
      else System.setProperty(property, original);
      server.stop(0);
    }
  }

  private static String settings(String namespace, String username) {
    return "<settings xmlns='" + namespace + "'><localRepository>/fixture/local</localRepository><servers>" +
      "<server><id>fixture</id><username>" + username + "</username><password>dummy-password</password></server></servers></settings>";
  }
}
