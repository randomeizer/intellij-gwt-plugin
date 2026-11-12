package org.randombits.intellij.gwt.maven;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.idea.maven.model.MavenArtifact;

import java.io.File;
import java.util.List;

public class GwtMavenSourceArtifactsTest extends LightPlatformTestCase {
  private static final String POM = "file:///project/pom.xml";
  private static final String REQUEST = "test:library:1.0-SNAPSHOT";
  private File myRepository;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRepository = FileUtil.createTempDirectory("gwt-maven-sources", "");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FileUtil.delete(myRepository);
    }
    finally {
      super.tearDown();
    }
  }

  public void testPersistentRoundTripPreservesSourceIdentity() throws Exception {
    MavenArtifact source = artifact("sources", true);
    GwtMavenSourceArtifacts original = new GwtMavenSourceArtifacts();
    original.replace(POM, List.of(GwtMavenSourceArtifacts.SourceArtifact.from(POM, REQUEST, source, myRepository)));

    GwtMavenSourceArtifacts restored = restored(original);
    List<MavenArtifact> artifacts = restored.getArtifacts(POM, List.of(REQUEST));
    assertEquals(1, artifacts.size());
    MavenArtifact actual = artifacts.get(0);
    assertEquals(source, actual);
    assertEquals(source.getLibraryName(), actual.getLibraryName());
    assertEquals("1.0-SNAPSHOT", actual.getBaseVersion());
    assertEquals("sources", actual.getClassifier());
    assertTrue(actual.isOptional());
    assertTrue(actual.isResolved());
  }

  public void testRemovedOrChangedSourceRequestCannotReuseCachedArtifacts() throws Exception {
    GwtMavenSourceArtifacts storage = new GwtMavenSourceArtifacts();
    storage.replace(POM, List.of(GwtMavenSourceArtifacts.SourceArtifact.from(POM, REQUEST, artifact("sources", true), myRepository)));
    assertEmpty(storage.getArtifacts(POM, List.of()));
    assertEmpty(storage.getArtifacts(POM, List.of("test:library:2.0")));
  }

  public void testReplacingOnePomDoesNotClearAnotherPom() throws Exception {
    GwtMavenSourceArtifacts storage = new GwtMavenSourceArtifacts();
    MavenArtifact source = artifact("sources", true);
    String secondPom = "file:///other/pom.xml";
    storage.replace(POM, List.of(GwtMavenSourceArtifacts.SourceArtifact.from(POM, REQUEST, source, myRepository)));
    storage.replace(secondPom, List.of(GwtMavenSourceArtifacts.SourceArtifact.from(secondPom, REQUEST, source, myRepository)));
    storage.replace(POM, List.of());
    assertEmpty(storage.getArtifacts(POM, List.of(REQUEST)));
    assertEquals(List.of(source), storage.getArtifacts(secondPom, List.of(REQUEST)));
  }

  public void testMissingSourceJarRemainsAnUnresolvedClasspathArtifact() throws Exception {
    MavenArtifact source = artifact("sources", false);
    GwtMavenSourceArtifacts storage = new GwtMavenSourceArtifacts();
    storage.replace(POM, List.of(GwtMavenSourceArtifacts.SourceArtifact.from(POM, REQUEST, source, myRepository)));
    MavenArtifact actual = restored(storage).getArtifacts(POM, List.of(REQUEST)).get(0);
    assertEquals(source.getFile(), actual.getFile());
    assertEquals(source.getLibraryName(), actual.getLibraryName());
    assertFalse(actual.isResolved());
  }

  public void testSourceSelectorsIgnoreUnknownAndDuplicateCoordinates() throws Exception {
    MavenArtifact dependency = artifact(null, true);
    Element config = new Element("compileSourcesArtifacts")
      .addContent(new Element("artifact").setText("test:library"))
      .addContent(new Element("artifact").setText("test:library:ignored-version"))
      .addContent(new Element("artifact").setText("unknown:dependency"))
      .addContent(new Element("artifact").setText("invalid"));
    assertEquals(List.of(dependency), GwtCodehausFacetImporter.sourceDependencies(List.of(dependency), config));
  }

  public void testAbsentSourceConfigurationHasNoSupplementalDependencies() throws Exception {
    assertEmpty(GwtCodehausFacetImporter.sourceDependencies(List.of(artifact(null, true)), null));
  }

  private MavenArtifact artifact(String classifier, boolean exists) throws Exception {
    File file = new File(myRepository, "library-1.0-20261112.101010-1" + (classifier == null ? "" : "-" + classifier) + ".jar");
    if (exists) FileUtil.writeToFile(file, "fixture");
    return new MavenArtifact("test", "library", "1.0-20261112.101010-1", "1.0-SNAPSHOT", "jar", classifier,
                             "compile", true, "jar", file, myRepository, true, false);
  }

  private static GwtMavenSourceArtifacts restored(GwtMavenSourceArtifacts storage) {
    GwtMavenSourceArtifacts restored = new GwtMavenSourceArtifacts();
    restored.loadState(XmlSerializer.deserialize(XmlSerializer.serialize(storage.getState()),
                                               GwtMavenSourceArtifacts.ArtifactsState.class));
    return restored;
  }
}
