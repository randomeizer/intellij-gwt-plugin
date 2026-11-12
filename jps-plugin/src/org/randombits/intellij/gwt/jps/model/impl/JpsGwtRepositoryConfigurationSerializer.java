package org.randombits.intellij.gwt.jps.model.impl;

import org.jdom.Element;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.serialization.JpsProjectExtensionSerializer;
import org.randombits.intellij.gwt.jps.model.GwtRepositoryConfiguration;

import java.util.ArrayList;

/** Reads the existing project component through a public serializer, alongside the platform serializer. */
final class JpsGwtRepositoryConfigurationSerializer extends JpsProjectExtensionSerializer {
  JpsGwtRepositoryConfigurationSerializer() {
    super("jarRepositories.xml", "RemoteRepositoriesConfiguration");
  }

  @Override
  public void loadExtension(JpsProject project, Element componentTag) {
    var repositories = new ArrayList<GwtRepositoryConfiguration.Repository>();
    for (var repository : componentTag.getChildren("remote-repository")) {
      String id = null;
      String url = null;
      for (var option : repository.getChildren("option")) {
        if ("id".equals(option.getAttributeValue("name"))) id = option.getAttributeValue("value");
        if ("url".equals(option.getAttributeValue("name"))) url = option.getAttributeValue("value");
      }
      if (id != null && url != null) repositories.add(new GwtRepositoryConfiguration.Repository(id, url));
    }
    GwtRepositoryConfiguration.set(project, repositories.isEmpty() ? GwtRepositoryConfiguration.defaults() : repositories);
  }

  @Override
  public void loadExtensionWithDefaultSettings(JpsProject project) {
    GwtRepositoryConfiguration.set(project, GwtRepositoryConfiguration.defaults());
  }
}
