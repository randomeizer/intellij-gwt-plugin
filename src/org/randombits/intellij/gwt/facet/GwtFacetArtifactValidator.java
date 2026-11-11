package org.randombits.intellij.gwt.facet;

import org.randombits.intellij.gwt.web.GwtWebSupport;
import com.intellij.facet.ui.FacetConfigurationQuickFix;
import com.intellij.facet.ui.FacetEditorValidator;
import com.intellij.facet.ui.ValidationResult;
import org.randombits.intellij.gwt.GwtBundle;
import org.randombits.intellij.gwt.packaging.GwtCompileOutputRelativePathSuggester;
import org.randombits.intellij.gwt.packaging.GwtCompilerOutputElement;
import org.randombits.intellij.gwt.packaging.GwtCompilerOutputElementType;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactEditorEx;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurableContext;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.ui.popup.util.BaseListPopupStep;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class GwtFacetArtifactValidator extends FacetEditorValidator {
  private final GwtFacet myFacet;
  private final Supplier<ArtifactsStructureConfigurableContext> myArtifactsContext;
  private final Supplier<String> mySelectedWebFacet;

  public GwtFacetArtifactValidator(GwtFacet facet, Supplier<ArtifactsStructureConfigurableContext> artifactsContext,
                                   Supplier<String> selectedWebFacet) {
    myFacet = facet;
    myArtifactsContext = artifactsContext;
    mySelectedWebFacet = selectedWebFacet;
  }

  @Override
  public @NotNull ValidationResult check() {
    final ArtifactsStructureConfigurableContext artifactsContext = myArtifactsContext.get();
    for (Artifact artifact : artifactsContext.getArtifactModel().getArtifacts()) {
      if (!ArtifactUtil.processPackagingElements(artifact, GwtCompilerOutputElementType.getInstance(),
                                                 element -> !myFacet.equals(element.getFacet()), artifactsContext, false)) {
        GwtWebSupport webSupport = GwtWebSupport.getInstance();
        return webSupport == null ? ValidationResult.OK : webSupport.checkArtifact(myFacet, artifact, artifactsContext);
      }
    }

    final List<? extends Artifact> artifacts = new ArrayList<Artifact>(getSuitableArtifacts(artifactsContext));
    artifacts.sort(ArtifactManager.ARTIFACT_COMPARATOR);

    final FacetConfigurationQuickFix fix;
    if (artifacts.isEmpty()) {
      fix = new FacetConfigurationQuickFix(GwtBundle.message("quickfix.text.create.artifact")) {
        @Override
        public void run(JComponent place) {
          createArtifact(artifactsContext);
        }
      };
    }
    else {
      fix = new FacetConfigurationQuickFix() {
        @Override
        public void run(JComponent place) {
          final String createItem = GwtBundle.message("quickfix.text.create.artifact");
          final String includeItem = artifacts.size() == 1 ? GwtBundle.message("quickfix.text.include.into.0", artifacts.get(0).getName())
                                                           : GwtBundle.message("quickfix.text.include.in.artifact");
          JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null, createItem, includeItem) {
            @Override
            public boolean hasSubstep(String selectedValue) {
              return includeItem.equals(selectedValue) && artifacts.size() > 1;
            }

            @Override
            public PopupStep<?> onChosen(String selectedValue, boolean finalChoice) {
              if (selectedValue.equals(createItem)) {
                createArtifact(artifactsContext);
              }
              else if (artifacts.size() == 1) {
                includeInArtifact(artifacts.get(0), artifactsContext);
              }
              else {
                return new MySelectArtifactPopupStep(artifacts, artifactsContext);
              }
              return FINAL_CHOICE;
            }
          }).showUnderneathOf(place);
        }
      };
    }

    return new ValidationResult(GwtBundle.message("validation.result.gwt.compiler.output.is.not.included.in.an.artifact"), fix);
  }

  private void createArtifact(ArtifactsStructureConfigurableContext artifactsContext) {
    final String artifactName = suggestArtifactName(myFacet.getModule().getName());
    final ModifiableArtifact artifact = artifactsContext.getOrCreateModifiableArtifactModel().addArtifact(artifactName, PlainArtifactType.getInstance());
    includeInArtifact(artifact, artifactsContext);
  }

  public static @NonNls String suggestArtifactName(final String moduleName) {
    return moduleName + ":GWT";
  }

  private void includeInArtifact(final Artifact artifact, final ArtifactsStructureConfigurableContext artifactsContext) {
    artifactsContext.editLayout(artifact, () -> {
      GwtCompilerOutputElement element = new GwtCompilerOutputElement(artifactsContext.getProject(), myFacet);
      String path = GwtCompileOutputRelativePathSuggester.suggestRelativeOutputPath(myFacet, artifactsContext);
      artifactsContext.getRootElement(artifact).addOrFindChild(PackagingElementFactory.getInstance().createParentDirectories(path, element));
    });
    ((ArtifactEditorEx)artifactsContext.getOrCreateEditor(artifact)).rebuildTries();
    ProjectStructureConfigurable.getInstance(artifactsContext.getProject()).select(artifact, true);
  }

  private Collection<? extends Artifact> getSuitableArtifacts(ArtifactsStructureConfigurableContext context) {
    GwtWebSupport webSupport = GwtWebSupport.getInstance();
    if (webSupport != null) {
      Collection<? extends Artifact> webArtifacts = webSupport.getSuitableArtifacts(myFacet, mySelectedWebFacet.get(), context);
      if (!webArtifacts.isEmpty()) return webArtifacts;
    }
    return context.getArtifactModel().getArtifactsByType(PlainArtifactType.getInstance());
  }

  private class MySelectArtifactPopupStep extends BaseListPopupStep<Artifact> {
    private final ArtifactsStructureConfigurableContext myArtifactsContext;

    MySelectArtifactPopupStep(List<? extends Artifact> artifacts, ArtifactsStructureConfigurableContext artifactsContext) {
      super(GwtBundle.message("popup.step.title.select.artifact"), artifacts);
      myArtifactsContext = artifactsContext;
    }

    @Override
    public @NotNull String getTextFor(Artifact value) {
      return value.getName();
    }

    @Override
    public Icon getIconFor(Artifact aValue) {
      return aValue.getArtifactType().getIcon();
    }

    @Override
    public PopupStep<?> onChosen(Artifact selectedValue, boolean finalChoice) {
      includeInArtifact(selectedValue, myArtifactsContext);
      return FINAL_CHOICE;
    }
  }

}
