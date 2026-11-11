package org.randombits.intellij.gwt.rpc;

import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.module.model.GwtModule;
import org.randombits.intellij.gwt.module.model.GwtServlet;
import com.intellij.jam.model.util.JamCommonUtil;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class GwtServletUtil {
  private GwtServletUtil() {
  }

  public static @NotNull String getDefaultServletPath(GwtModule module, String serviceName) {
    return "/" + serviceName;
  }

  public static @NotNull String getServletPath(GwtModule gwtModule, String serviceName, PsiClass serviceImpl) {
    final PsiClass serviceInterface = RemoteServiceUtil.findRemoteServiceInterface(serviceImpl);
    if (serviceInterface != null) {
      final PsiModifierList modifierList = serviceInterface.getModifierList();
      if (modifierList != null) {
        final PsiAnnotation servicePath = modifierList.findAnnotation(RemoteServiceUtil.SERVICE_PATH_ANNOTATION_NAME);
        if (servicePath != null) {
          final PsiAnnotationMemberValue value = servicePath.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
          if (value != null) {
            final String path = JamCommonUtil.getObjectValue(value, String.class);
            if (path != null) {
              return path;
            }
          }
        }
      }
    }

    List<GwtServlet> list = gwtModule.getServlets();
    final String serviceImplName = serviceImpl.getQualifiedName();
    if (serviceImplName != null) {
      for (GwtServlet servlet : list) {
        if (serviceImplName.equals(servlet.getServletClass().getValue())) {
          String path = servlet.getPath().getValue();
          if (path != null) {
            if (!path.startsWith("/")) {
              path = "/" + path;
            }
            return path;
          }
        }
      }
    }

    return getDefaultServletPath(gwtModule, serviceName);
  }

  public static String getServletUrlPattern(GwtFacet gwtFacet, GwtModule module, String serviceName, PsiClass serviceImpl) {
    return getServletUrlPattern(gwtFacet, module, getServletPath(module, serviceName, serviceImpl));
  }

  public static String getServletUrlPattern(GwtFacet gwtFacet, GwtModule module, final String servletPath) {
    String base = gwtFacet.getConfiguration().getPackagingRelativePath(module);
    return DeploymentUtil.concatPaths(base, servletPath);
  }

}
