package org.randombits.intellij.gwt.maven;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifact;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/** Keeps resolved source-library data available to cached Maven imports after reopening a project. */
@Service(Service.Level.PROJECT)
@State(name = "RandombitsGwtMavenSourceArtifacts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class GwtMavenSourceArtifacts implements PersistentStateComponent<GwtMavenSourceArtifacts.ArtifactsState> {
  private volatile ArtifactsState myState = new ArtifactsState();

  public static GwtMavenSourceArtifacts getInstance(Project project) {
    return project.getService(GwtMavenSourceArtifacts.class);
  }

  @Override
  public @NotNull ArtifactsState getState() {
    return myState;
  }

  @Override
  public synchronized void loadState(@NotNull ArtifactsState state) {
    ArtifactsState copy = new ArtifactsState();
    copy.artifacts.addAll(state.artifacts);
    myState = copy;
  }

  synchronized void replace(String pomUrl, List<SourceArtifact> artifacts) {
    ArtifactsState copy = new ArtifactsState();
    for (SourceArtifact artifact : myState.artifacts) {
      if (!pomUrl.equals(artifact.pomUrl)) copy.artifacts.add(artifact);
    }
    copy.artifacts.addAll(artifacts);
    myState = copy;
  }

  List<MavenArtifact> getArtifacts(String pomUrl, Collection<String> requestedCoordinates) {
    LinkedHashSet<MavenArtifact> result = new LinkedHashSet<>();
    for (SourceArtifact artifact : myState.artifacts) {
      if (pomUrl.equals(artifact.pomUrl) && requestedCoordinates.contains(artifact.requestedCoordinates)
          && artifact.file != null && artifact.localRepository != null) {
        result.add(artifact.toArtifact());
      }
    }
    return new ArrayList<>(result);
  }

  static String coordinates(MavenArtifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
  }

  public static final class ArtifactsState {
    public List<SourceArtifact> artifacts = new ArrayList<>();
  }

  public static final class SourceArtifact {
    public String pomUrl;
    public String requestedCoordinates;
    public String groupId;
    public String artifactId;
    public String version;
    public String baseVersion;
    public String type;
    public String classifier;
    public String scope;
    public boolean optional;
    public String extension;
    public String file;
    public String localRepository;
    public boolean resolved;

    static SourceArtifact from(String pomUrl, String requestedCoordinates, MavenArtifact artifact, File repository) {
      SourceArtifact state = new SourceArtifact();
      state.pomUrl = pomUrl;
      state.requestedCoordinates = requestedCoordinates;
      state.groupId = artifact.getGroupId();
      state.artifactId = artifact.getArtifactId();
      state.version = artifact.getVersion();
      state.baseVersion = artifact.getBaseVersion();
      state.type = artifact.getType();
      state.classifier = artifact.getClassifier();
      state.scope = artifact.getScope();
      state.optional = artifact.isOptional();
      state.extension = artifact.getExtension();
      state.file = artifact.getFile().getAbsolutePath();
      state.localRepository = repository.getAbsolutePath();
      // Preserve effective resolution, including missing/stubbed artifacts, without private state access.
      state.resolved = artifact.isResolved();
      return state;
    }

    MavenArtifact toArtifact() {
      return new MavenArtifact(groupId, artifactId, version, baseVersion, type, classifier, scope, optional,
                               extension, new File(file), new File(localRepository), resolved, false);
    }
  }
}
