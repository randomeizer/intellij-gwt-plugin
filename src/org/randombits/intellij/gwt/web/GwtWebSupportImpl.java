package org.randombits.intellij.gwt.web;

import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.javaee.DeploymentDescriptorsConstants;
import com.intellij.javaee.web.facet.WebFacetConfiguration;
import com.intellij.util.descriptors.ConfigFileFactory;
import com.intellij.util.descriptors.ConfigFileMetaData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetModel;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.javaee.web.WebDirectoryElement;
import com.intellij.javaee.web.WebRoot;
import com.intellij.javaee.web.WebUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.web.model.xml.WebApp;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.css.StylesheetFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.Nullable;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.facet.GwtFacetArtifactValidator;
import org.randombits.intellij.gwt.facet.GwtFacetEditor;
import org.randombits.intellij.gwt.module.model.GwtModule;

import javax.swing.Icon;
import java.util.List;

/** Loaded only by gwt-web-support.xml, never by the base plugin in free mode. */
public final class GwtWebSupportImpl implements GwtWebSupport {
  public static @Nullable WebFacet getWebFacet(GwtFacet facet) {
    String name = facet.getConfiguration().getWebFacetName();
    return name == null ? null : FacetManager.getInstance(facet.getModule()).findFacet(WebFacet.ID, name);
  }

  public static WebFacet createWebFacet(@NotNull GwtFacet facet, @NotNull VirtualFile root) {
    Project project = facet.getModule().getProject();
    final ConfigFileMetaData webXml = DeploymentDescriptorsConstants.WEB_XML_META_DATA;
    final @NonNls String webRootUrl = root.getUrl() + "/war";
    final String webXmlUrl = webRootUrl + "/" + webXml.getDirectoryPath() + "/" + webXml.getFileName();
    final FacetType<WebFacet, WebFacetConfiguration> webFacetType = FacetTypeRegistry.getInstance().findFacetType(WebFacet.ID);
    final WebFacet webFacet = FacetManager.getInstance(facet.getModule()).addFacet(webFacetType, webFacetType.getDefaultFacetName(), null);
    webFacet.addWebRoot(webRootUrl, "/");
    ConfigFileFactory.getInstance().createFile(project, webXmlUrl, webXml.getDefaultVersion(), false);
    webFacet.getDescriptorsContainer().getConfiguration().replaceConfigFile(webXml, webXmlUrl);
    facet.getConfiguration().setWebFacetName(webFacet.getName());
    return webFacet;
  }

  @Override
  public List<? extends Facet<?>> getWebFacets(FacetEditorContext context) {
    return List.copyOf(context.getFacetsProvider().getFacetsByType(context.getModule(), WebFacet.ID));
  }

  @Override
  public @Nullable Icon getWebFacetIcon(Module module, String name) {
    WebFacet facet = FacetManager.getInstance(module).findFacet(WebFacet.ID, name);
    return facet == null ? null : facet.getType().getIcon();
  }

  @Override
  public FacetEditorValidator createArtifactValidator(GwtFacet facet, FacetEditorContext context, GwtFacetEditor editor) {
    return new GwtFacetArtifactValidator(facet, context, editor);
  }

  @Override
  public List<String> getWebRootUrls(GwtFacet facet) {
    return getWebRootUrls(getWebFacet(facet));
  }

  private static List<String> getWebRootUrls(@Nullable WebFacet facet) {
    return facet == null ? List.of() : facet.getWebRoots().stream().map(WebRoot::getDirectoryUrl).toList();
  }

  @Override
  public List<String> associateWebFacet(GwtFacet facet, FacetModel model) {
    WebFacet web = model.getFacetByType(WebFacet.ID);
    if (web != null) facet.getConfiguration().setWebFacetName(web.getName());
    return getWebRootUrls(web);
  }

  @Override
  public boolean isUnderWebRoot(GwtFacet facet, VirtualFile directory) {
    WebFacet web = getWebFacet(facet);
    return web != null && WebUtil.findParentWebRoot(directory, web.getWebRoots()) != null;
  }

  @Override
  public @Nullable String getRelativeToWebRootPath(VirtualFile file, Project project) {
    WebFacet facet = WebUtil.findFacetByFileUnderWebRoot(file, project);
    if (facet == null) return null;
    WebRoot root = WebUtil.findParentWebRoot(file, facet.getWebRoots());
    VirtualFile rootFile = root == null ? null : root.getFile();
    return rootFile == null ? null : VfsUtilCore.getRelativePath(file, rootFile, '/');
  }

  @Override
  public @Nullable String getParentPathFromWebRoot(VirtualFile file, Project project) {
    WebDirectoryElement directory = WebUtil.findWebDirectoryByFile(file, project);
    WebDirectoryElement parent = directory == null ? null : directory.getParent();
    return parent == null ? null : parent.getPath();
  }

  @Override
  public @Nullable StylesheetFile findCssFile(GwtFacet facet, String path) {
    WebFacet web = getWebFacet(facet);
    WebDirectoryElement directory = web == null ? null : WebUtil.getWebUtil().findWebDirectoryElement(path, web);
    return directory != null && directory.getOriginalFile() instanceof StylesheetFile css ? css : null;
  }

  @Override
  public @Nullable PsiFile getWebXmlFile(XmlFile moduleFile) {
    WebFacet facet = WebUtil.getWebFacet(moduleFile);
    WebApp root = facet == null ? null : facet.getRoot();
    return root == null ? null : DomUtil.getFile(root);
  }

  @Override
  public void registerServlet(GwtFacet facet, GwtModule module, XmlFile moduleFile, PsiClass servlet, String name) {
    WebFacet web = WebUtil.getWebFacet(moduleFile);
    WebApp root = web == null ? null : web.getRoot();
    if (root != null) GwtWebServletUtil.registerServletForService(facet, module, root, servlet, name);
  }

  @Override
  public @Nullable XmlTag findServletTag(GwtModule module, String path) {
    var servlet = GwtWebServletUtil.findServletByPath(module, path);
    return servlet == null ? null : servlet.getXmlTag();
  }

  @Override
  public List<String> getServletPaths(GwtModule module) {
    return GwtWebServletUtil.getAllGwtServletsPaths(module);
  }
}
