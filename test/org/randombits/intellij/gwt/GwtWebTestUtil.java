package org.randombits.intellij.gwt;

import com.intellij.facet.impl.FacetUtil;
import com.intellij.javaee.DeploymentDescriptorsConstants;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.web.facet.WebFacetType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtilCore;
import org.randombits.intellij.gwt.facet.GwtFacet;

/** Test fixtures loaded only when the optional Web implementation is enabled. */
public final class GwtWebTestUtil {
  private GwtWebTestUtil() {}

  public static WebFacet addWebFacet(final GwtFacet gwtFacet, final String testDataPath, final String webXmlPath) {
    return WriteAction.computeAndWait(() -> {
      WebFacet webFacet = FacetUtil.addFacet(gwtFacet.getModule(), WebFacetType.getInstance());
      final String webXmlUrl = VfsUtilCore.pathToUrl(testDataPath) + "/" + webXmlPath;
      webFacet.getDescriptorsContainer().getConfiguration().addConfigFile(DeploymentDescriptorsConstants.WEB_XML_META_DATA, webXmlUrl);
      gwtFacet.getConfiguration().setWebFacetName(webFacet.getName());
      return webFacet;
    });
  }

}
