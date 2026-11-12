package org.randombits.intellij.gwt.build;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VfsUtilCore;

import java.nio.file.Path;
import java.util.LinkedHashSet;

/** Excludes the model-known generated output, including custom Java output locations. */
public final class GwtCompilerOutputExclusionPolicy implements DirectoryIndexExcludePolicy {
  private final Project myProject;

  public GwtCompilerOutputExclusionPolicy(Project project) {
    myProject = project;
  }

  @Override
  public String[] getExcludeUrlsForProject() {
    var urls = new LinkedHashSet<String>();
    for (var module : ModuleManager.getInstance(myProject).getModules()) {
      // Reserve the container independently of facet changes, including leftovers after a facet is removed.
      var extension = CompilerModuleExtension.getInstance(module);
      String url = extension == null ? null : extension.getCompilerOutputUrl();
      if (url == null || !url.startsWith("file://")) continue;
      Path parent = Path.of(VfsUtilCore.urlToPath(url)).toAbsolutePath().normalize().getParent();
      if (parent != null) urls.add(VfsUtilCore.pathToUrl(parent.resolve(".intellij-gwt").toString()));
    }
    return urls.toArray(String[]::new);
  }
}
