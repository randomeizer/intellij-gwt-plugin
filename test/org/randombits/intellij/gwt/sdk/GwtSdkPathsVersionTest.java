package org.randombits.intellij.gwt.sdk;

import junit.framework.TestCase;
import org.randombits.intellij.gwt.jps.model.GwtDependenciesResolver;
import org.randombits.intellij.gwt.jps.model.GwtSdkPaths;
import org.randombits.intellij.gwt.jps.model.MavenCoordinates;
import org.randombits.intellij.gwt.jps.model.impl.sdk.GwtGradleSdkPaths;
import org.randombits.intellij.gwt.jps.model.impl.sdk.GwtMavenSdkPaths;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GwtSdkPathsVersionTest extends TestCase {
  private static final List<String> GWT_VERSIONS = List.of("2.12.2", "2.13.0", "2.13.1");

  public void testMavenSdkPathsResolveGwtProjectCoordinates() {
    for (String version : GWT_VERSIONS) {
      assertGwtProjectCoordinates(new GwtMavenSdkPaths(
        "/m2/repository/org/gwtproject/gwt-dev/" + version, version), version);
    }
  }

  public void testGradleSdkPathsResolveGwtProjectCoordinates() {
    for (String version : GWT_VERSIONS) {
      assertGwtProjectCoordinates(new GwtGradleSdkPaths(
        "/gradle/caches/modules-2/files-2.1/org.gwtproject/gwt-dev/" + version, version), version);
    }
  }

  private void assertGwtProjectCoordinates(GwtSdkPaths paths, String version) {
    Set<String> resolvedArtifactIds = new HashSet<>();
    GwtDependenciesResolver resolver = new GwtDependenciesResolver() {
      @Override
      public List<String> resolveDependencies(String groupId, String artifactId, String version) {
        fail("Expected alternative coordinate lookup");
        return List.of();
      }

      @Override
      public List<String> resolveAnyOf(Collection<MavenCoordinates> alternatives) {
        String artifactId = alternatives.iterator().next().artifactId;
        assertTrue(hasCoordinate(alternatives, GwtSdkPaths.NEW_GROUP_ID, artifactId, version));
        assertTrue(hasCoordinate(alternatives, GwtSdkPaths.OLD_GROUP_ID, artifactId, version));
        resolvedArtifactIds.add(artifactId);
        return List.of("resolved-from-org.gwtproject");
      }
    };

    assertEquals(List.of("resolved-from-org.gwtproject"), paths.getGwtUserDependencies(resolver));
    assertEquals(List.of("resolved-from-org.gwtproject"), paths.getGwtDevDependencies(resolver));
    assertEquals(Set.of(GwtSdkPaths.GWT_USER_ARTIFACT_ID, GwtSdkPaths.GWT_DEV_ARTIFACT_ID), resolvedArtifactIds);
  }

  private static boolean hasCoordinate(Collection<MavenCoordinates> coordinates, String groupId, String artifactId, String version) {
    return coordinates.stream().anyMatch(coordinate -> groupId.equals(coordinate.groupId)
                                                        && artifactId.equals(coordinate.artifactId)
                                                        && version.equals(coordinate.version));
  }
}
