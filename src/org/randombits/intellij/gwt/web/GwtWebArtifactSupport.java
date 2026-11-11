package org.randombits.intellij.gwt.web;

import org.randombits.intellij.gwt.facet.GwtFacet;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.ValidationResult;
import org.randombits.intellij.gwt.GwtBundle;
import org.randombits.intellij.gwt.sdk.GwtSdk;
import com.intellij.javaee.artifact.JavaeeArtifactUtil;
import com.intellij.javaee.web.artifact.WebArtifactUtil;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactType;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PackagingElementPath;
import com.intellij.packaging.impl.artifacts.PackagingElementProcessor;
import com.intellij.packaging.impl.elements.FileCopyPackagingElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.io.File;
import java.util.Collection;

public final class GwtWebArtifactSupport {
  private GwtWebArtifactSupport() {}

  public static ValidationResult checkArtifact(GwtFacet facet, Artifact artifact, ArtifactsStructureConfigurableContext context) {
    ArtifactType type = artifact.getArtifactType();
    if (WebArtifactUtil.getInstance().isWebApplication(type) && !containsServletJar(facet, context, artifact, type)
        && GwtWebServletUtil.hasServlets(facet)) {
      return new ValidationResult(
        GwtBundle.message("validation.result.gwt.servlet.jar.is.not.included.in.0.artifact", artifact.getName()),
        new IncludeGwtServletJarQuickFix(facet, artifact, context));
    }
    return ValidationResult.OK;
  }

  private static boolean containsServletJar(GwtFacet facet, ArtifactsStructureConfigurableContext artifactsContext, Artifact artifact, ArtifactType artifactType) {
    final GwtSdk sdk = facet.getConfiguration().getSdk();
    if (!new File(sdk.getServletJarPath()).exists()) {
      return true;
    }

    final String servletJarPath = FileUtil.toSystemIndependentName(sdk.getServletJarPath());
    return !ArtifactUtil.processDirectoryChildren(artifact.getRootElement(), PackagingElementPath.EMPTY, "/WEB-INF/lib", artifactsContext,
                                          artifactType, new PackagingElementProcessor<>() {
        @Override
        public boolean process(@NotNull PackagingElement<?> element, @NotNull PackagingElementPath path) {
          if (element instanceof FileCopyPackagingElement &&
              FileUtil.pathsEqual(FileUtil.toSystemIndependentName(((FileCopyPackagingElement)element).getFilePath()), servletJarPath)) {
            return false;
          }
          return true;
        }
      });
  }

  public static Collection<? extends Artifact> getSuitableArtifacts(GwtFacet facet, String webFacetName, ArtifactsStructureConfigurableContext artifactsContext) {
    if (webFacetName != null) {
      final WebFacet webFacet = artifactsContext.getModulesProvider().getFacetModel(facet.getModule()).findFacet(WebFacet.ID, webFacetName);
      if (webFacet != null) {
        final Collection<Artifact> artifacts = JavaeeArtifactUtil.getInstance().getArtifactsContainingFacet(webFacet, artifactsContext,
                                                                                                            WebArtifactUtil.getInstance().getWebArtifactTypes(), false);
        if (!artifacts.isEmpty()) {
          return artifacts;
        }
      }
    }
    return artifactsContext.getArtifactModel().getArtifactsByType(WebArtifactUtil.getInstance().getExplodedWarArtifactType());
  }

  private static class IncludeGwtServletJarQuickFix extends FacetConfigurationQuickFix {
    private final GwtFacet myFacet;
    private final Artifact myArtifact;
    private final ArtifactsStructureConfigurableContext myArtifactsContext;

    IncludeGwtServletJarQuickFix(GwtFacet facet, Artifact artifact, ArtifactsStructureConfigurableContext artifactsContext) {
      myFacet = facet;
      myArtifact = artifact;
      myArtifactsContext = artifactsContext;
    }

    @Override
    public void run(JComponent place) {
      myArtifactsContext.editLayout(myArtifact, () -> {
        final CompositePackagingElement<?> root = myArtifactsContext.getRootElement(myArtifact);
        PackagingElementFactory.getInstance().addFileCopy(root, "WEB-INF/lib", FileUtil.toSystemIndependentName(myFacet.getConfiguration().getSdk().getServletJarPath()));
      });
      ((ArtifactEditorEx)myArtifactsContext.getOrCreateEditor(myArtifact)).rebuildTries();
    }
  }
}
