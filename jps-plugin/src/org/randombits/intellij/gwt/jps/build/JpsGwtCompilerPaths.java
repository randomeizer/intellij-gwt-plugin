package org.randombits.intellij.gwt.jps.build;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

public final class JpsGwtCompilerPaths {
  private static final byte[] OWNER = "org.randombits.gwtplugin JPS output v1\n".getBytes(StandardCharsets.UTF_8);

  private JpsGwtCompilerPaths() {}

  public static File getCompilerOutputRoot(@NotNull GwtBuildTarget target) {
    return getModuleOutputDirectory(target.getExtension().getModule()).resolve("war").toFile();
  }

  public static File getExtraOutputRoot(@NotNull GwtBuildTarget target) {
    return getModuleOutputDirectory(target.getExtension().getModule()).resolve("extra").toFile();
  }

  // Preserve callers of the plugin's former path API; layout is now available before a build context exists.
  public static File getCompilerOutputRoot(@NotNull GwtBuildTarget target, BuildDataPaths dataPaths) {
    return getCompilerOutputRoot(target);
  }

  public static File getExtraOutputRoot(@NotNull GwtBuildTarget target, BuildDataPaths dataPaths) {
    return getExtraOutputRoot(target);
  }

  public static Path getModuleOutputDirectory(JpsModule module) {
    Path production = JpsJavaExtensionService.getInstance().getOutputDirectoryPath(module, false);
    if (production == null || production.toAbsolutePath().normalize().getParent() == null) {
      throw new IllegalStateException("GWT compilation requires a production output directory for module '" + module.getName() + "'");
    }
    production = production.toAbsolutePath().normalize();
    Path project = JpsModelSerializationDataService.getBaseDirectoryPath(module.getProject());
    String identity = project != null ? project.toAbsolutePath().normalize().toString() :
      module.getSourceRoots().stream().map(root -> root.getUrl()).sorted().collect(Collectors.joining("\n"));
    String key = UUID.nameUUIDFromBytes((identity + "\n" + production + "\n" + module.getName())
                                        .getBytes(StandardCharsets.UTF_8)).toString();
    return production.getParent().resolve(".intellij-gwt").resolve(key);
  }

  /** Validate ownership before JPS can clean output roots or an artifact can copy them. */
  public static void checkOutputLocation(JpsModule module) throws IOException {
    Path directory = getModuleOutputDirectory(module);
    for (Path path : new Path[]{directory.getParent(), directory, directory.resolve("war"), directory.resolve("extra")}) {
      if (Files.isSymbolicLink(path)) throw new IOException("GWT output location is a symbolic link: " + path);
    }
    if (Files.notExists(directory)) return;
    Path marker = directory.resolve(".owner");
    if (!Files.isRegularFile(marker) || Files.isSymbolicLink(marker) || Files.size(marker) != OWNER.length ||
        !Arrays.equals(Files.readAllBytes(marker), OWNER)) {
      throw new IOException("GWT output directory contains unowned data: " + directory);
    }
  }

  public static void prepareOutputLocation(JpsModule module) throws IOException {
    checkOutputLocation(module);
    Path directory = getModuleOutputDirectory(module);
    if (Files.notExists(directory)) {
      Files.createDirectories(directory);
      try {
        Files.write(directory.resolve(".owner"), OWNER, StandardOpenOption.CREATE_NEW);
      }
      catch (FileAlreadyExistsException e) {
        checkOutputLocation(module);
      }
    }
    checkOutputLocation(module);
  }
}
