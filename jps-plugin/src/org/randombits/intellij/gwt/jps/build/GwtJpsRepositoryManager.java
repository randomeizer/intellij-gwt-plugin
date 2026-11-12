// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.randombits.intellij.gwt.jps.build;

import com.intellij.openapi.util.Key;
import com.intellij.util.SystemProperties;
import org.jetbrains.idea.maven.aether.ArtifactRepositoryManager;
import org.jetbrains.idea.maven.aether.ProgressConsumer;
import org.jetbrains.idea.maven.aether.Retry;
import org.jetbrains.idea.maven.aether.RetryProvider;
import org.jetbrains.jps.incremental.CompileContext;
import org.jetbrains.jps.incremental.dependencies.DependencyAuthenticationDataProvider;
import org.jetbrains.jps.incremental.messages.ProgressMessage;
import org.jetbrains.jps.model.JpsGlobal;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.service.JpsServiceManager;
import org.randombits.intellij.gwt.jps.model.GwtRepositoryConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Native IDEA 263 resolution policy with a public, per-build repository configuration handoff. */
final class GwtJpsRepositoryManager {
  static final String AUTH_PROPERTY_PREFIX = "org.jetbrains.jps.incremental.dependencies.resolution.remote.repository.auth.";
  private static final String RETRY_PREFIX = "org.jetbrains.jps.incremental.dependencies.resolution.retry.";
  private static final Key<ArtifactRepositoryManager> MANAGER = Key.create("gwt.jps.repository.manager");

  private GwtJpsRepositoryManager() {}

  static synchronized ArtifactRepositoryManager get(CompileContext context) {
    ArtifactRepositoryManager manager = MANAGER.get(context);
    if (manager == null) {
      String encoded = context.getBuilderParameter(GwtRepositoryConfiguration.BUILDER_PARAMETER);
      var descriptor = context.getProjectDescriptor();
      var repositories = encoded == null ? GwtRepositoryConfiguration.get(descriptor.getProject()) :
        GwtRepositoryConfiguration.decode(encoded);
      manager = create(localRepository(descriptor.getModel().getGlobal()), repositories, new ProgressConsumer() {
        @Override
        public void consume(String message) {
          context.processMessage(new ProgressMessage(message));
        }

        @Override
        public boolean isCanceled() {
          return context.getCancelStatus().isCanceled();
        }
      });
      MANAGER.set(context, manager);
    }
    return manager;
  }

  static ArtifactRepositoryManager create(File local, List<GwtRepositoryConfiguration.Repository> configuration, ProgressConsumer progress) {
    var settings = GwtJpsMavenSettings.loadAuthenticationSettings(
      GwtJpsMavenSettings.getGlobalMavenSettingsXml(), GwtJpsMavenSettings.getUserMavenSettingsXml());
    var providers = JpsServiceManager.getInstance().getExtensions(DependencyAuthenticationDataProvider.class);
    var repositories = new ArrayList<org.eclipse.aether.repository.RemoteRepository>();
    for (var repository : configuration) {
      repositories.add(ArtifactRepositoryManager.createRemoteRepository(repository.id, repository.url,
        authentication(repository, settings, providers)));
    }
    Retry retry = RetryProvider.disabled();
    if (SystemProperties.getBooleanProperty(RETRY_PREFIX + "enabled", false)) {
      retry = RetryProvider.withExponentialBackOff(
        SystemProperties.getLongProperty(RETRY_PREFIX + "delay.ms", 1000),
        SystemProperties.getLongProperty(RETRY_PREFIX + "backoff.limit.ms", TimeUnit.MINUTES.toMillis(15)),
        SystemProperties.getIntProperty(RETRY_PREFIX + "max.attempts", 10));
    }
    return new ArtifactRepositoryManager(local, repositories, progress, retry);
  }

  static ArtifactRepositoryManager.ArtifactAuthenticationData authentication(
    GwtRepositoryConfiguration.Repository repository,
    Map<String, GwtJpsMavenSettings.RemoteRepositoryAuthentication> settings,
    Iterable<DependencyAuthenticationDataProvider> providers
  ) {
    String property = AUTH_PROPERTY_PREFIX + repository.id;
    String value = System.getProperty(property);
    if (value != null) {
      String[] parts = value.split(":");
      if (parts.length != 2) throw new IllegalArgumentException("Malformed repository authentication property '" + property + "'");
      return new ArtifactRepositoryManager.ArtifactAuthenticationData(parts[0], parts[1]);
    }
    for (var provider : providers) {
      var auth = provider.provideAuthenticationData(repository.url);
      if (auth != null) return new ArtifactRepositoryManager.ArtifactAuthenticationData(auth.getUserName(), auth.getPassword());
    }
    var auth = settings.get(repository.id);
    return auth == null ? null : new ArtifactRepositoryManager.ArtifactAuthenticationData(auth.username, auth.password);
  }

  static File localRepository(JpsGlobal global) {
    String path = JpsModelSerializationDataService.getPathVariableValue(global, "MAVEN_REPOSITORY");
    return new File(path != null ? path : GwtJpsMavenSettings.getMavenRepositoryPath());
  }
}
