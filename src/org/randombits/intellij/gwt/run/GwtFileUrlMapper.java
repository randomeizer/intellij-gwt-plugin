package org.randombits.intellij.gwt.run;

import org.randombits.intellij.gwt.GwtBundle;
import org.randombits.intellij.gwt.module.GwtModulesManager;
import org.randombits.intellij.gwt.module.model.GwtModule;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbModeBlockedFunctionality;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.jetbrains.javascript.debugger.FileUrlMapper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

final class GwtFileUrlMapper extends FileUrlMapper {
  private static final @NonNls String PATH_PREFIX = "/sourcemaps/";

  @Override
  public @NotNull List<Url> getUrls(@NotNull VirtualFile file, @NotNull Project project, @Nullable String currentAuthority) {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaFileType.INSTANCE)) return List.of();
    var urls = nonBlockingReadActionInSmartMode(project, () -> {
      var root = classpathRoot(project, file);
      if (root == null) return List.<Url>of();
      String relative = VfsUtilCore.getRelativePath(file, root, '/');
      if (relative == null) return List.<Url>of();
      var manager = GwtModulesManager.getInstance(project);
      var modules = allModules(project, manager);
      var owners = modules.stream().filter(module -> ownsSource(project, module, file, relative)).toList();
      if (owners.isEmpty()) return List.<Url>of();
      var endpoints = GwtCodeServerUrls.get(project, currentAuthority);
      Set<Url> result = new LinkedHashSet<>();
      for (var module : modules) {
        var ideaModule = module.getModule();
        if (ideaModule != null && !visibleSource(project, ideaModule, file, relative)) continue;
        if (owners.stream().noneMatch(owner -> manager.isInheritedOrSelf(module, owner))) continue;
        for (String endpoint : endpoints) {
          result.add(Urls.parseEncoded(endpoint + PATH_PREFIX + module.getOutputName() + '/' + relative));
        }
      }
      return List.copyOf(result);
    }, GwtBundle.message("notification.content.gwt.file.mapping.is.not.possible.during.index.update"));
    return urls == null ? List.of() : urls;
  }

  private static VirtualFile classpathRoot(Project project, VirtualFile file) {
    var index = ProjectRootManager.getInstance(project).getFileIndex();
    var source = index.getSourceRootForFile(file);
    return source != null ? source : index.getClassRootForFile(file);
  }

  private static boolean visibleSource(Project project, Module module, VirtualFile file, String relative) {
    var scope = module.getModuleWithDependenciesAndLibrariesScope(true);
    if (scope.contains(file)) return true;
    var index = ProjectRootManager.getInstance(project).getFileIndex();
    if (!index.isInLibrarySource(file)) return false;
    for (var entry : index.getOrderEntriesForFile(file)) {
      if (!(entry instanceof LibraryOrderEntry libraryEntry) || libraryEntry.getLibrary() == null) continue;
      for (var root : libraryEntry.getLibrary().getFiles(OrderRootType.CLASSES)) {
        var compiled = root.findFileByRelativePath(relative);
        if (compiled != null && scope.contains(compiled)) return true;
      }
    }
    return false;
  }

  private static boolean ownsSource(Project project, GwtModule module, VirtualFile file, String relative) {
    if (module.isSourceFile(file) || module.isSuperSourceFile(file)) return true;
    var index = ProjectRootManager.getInstance(project).getFileIndex();
    if (!index.isInLibrarySource(file)) return false;
    // Attached sources may omit .gwt.xml; use their own library's compiled GWT definition/resources.
    for (var entry : index.getOrderEntriesForFile(file)) {
      if (!(entry instanceof LibraryOrderEntry libraryEntry) || libraryEntry.getLibrary() == null) continue;
      for (var root : libraryEntry.getLibrary().getFiles(OrderRootType.CLASSES)) {
        if (!VfsUtilCore.isAncestor(root, module.getModuleFile(), false)) continue;
        var compiledSource = root.findFileByRelativePath(relative);
        if (compiledSource != null && (module.isSourceFile(compiledSource) || module.isSuperSourceFile(compiledSource))) return true;
      }
    }
    return false;
  }

  private static VirtualFile sourceView(Project project, VirtualFile file, String relative) {
    var index = ProjectRootManager.getInstance(project).getFileIndex();
    if (!index.isInLibraryClasses(file)) return file;
    for (var entry : index.getOrderEntriesForFile(file)) {
      if (!(entry instanceof LibraryOrderEntry libraryEntry) || libraryEntry.getLibrary() == null) continue;
      for (var root : libraryEntry.getLibrary().getFiles(OrderRootType.SOURCES)) {
        var source = root.findFileByRelativePath(relative);
        if (source != null) return source;
      }
    }
    return file;
  }

  private static List<GwtModule> allModules(Project project, GwtModulesManager manager) {
    List<GwtModule> result = new ArrayList<>();
    var psiManager = PsiManager.getInstance(project);
    for (var file : manager.getGwtModuleFiles(GlobalSearchScope.allScope(project))) {
      var psi = psiManager.findFile(file);
      var module = psi == null ? null : manager.getGwtModuleByXmlFile(psi);
      if (module != null) result.add(module);
    }
    return result;
  }

  private static VirtualFile findSourceFile(Url url, Project project) {
    String path = url.getPath();
    if (!path.startsWith(PATH_PREFIX) || !path.endsWith(JavaFileType.DOT_DEFAULT_EXTENSION)) return null;
    int separator = path.indexOf('/', PATH_PREFIX.length());
    if (separator < 0) return null;
    String outputName = path.substring(PATH_PREFIX.length(), separator);
    String relative = path.substring(separator + 1);
    if (relative.contains("\\") || Arrays.stream(relative.split("/", -1)).anyMatch(part -> part.isEmpty() || part.equals(".") || part.equals(".."))) return null;
    return nonBlockingReadActionInSmartMode(project, () -> {
      var manager = GwtModulesManager.getInstance(project);
      var modules = allModules(project, manager);
      for (var output : modules) {
        if (!outputName.equals(output.getOutputName())) continue;
        var module = output.getModule();
        var scope = module == null ? null : module.getModuleWithDependenciesAndLibrariesScope(true);
        for (var owner : modules) {
          if (!manager.isInheritedOrSelf(output, owner)) continue;
          var root = classpathRoot(project, owner.getModuleFile());
          var file = root == null ? null : root.findFileByRelativePath(relative);
          if (file != null && (scope == null || scope.contains(file)) &&
              (owner.isSourceFile(file) || owner.isSuperSourceFile(file))) return sourceView(project, file, relative);
        }
      }
      return null;
    }, GwtBundle.message("notification.content.gwt.file.mapping.is.not.possible.during.index.update"));
  }

  private static @Nullable PsiClass findClass(@NotNull Url url, final @NotNull Project project) {
    final String path = url.getPath();
    if (!(path.startsWith(PATH_PREFIX) && path.endsWith(JavaFileType.DOT_DEFAULT_EXTENSION))) {
      return null;
    }

    int index = path.indexOf('/', PATH_PREFIX.length() + 1);
    if (index < 0 || path.length() < (index + 2 + JavaFileType.DOT_DEFAULT_EXTENSION.length())) {
      return null;
    }

    final String moduleName = path.substring(PATH_PREFIX.length(), index);
    final String className = path.substring(index + 1, path.length() - JavaFileType.DOT_DEFAULT_EXTENSION.length()).replace('/', '.');

    return nonBlockingReadActionInSmartMode(project, () -> {
      GwtModulesManager gwtModulesManager = GwtModulesManager.getInstance(project);
      for (GwtModule gwtModule : gwtModulesManager.findGwtModulesByOutputName(moduleName, GlobalSearchScope.projectScope(project))) {
        Module module = gwtModule.getModule();
        if (module != null) {
          PsiClass[] psiClasses = JavaPsiFacade.getInstance(project).findClasses(className, module.getModuleWithLibrariesScope());
          if (psiClasses.length == 1) {
            return psiClasses[0];
          }
          else if (psiClasses.length > 1) {
            for (PsiClass psiClass : psiClasses) {
              if (psiClass.canNavigateToSource() && psiClass.getNavigationElement().getContainingFile().getFileType() == JavaFileType.INSTANCE) {
                return psiClass;
              }
            }
            Arrays.sort(psiClasses, new Comparator<>() {
              private int getWeight(@NotNull PsiClass psiClass) {
                if (psiClass.canNavigateToSource()) {
                  return 2;
                }
                return psiClass.canNavigate() ? 1 : 0;
              }

              @Override
              public int compare(@NotNull PsiClass c1, @NotNull PsiClass c2) {
                return getWeight(c1) - getWeight(c2);
              }
            });
            return psiClasses[psiClasses.length - 1];
          }
        }
      }
      return null;
    }, GwtBundle.message("notification.content.gwt.file.mapping.is.not.possible.during.index.update"));
  }

  private static <T> T nonBlockingReadActionInSmartMode(@NotNull Project project,
                                                        @NotNull Supplier<? extends T> task,
                                                        @NotNull @NlsContexts.PopupContent String notification) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      try {
        return task.get();
      }
      catch (IndexNotReadyException e) {
        DumbService.getInstance(project).showDumbModeNotificationForFunctionality(notification, DumbModeBlockedFunctionality.Other);
        return null;
      }
    }

    return ReadAction.nonBlocking(() -> task.get())
      .inSmartMode(project)
      .executeSynchronously();
  }

  @Override
  public @Nullable Navigatable getNavigatable(@NotNull Url url, @NotNull Project project, @Nullable Url requestor) {
    var file = findSourceFile(url, project);
    if (file != null) return ReadAction.computeBlocking(() -> PsiManager.getInstance(project).findFile(file));
    return findClass(url, project);
  }

  @Override
  public @Nullable VirtualFile getFile(@NotNull Url url, final @NotNull Project project, @Nullable Url requestor) {
    var source = findSourceFile(url, project);
    if (source != null) return source;
    PsiClass psiClass = findClass(url, project);
    if (psiClass == null) {
      return null;
    }
    else {
      return ReadAction.computeBlocking(()-> {
        PsiFile psiFile;
        psiFile = psiClass.getNavigationElement().getContainingFile();
        if (psiFile == null) {
          psiFile = psiClass.getContainingFile();
        }
        return psiFile == null ? null : psiFile.getVirtualFile();
      });
    }
  }

}
