package org.randombits.intellij.gwt.jps.model;

import org.jetbrains.jps.model.JpsElementChildRole;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.JpsSimpleElement;
import org.jetbrains.jps.model.ex.JpsElementChildRoleBase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/** Versioned, per-build repository identities/URLs; authentication stays in the build process. */
public final class GwtRepositoryConfiguration {
  public static final String BUILDER_PARAMETER = "org.randombits.gwt.repositories.v1";
  private static final int MAX_REPOSITORIES = 256;
  private static final int MAX_BYTES = 1024 * 1024;
  private static final JpsElementChildRole<JpsSimpleElement<List<Repository>>> ROLE =
    JpsElementChildRoleBase.create("GWT repositories");

  private GwtRepositoryConfiguration() {}

  public static void set(JpsProject project, List<Repository> repositories) {
    project.getContainer().setChild(ROLE, JpsElementFactory.getInstance().createSimpleElement(List.copyOf(repositories)));
  }

  public static List<Repository> get(JpsProject project) {
    var element = project.getContainer().getChild(ROLE);
    return element != null ? element.getData() : defaults();
  }

  public static List<Repository> defaults() {
    // Match the native IDEA 263 JPS repository defaults.
    return List.of(new Repository("central", "https://repo1.maven.org/maven2"),
                   new Repository("jboss.community", "https://repository.jboss.org/nexus/content/repositories/public/"));
  }

  public static String encode(List<Repository> repositories) {
    if (repositories.size() > MAX_REPOSITORIES) throw invalid();
    try {
      var bytes = new ByteArrayOutputStream();
      var output = new DataOutputStream(bytes);
      output.writeInt(1);
      output.writeInt(repositories.size());
      for (var repository : repositories) {
        output.writeUTF(repository.id);
        output.writeUTF(repository.url);
      }
      if (bytes.size() > MAX_BYTES) throw invalid();
      return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    catch (IOException e) {
      throw invalid();
    }
  }

  public static List<Repository> decode(String encoded) {
    if (encoded.length() > (MAX_BYTES + 2) / 3 * 4) throw invalid();
    try {
      var input = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)));
      if (input.readInt() != 1) throw invalid();
      int count = input.readInt();
      if (count < 0 || count > MAX_REPOSITORIES) throw invalid();
      List<Repository> result = new ArrayList<>(count);
      for (int i = 0; i < count; i++) result.add(new Repository(input.readUTF(), input.readUTF()));
      if (input.available() != 0) throw invalid();
      return List.copyOf(result);
    }
    catch (IOException | IllegalArgumentException e) {
      throw invalid();
    }
  }

  private static IllegalArgumentException invalid() {
    // Do not echo the serialized payload or repository URLs into diagnostics.
    return new IllegalArgumentException("Invalid or unsupported GWT repository configuration; rebuild from IDEA or refresh the standalone configuration");
  }

  public static final class Repository {
    public final String id;
    public final String url;

    public Repository(String id, String url) {
      this.id = Objects.requireNonNull(id);
      this.url = Objects.requireNonNull(url);
      if (id.length() > 1024 || url.length() > 16384) throw invalid();
    }
  }
}
