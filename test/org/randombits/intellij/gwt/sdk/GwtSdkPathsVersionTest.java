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
  private static final String GWT_VERSION = "2.12.2";

  public void testMavenSdkPathsResolveGwtProjectCoordinates() {
    assertGwtProjectCoordinates(new GwtMavenSdkPaths(
      "/m2/repository/org/gwtproject/gwt-dev/" + GWT_VERSION, GWT_VERSION));
  }

  public void testGradleSdkPathsResolveGwtProjectCoordinates() {
    assertGwtProjectCoordinates(new GwtGradleSdkPaths(
      "/gradle/caches/modules-2/files-2.1/org.gwtproject/gwt-dev/" + GWT_VERSION, GWT_VERSION));
  }

  private void assertGwtProjectCoordinates(GwtSdkPaths paths) {
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
        assertTrue(hasCoordinate(alternatives, GwtSdkPaths.NEW_GROUP_ID, artifactId));
        assertTrue(hasCoordinate(alternatives, GwtSdkPaths.OLD_GROUP_ID, artifactId));
        resolvedArtifactIds.add(artifactId);
        return List.of("resolved-from-org.gwtproject");
      }
    };

    assertEquals(List.of("resolved-from-org.gwtproject"), paths.getGwtUserDependencies(resolver));
    assertEquals(List.of("resolved-from-org.gwtproject"), paths.getGwtDevDependencies(resolver));
    assertEquals(Set.of(GwtSdkPaths.GWT_USER_ARTIFACT_ID, GwtSdkPaths.GWT_DEV_ARTIFACT_ID), resolvedArtifactIds);
  }

  private static boolean hasCoordinate(Collection<MavenCoordinates> coordinates, String groupId, String artifactId) {
    return coordinates.stream().anyMatch(coordinate -> groupId.equals(coordinate.groupId)
                                                        && artifactId.equals(coordinate.artifactId)
                                                        && GWT_VERSION.equals(coordinate.version));
  }
}
