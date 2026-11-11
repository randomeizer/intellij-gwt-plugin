package org.randombits.intellij.gwt.web;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.css.StylesheetFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.facet.GwtFacetEditor;
import org.randombits.intellij.gwt.module.model.GwtModule;

import javax.swing.Icon;
import java.util.List;

/** Optional Web integration. Keep signatures limited to APIs available in free IDEA. */
public interface GwtWebSupport {
  static @Nullable GwtWebSupport getInstance() {
    return ApplicationManager.getApplication().getService(GwtWebSupport.class);
  }

  List<? extends Facet<?>> getWebFacets(FacetEditorContext context);
  @Nullable Icon getWebFacetIcon(Module module, String name);
  FacetEditorValidator createArtifactValidator(GwtFacet facet, FacetEditorContext context, GwtFacetEditor editor);
  List<String> getWebRootUrls(GwtFacet facet);
  List<String> associateWebFacet(GwtFacet facet, FacetModel model);
  boolean isUnderWebRoot(GwtFacet facet, VirtualFile directory);
  @Nullable String getRelativeToWebRootPath(VirtualFile file, Project project);
  @Nullable String getParentPathFromWebRoot(VirtualFile file, Project project);
  @Nullable StylesheetFile findCssFile(GwtFacet facet, String path);
  @Nullable PsiFile getWebXmlFile(XmlFile moduleFile);
  void registerServlet(GwtFacet facet, GwtModule module, XmlFile moduleFile, PsiClass servlet, String name);
  @Nullable XmlTag findServletTag(GwtModule module, String path);
  List<String> getServletPaths(GwtModule module);
}
