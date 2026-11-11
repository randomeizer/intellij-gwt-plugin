package org.randombits.intellij.gwt.model;

import org.randombits.intellij.gwt.GwtTestCase;
import org.randombits.intellij.gwt.module.GwtModulesManager;
import org.randombits.intellij.gwt.module.model.GwtModule;
import org.randombits.intellij.gwt.superSource.GwtSuperSourceElementFinder;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootModificationUtil;

public class GwtSuperSourceElementFinderTest extends GwtTestCase {
  private Sdk myTestSdk;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTestSdk = JavaSdk.getInstance().createJdk("test JDK", System.getProperty("java.home"), false);
    WriteAction.run(() -> ProjectJdkTable.getInstance().addJdk(myTestSdk));
    ModuleRootModificationUtil.setModuleSdk(myModule, myTestSdk);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myTestSdk != null) {
        ModuleRootModificationUtil.setModuleSdk(myModule, null);
        WriteAction.run(() -> ProjectJdkTable.getInstance().removeJdk(myTestSdk));
      }
    }
    finally {
      super.tearDown();
    }
  }

  public void testFindUserClass() throws Exception {
    GwtSuperSourceElementFinder finder = new GwtSuperSourceElementFinder(myProject);
    GlobalSearchScope searchScope = GlobalSearchScope.allScope(myProject);

    VirtualFile moduleDir = addGwtModule("model/superSource", myModule, getLatestVersion());
    VirtualFile file = VfsUtilCore.findRelativeFile("ppp/sup/xxx/MyClass.java", moduleDir);

    WriteAction.run(() -> file.rename(this, "MyClass.notjava"));
    assertNull(finder.findClass("xxx.MyClass", searchScope));

    WriteAction.run(() -> file.rename(this, "MyClass.java"));
    PsiClass psiClass = finder.findClass("xxx.MyClass", searchScope);
    assertNotNull(psiClass);
    assertEquals("sup", psiClass.getContainingFile().getParent().getParent().getName());

    WriteAction.run(() -> file.delete(this));
    assertNull(finder.findClass("xxx.MyClass", searchScope));
  }

  public void testModifyGwtXml() {
    addGwtModule("model/superSource", myModule, getLatestVersion());
    final GwtModule gwtModule = assertOneElement(
      GwtModulesManager.getInstance(myProject).findGwtModulesByQualifiedName("ppp.MyModule", GlobalSearchScope.projectScope(myProject)));
    WriteCommandAction.runWriteCommandAction(myProject, () -> assertOneElement(gwtModule.getSuperSources()).undefine());
    PsiPackage packageAfterRemoval = findXxxPackage();
    if (packageAfterRemoval != null) {
      assertEmpty(packageAfterRemoval.getDirectories(GlobalSearchScope.projectScope(myProject)));
    }

    WriteCommandAction.runWriteCommandAction(myProject, () -> gwtModule.addSuperSource().getPath().setValue("sup"));
    PsiDirectory directory = assertOneElement(findXxxPackage().getDirectories(GlobalSearchScope.projectScope(myProject)));
    assertEquals("sup", directory.getParent().getName());
  }

  private PsiPackage findXxxPackage() {
    return JavaPsiFacade.getInstance(myProject).findPackage("xxx");
  }

  public void testFindSdkClass() {
    doTestFindClasses(CommonClassNames.JAVA_LANG_OBJECT, "com/google/gwt/emul/java/lang/Object.java");
  }

  public void testFindInnerClass() {
    doTestFindClasses("java.util.Map.Entry", "com/google/gwt/emul/java/util/Map.java");
  }

  private void doTestFindClasses(final String className, final String classFilePath) {
    addGwtFacet(myModule, getLatestVersion());
    addGwtLibrary(myModule, getLatestVersion());
    final PsiClass[] classes = JavaPsiFacade
      .getInstance(myProject).findClasses(className, GlobalSearchScope.allScope(myProject));
    assertSize(2, classes);
    final VirtualFile classFile = classes[1].getContainingFile().getVirtualFile();
    assertNotNull(classFile);
    assertEquals(getMockGwtUserJarPath(getLatestVersion()) + classFilePath, classFile.getPath());
  }
}
