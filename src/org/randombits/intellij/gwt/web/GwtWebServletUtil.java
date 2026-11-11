package org.randombits.intellij.gwt.web;

import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.module.model.GwtModule;
import com.intellij.javaee.web.CommonServlet;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.web.model.xml.Servlet;
import com.intellij.javaee.web.model.xml.ServletMapping;
import com.intellij.javaee.web.model.xml.WebApp;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.randombits.intellij.gwt.rpc.RemoteServiceUtil;
import static org.randombits.intellij.gwt.rpc.GwtServletUtil.*;

public final class GwtWebServletUtil {
  private GwtWebServletUtil() {}

  public static void registerServletForService(GwtFacet gwtFacet, final GwtModule gwtModule, final WebApp root, final PsiClass servletImpl,
                                         final String serviceName) {
    registerServletForService(gwtModule, root, servletImpl, serviceName,
                              getServletUrlPattern(gwtFacet, gwtModule, serviceName, servletImpl));
  }

  public static void registerServletForService(final GwtModule gwtModule, final @NotNull WebApp root, final PsiClass servletImpl, final String serviceName,
                                               final String servletUrlPattern) {
    final Servlet servlet = root.addServlet();
    servlet.getServletClass().setValue(servletImpl);
    final String servletName = gwtModule.getQualifiedName() + " " + serviceName;
    servlet.getServletName().setValue(servletName);

    addServletMapping(root, servlet, servletUrlPattern);
  }

  public static void addServletMapping(final WebApp root, final CommonServlet servlet, final String servletUrlPattern) {
    final ServletMapping mapping = root.addServletMapping();
    mapping.getServletName().setValue(servlet);
    mapping.addUrlPattern().setValue(servletUrlPattern);
  }

  public static @Nullable CommonServlet findServletByPath(GwtModule gwtModule, String relativePath) {
    final GwtFacet facet = GwtFacet.getInstance(gwtModule);
    if (facet == null) return null;

    final WebFacet webFacet = GwtWebSupportImpl.getWebFacet(facet);
    if (webFacet == null) return null;

    final WebApp root = webFacet.getRoot();
    if (root == null) return null;

    final String urlPattern = getServletUrlPattern(facet, gwtModule, relativePath);
    for (ServletMapping mapping : root.getServletMappings()) {
      final List<GenericDomValue<String>> urlPatterns = mapping.getUrlPatterns();
      for (GenericDomValue<String> pattern : urlPatterns) {
        if (urlPattern.equals(pattern.getStringValue())) {
          return mapping.getServletName().getValue();
        }
      }
    }
    return null;
  }

  public static @NotNull List<String> getAllGwtServletsPaths(@NotNull GwtModule gwtModule) {
    final GwtFacet facet = GwtFacet.getInstance(gwtModule);
    if (facet == null) return Collections.emptyList();

    final WebFacet webFacet = GwtWebSupportImpl.getWebFacet(facet);
    if (webFacet == null) return Collections.emptyList();

    final WebApp root = webFacet.getRoot();
    if (root == null) return Collections.emptyList();

    final String rootPath = facet.getConfiguration().getPackagingRelativePath(gwtModule) + "/";
    List<String> paths = new ArrayList<>();
    for (ServletMapping servletMapping : root.getServletMappings()) {
      final CommonServlet servlet = servletMapping.getServletName().getValue();
      if (servlet != null && RemoteServiceUtil.isRemoteServiceImplementation(servlet.getPsiClass())) {
        for (GenericDomValue<String> pattern : servletMapping.getUrlPatterns()) {
          final String value = pattern.getValue();
          if (value != null && value.startsWith(rootPath)) {
            paths.add(value.substring(rootPath.length()));
          }
        }
      }
    }
    return paths;
  }

  public static boolean hasServlets(@NotNull GwtFacet facet) {
    final WebFacet webFacet = GwtWebSupportImpl.getWebFacet(facet);
    if (webFacet == null) return false;

    final WebApp root = webFacet.getRoot();
    if (root == null) return false;

    for (Servlet servlet : root.getServlets()) {
      if (RemoteServiceUtil.isRemoteServiceImplementation(servlet.getServletClass().getValue())) {
        return true;
      }
    }
    return false;
  }

  public static @Nullable Servlet findServlet(WebApp root, PsiClass servletImpl) {
    final List<Servlet> servlets = root.getServlets();
    final PsiManager psiManager = servletImpl.getManager();
    for (Servlet servlet : servlets) {
      if (psiManager.areElementsEquivalent(servletImpl, servlet.getServletClass().getValue())) {
        return servlet;
      }
    }
    return null;
  }
}
