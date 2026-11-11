package org.randombits.intellij.gwt.sdk;

import org.randombits.intellij.gwt.make.GwtCompilerPaths;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.randombits.intellij.gwt.jps.model.impl.sdk.JpsGwtDependenciesStorage;

@Service(Service.Level.PROJECT)
public final class GwtDependenciesStorage extends JpsGwtDependenciesStorage {
  public static GwtDependenciesStorage getInstance(@NotNull Project project) {
    return project.getService(GwtDependenciesStorage.class);
  }

  public GwtDependenciesStorage(Project project) {
    super(GwtCompilerPaths.getGwtDependencies(project));
  }
}
