package org.randombits.intellij.gwt.clientBundle.css.language;

import com.intellij.facet.ProjectFacetManager;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.facet.GwtFacetType;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.LanguageSubstitutor;
import org.jetbrains.annotations.NotNull;

public final class GwtCssLanguageSubstitutor extends LanguageSubstitutor {
  @Override
  public Language getLanguage(@NotNull VirtualFile file, @NotNull Project project) {
    if (!ProjectFacetManager.getInstance(project).hasFacets(GwtFacetType.ID)) {
      return null;
    }
    if (GwtFacet.isInModuleWithGwtFacet(project, file) && ProjectRootManager.getInstance(project).getFileIndex().isInSourceContent(file)) {
      return GwtCssLanguage.GWT_CSS_LANGUAGE;
    }
    return null;
  }
}
