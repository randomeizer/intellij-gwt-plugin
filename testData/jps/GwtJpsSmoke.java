import org.jetbrains.jps.api.BuildType;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.api.CmdlineRemoteProto.Message.ControllerMessage.ParametersMessage.TargetTypeBuildScope;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.java.JavaModuleBuildTargetType;
import org.jetbrains.jps.cmdline.BuildRunner;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuildTargetType;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.artifact.elements.JpsPackagingElementFactory;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.LanguageLevel;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryService;
import org.jetbrains.jps.model.jarRepository.JpsRemoteRepositoryDescription;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;
import org.jetbrains.jps.util.JpsPathUtil;
import org.randombits.intellij.gwt.jps.build.GwtBuildTarget;
import org.randombits.intellij.gwt.jps.build.GwtBuildTargetType;
import org.randombits.intellij.gwt.jps.build.GwtBuilderParameters;
import org.randombits.intellij.gwt.jps.build.JpsGwtCompilerPaths;
import org.randombits.intellij.gwt.jps.model.JpsGwtExtensionService;
import org.randombits.intellij.gwt.jps.model.impl.GwtModuleExtensionProperties;
import org.randombits.intellij.gwt.jps.model.impl.GwtModulePackagingProperties;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtCompilerOutputPackagingElement;
import org.randombits.intellij.gwt.jps.model.impl.JpsGwtModuleExtensionImpl;
import org.randombits.intellij.gwt.jps.model.impl.sdk.JpsGwtDependenciesStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real JPS orchestration and real GWT compiler; source-launched in an isolated JVM. */
public class GwtJpsSmoke {
  private static Path work;
  private static Path repository;

  public static void main(String[] args) {
    try {
      repository = Path.of(args[0]);
      work = Path.of(args[1]);
      boolean builderFound = false;
      for (var service : org.jetbrains.jps.service.JpsServiceManager.getInstance()
        .getExtensions(org.jetbrains.jps.incremental.BuilderService.class)) {
        if (service instanceof org.randombits.intellij.gwt.jps.build.GwtBuilderService) builderFound = true;
      }
      require(builderFound, "GWT JPS builder service is missing from child classpath");
      Files.createDirectories(work.resolve("src/client"));
      moduleXml("probe");
      source("first", false);
      var storage = new JpsGwtDependenciesStorage(new BuildDataPathsImpl(work.resolve("cache")));
      try (var jars = Files.walk(repository)) {
        var paths = jars.filter(p -> p.toString().endsWith(".jar")).map(Path::toString).toList();
        storage.storeDependenciesPaths("org.gwtproject", "gwt-dev", "2.13.1", paths);
        storage.storeDependenciesPaths("org.gwtproject", "gwt-user", "2.13.1", paths);
      }
      build(false, Map.of(), false);
      require(Files.isRegularFile(work.resolve("artifact/custom/nested/probe.nocache.js")), "Fresh artifact missed regular output");
      require(!symbolMaps().isEmpty(), "Fresh artifact missed deployment symbol maps");
      System.out.println("FRESH_ARTIFACT_COMPILE_OK");

      var noOp = build(false, Map.of(), false);
      require(noOp.stream().noneMatch(s -> s.contains("Compiling GWT module")), "No-op invoked GWT compiler");
      System.out.println("NO_OP_OK");

      String before = scripts();
      source("second", false);
      build(false, Map.of(), false);
      require(!before.equals(scripts()), "Incremental edit did not update artifact JavaScript");
      System.out.println("INCREMENTAL_OK");

      build(false, Map.of("GWT_COMPILE_REPORT_SOURCE",
        GwtBuilderParameters.generateCompileReportParameter("client", "Probe")), false);
      Path extra = outputRoot(true);
      try (var files = Files.walk(extra)) {
        require(files.anyMatch(p -> p.getFileName().toString().endsWith(".html")), "Real compiler report missing");
      }
      System.out.println("REPORT_OK");

      moduleXml("updated");
      build(false, Map.of(), false);
      require(Files.isRegularFile(work.resolve("artifact/custom/nested/updated.nocache.js")), "Changed rename-to was not packaged");
      require(!Files.exists(work.resolve("artifact/custom/nested/probe.nocache.js")), "Old renamed artifact output remained");
      System.out.println("RENAME_AND_STALE_REMOVAL_OK");

      // IDEA's Clean Artifact action deletes its output directory; BuildRunner's CLEAN enum is a no-op.
      require(com.intellij.openapi.util.io.FileUtil.delete(work.resolve("artifact").toFile()), "Cannot clean fixture artifact");
      require(!Files.exists(work.resolve("artifact/custom/nested/updated.nocache.js")), "Clean left artifact output");
      build(true, Map.of(), false);
      require(Files.isRegularFile(work.resolve("artifact/custom/nested/updated.nocache.js")), "Rebuild missed artifact output");
      System.out.println("CLEAN_REBUILD_OK");

      source("broken", true);
      build(false, Map.of(), true);
      System.out.println("COMPILE_FAILURE_OK");
      System.out.println("DONE");
      System.exit(0);
    }
    catch (Throwable failure) {
      failure.printStackTrace();
      System.exit(1);
    }
  }

  private static JpsModel model() throws java.io.IOException {
    var model = JpsElementFactory.getInstance().createModel();
    var module = model.getProject().addModule("client", JpsJavaModuleType.INSTANCE);
    module.addSourceRoot(JpsPathUtil.pathToUrl(work.resolve("src").toString()), JavaSourceRootType.SOURCE);
    var java = JpsJavaExtensionService.getInstance();
    var sdk = java.addJavaSdk(model.getGlobal(), "smoke-jdk", System.getProperty("java.home"));
    sdk.getProperties().setVersionString(System.getProperty("java.version"));
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, sdk.getProperties().createReference());
    module.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);
    var moduleJava = java.getOrCreateModuleExtension(module);
    moduleJava.setOutputUrl(JpsPathUtil.pathToUrl(work.resolve("classes").toString()));
    moduleJava.setLanguageLevel(LanguageLevel.JDK_17);
    var library = module.addModuleLibrary("real-gwt", JpsJavaLibraryType.INSTANCE);
    try (var jars = Files.walk(repository)) {
      jars.filter(p -> p.toString().endsWith(".jar")).forEach(p -> library.addRoot(p, JpsOrderRootType.COMPILED));
    }
    module.getDependenciesList().addLibraryDependency(library);
    JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.getGlobal())
      .addPathVariable("MAVEN_REPOSITORY", repository.toString());
    JpsRemoteRepositoryService.getInstance().getOrCreateRemoteRepositoriesConfiguration(model.getProject())
      .setRepositories(List.of(new JpsRemoteRepositoryDescription("fixture", "Local SDK fixture", repository.toUri().toString())));
    var properties = new GwtModuleExtensionProperties();
    properties.mySdkType = "maven";
    properties.mySdkUrl = JpsPathUtil.pathToUrl(repository.resolve("org/gwtproject/gwt-dev/2.13.1").toString());
    properties.myCompilerParameters = "-localWorkers 1 -sourceLevel 17 -optimize 0";
    properties.myCompilerMaxHeapSize = 768;
    var packaging = new GwtModulePackagingProperties();
    packaging.myName = "Probe";
    packaging.myPath = "/custom/nested";
    properties.myPackagingStates.add(packaging);
    JpsGwtExtensionService.getInstance().setExtension(module, new JpsGwtModuleExtensionImpl(properties));
    var root = JpsPackagingElementFactory.getInstance().createArtifactRoot();
    for (var kind : JpsGwtCompilerOutputPackagingElement.OutputKind.values()) {
      root.addChild(new JpsGwtCompilerOutputPackagingElement(module.createReference(), kind));
    }
    var artifact = JpsArtifactService.getInstance().addArtifact(model.getProject(), "web", root, DirectoryArtifactType.INSTANCE,
      JpsElementFactory.getInstance().createDummyElement());
    artifact.setOutputPath(work.resolve("artifact").toString());
    return model;
  }

  private static List<String> build(boolean rebuild, Map<String, String> parameters, boolean expectErrors) throws Exception {
    var messages = new ArrayList<String>();
    var errors = new ArrayList<String>();
    org.jetbrains.jps.incremental.MessageHandler handler = message -> {
      synchronized (messages) {
        messages.add(message.getMessageText());
        if (message.getKind() == BuildMessage.Kind.ERROR) errors.add(message.getMessageText());
      }
    };
    var runner = new BuildRunner(GwtJpsSmoke::model);
    runner.setBuilderParams(parameters);
    var descriptor = runner.load(handler, work.resolve("cache"), new BuildFSState(true));
    try {
      var scope = TargetTypeBuildScope.newBuilder().setTypeId(ArtifactBuildTargetType.INSTANCE.getTypeId())
        .addTargetId("web").setForceBuild(rebuild).build();
      var scopes = new ArrayList<TargetTypeBuildScope>();
      scopes.add(scope);
      if (rebuild) {
        scopes.add(TargetTypeBuildScope.newBuilder().setTypeId(GwtBuildTargetType.TYPE_ID).addTargetId("client").setForceBuild(true).build());
        scopes.add(TargetTypeBuildScope.newBuilder().setTypeId(JavaModuleBuildTargetType.PRODUCTION.getTypeId())
          .addTargetId("client").setForceBuild(true).build());
      }
      runner.runBuild(descriptor, CanceledStatus.NULL, handler, BuildType.BUILD, scopes, true);

    }
    finally {
      descriptor.release();
    }
    if (expectErrors) require(!errors.isEmpty(), "Broken GWT source unexpectedly compiled");
    else require(errors.isEmpty(), "JPS errors: " + String.join("\n", errors));
    return messages;
  }

  private static Path outputRoot(boolean extra) throws Exception {
    var module = model().getProject().getModules().getFirst();
    var target = new GwtBuildTarget(JpsGwtExtensionService.getInstance().getExtension(module));
    var paths = new BuildDataPathsImpl(work.resolve("cache"));
    return (extra ? JpsGwtCompilerPaths.getExtraOutputRoot(target, paths) : JpsGwtCompilerPaths.getCompilerOutputRoot(target, paths)).toPath();
  }

  private static String scripts() throws Exception {
    try (var files = Files.walk(work.resolve("artifact/custom/nested"))) {
      var result = new StringBuilder();
      for (var file : files.filter(p -> p.toString().endsWith(".cache.js")).sorted().toList()) result.append(Files.readString(file));
      require(!result.isEmpty(), "No compiled JavaScript in artifact");
      return result.toString();
    }
  }

  private static List<Path> symbolMaps() throws Exception {
    try (var files = Files.walk(work.resolve("artifact/WEB-INF/deploy/custom/nested"))) {
      return files.filter(p -> p.toString().endsWith(".symbolMap")).toList();
    }
  }

  private static void moduleXml(String name) throws Exception {
    Files.writeString(work.resolve("src/Probe.gwt.xml"), "<module rename-to='" + name + "'>" +
      "<inherits name='com.google.gwt.user.User'/><set-property name='user.agent' value='safari'/>" +
      "<source path='client'/><entry-point class='client.Probe'/></module>");
  }

  private static void source(String message, boolean invalidJs) throws Exception {
    Files.writeString(work.resolve("src/client/Probe.java"), "package client;\n" +
      "public class Probe implements com.google.gwt.core.client.EntryPoint {\n" +
      "public void onModuleLoad() { log(\"" + message + "\"); }\n" +
      "private static native void log(String value) /*-{ " + (invalidJs ? "this is invalid JS" : "console.log(value);") + " }-*/;\n}");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
}
