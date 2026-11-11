package org.randombits.intellij.gwt.maven

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import org.jetbrains.idea.maven.model.MavenArtifact
import org.jetbrains.idea.maven.model.MavenArtifactInfo
import org.jetbrains.idea.maven.model.MavenExplicitProfiles
import org.jetbrains.idea.maven.model.MavenRemoteRepository
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper
import org.randombits.intellij.gwt.jps.model.GwtSdkPaths

class GwtFacetImporterUtil {
  companion object {
    @JvmStatic
    fun resolveArtifactTransitively(artifactId: String,
                                    gwtVersion: String,
                                    classifier: String?,
                                    embedder: MavenEmbedderWrapper,
                                    repos: List<MavenRemoteRepository>): Collection<MavenArtifact> {
      val oldGroupArtifact = MavenArtifactInfo(GwtSdkPaths.OLD_GROUP_ID, artifactId, gwtVersion, "jar", classifier)
      val newGroupArtifact = MavenArtifactInfo(GwtSdkPaths.NEW_GROUP_ID, artifactId, gwtVersion, "jar", classifier)
      return runBlockingMaybeCancellable {
        embedder.resolveProcessorPathEntries(
          listOf(oldGroupArtifact, newGroupArtifact),
          repos,
          emptyMap(),
          MavenExplicitProfiles(emptyList(), emptyList())
        ).mavenResolvedArtifacts
      }
    }
  }
}
