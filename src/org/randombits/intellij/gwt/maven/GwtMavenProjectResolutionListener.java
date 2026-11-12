package org.randombits.intellij.gwt.maven;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenEmbeddersManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.MavenEmbedderWrapper;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;

import java.util.List;

/** Resolves plugin dependencies synchronously before Maven starts importing the resolved models. */
public final class GwtMavenProjectResolutionListener implements MavenProjectsTree.Listener {
  private final Project myProject;

  public GwtMavenProjectResolutionListener(Project project) {
    myProject = project;
  }

  @Override
  public void projectsResolved(@NotNull List<MavenProject> projects) {
    if (myProject.isDisposed()) return;
    GwtCodehausFacetImporter importer = new GwtCodehausFacetImporter();
    for (MavenProject project : projects) {
      if (!importer.isApplicable(project)) {
        GwtMavenSourceArtifacts.getInstance(myProject).replace(project.getFile().getUrl(), List.of());
        continue;
      }
      MavenEmbeddersManager manager = MavenProjectsManager.getInstance(myProject).getEmbeddersManager();
      // This public acquisition API is retained for third-party plugins, but marked Obsolete by the SDK.
      MavenEmbedderWrapper embedder = manager.getEmbedder(project, MavenEmbeddersManager.FOR_DEPENDENCIES_RESOLVE);
      try {
        importer.resolve(myProject, project, embedder);
      }
      catch (MavenProcessCanceledException exception) {
        throw new ProcessCanceledException(exception);
      }
      finally {
        manager.release(embedder);
      }
    }
  }
}
