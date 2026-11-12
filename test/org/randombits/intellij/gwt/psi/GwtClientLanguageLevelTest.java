package org.randombits.intellij.gwt.psi;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.roots.impl.JavaLanguageLevelPusherCustomizer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.impl.source.PsiJavaFileBaseImpl;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.randombits.intellij.gwt.GwtTestCase;
import org.randombits.intellij.gwt.facet.GwtFacet;
import org.randombits.intellij.gwt.sdk.impl.GwtVersionImpl;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Active runtime coverage for the retained integration and the limits of public PSI overrides. */
public class GwtClientLanguageLevelTest extends JavaCodeInsightFixtureTestCase {
  private GwtFacet facet;
  private PsiJavaFile client;
  private PsiJavaFile superSource;
  private PsiJavaFile server;
  private PsiFile module;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_17);
    facet = GwtTestCase.addGwtFacet(getModule(), GwtVersionImpl.VERSION_2_13);
    facet.getConfiguration().setCompilerParameters("-sourceLevel 8");
    module = myFixture.addFileToProject("sample/App.gwt.xml",
      "<module><source path='client'/><super-source path='super'/></module>");
    client = (PsiJavaFile)myFixture.addFileToProject("sample/client/Client.java", "package sample.client; public class Client {}");
    superSource = (PsiJavaFile)myFixture.addFileToProject("sample/super/example/Shared.java", "package example; public class Shared {}");
    server = (PsiJavaFile)myFixture.addFileToProject("sample/server/Server.java", "package sample.server; public class Server {}");
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }

  public void testClientSuperSourceAndServerHaveIndependentEditorLevels() {
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_17);
    assertEquals(LanguageLevel.JDK_1_8, client.getLanguageLevel());
    assertEquals(LanguageLevel.JDK_1_8, superSource.getLanguageLevel());
    assertEquals(LanguageLevel.JDK_17, server.getLanguageLevel());
    assertEquals("8", facet.getConfiguration().getClientLanguageLevelString());
  }

  public void testEqualLevelsAndSettingsChangesRefreshNativeFileLevels() {
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_17);
    facet.getConfiguration().setCompilerParameters("-sourceLevel 17");
    refresh(LanguageLevel.JDK_17, LanguageLevel.JDK_17);
    assertEquals(LanguageLevel.JDK_17, level(client.getVirtualFile()));
    facet.getConfiguration().setCompilerParameters("-sourceLevel 8");
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_17);
    assertEquals(LanguageLevel.JDK_1_8, level(client.getVirtualFile()));
  }

  public void testModuleLevelChangesLeaveClientCompilerSettingIndependent() {
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_17);
    IdeaTestUtil.setModuleLanguageLevel(getModule(), LanguageLevel.JDK_21);
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_21);
    assertEquals(LanguageLevel.JDK_1_8, superSource.getLanguageLevel());
    assertEquals("8", facet.getConfiguration().getClientLanguageLevelString());
  }

  public void testSourcePathEditsRefreshOldAndNewRoots() {
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_17);
    var replacement = (PsiJavaFile)myFixture.addFileToProject("sample/client2/Replacement.java", "package sample.client2; public class Replacement {}");
    var document = PsiDocumentManager.getInstance(getProject()).getDocument(module);
    assertNotNull(document);
    WriteAction.run(() -> {
      document.setText("<module><source path='client2'/><super-source path='super'/></module>");
      PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    });
    GwtLanguageLevelPusher.updateConfigurationAndPush(getProject(), true);
    PlatformTestUtil.waitWithEventsDispatching("Changed GWT source roots were not pushed", () ->
      level(client.getVirtualFile()) == LanguageLevel.JDK_17 && level(replacement.getVirtualFile()) == LanguageLevel.JDK_1_8, 30);
    assertEquals(LanguageLevel.JDK_1_8, level(superSource.getVirtualFile()));
    assertEquals(LanguageLevel.JDK_17, level(server.getVirtualFile()));
  }

  public void testPublicPsiOverrideDoesNotChangeResolveOrIndexLanguageLevel() {
    // Test-only masking establishes what the public key can do without the Internal extension.
    ExtensionTestUtil.maskExtensions(JavaLanguageLevelPusherCustomizer.EP_NAME, List.of(), getTestRootDisposable());
    refresh(LanguageLevel.JDK_17, LanguageLevel.JDK_17);
    client.putUserData(PsiUtil.FILE_LANGUAGE_LEVEL_KEY, LanguageLevel.JDK_1_8);
    ((PsiJavaFileBaseImpl)client).clearCaches();
    assertEquals(LanguageLevel.JDK_1_8, client.getLanguageLevel());
    assertEquals("Public PSI userdata does not replace native resolve/indexing state", LanguageLevel.JDK_17, level(client.getVirtualFile()));
    assertEquals("8", facet.getConfiguration().getClientLanguageLevelString());
  }

  public void testModernUnsupportedFeatureDiagnosticDoesNotCallLegacyMessageCustomizer() {
    AtomicInteger calls = new AtomicInteger();
    var delegate = new GwtLanguageLevelPusher();
    ExtensionTestUtil.maskExtensions(JavaLanguageLevelPusherCustomizer.EP_NAME, List.of(new JavaLanguageLevelPusherCustomizer() {
      @Override
      public LanguageLevel getImmediateValue(com.intellij.openapi.project.Project project, VirtualFile file) {
        return delegate.getImmediateValue(project, file);
      }
      @Override
      public String getInconsistencyLanguageLevelMessage(String message, LanguageLevel level, PsiFile file) {
        calls.incrementAndGet();
        return delegate.getInconsistencyLanguageLevelMessage(message, level, file);
      }
    }), getTestRootDisposable());
    refresh(LanguageLevel.JDK_1_8, LanguageLevel.JDK_17);
    var record = myFixture.addFileToProject("sample/client/RecordProbe.java", "package sample.client; public record RecordProbe(int value) {}");
    GwtLanguageLevelPusher.updateConfigurationAndPush(getProject(), true);
    PlatformTestUtil.waitWithEventsDispatching("New client record language level was not pushed", () ->
      level(record.getVirtualFile()) == LanguageLevel.JDK_1_8, 30);
    myFixture.openFileInEditor(record.getVirtualFile());
    var descriptions = myFixture.doHighlighting().stream().map(info -> info.getDescription()).filter(java.util.Objects::nonNull).toList();
    assertTrue("Expected native unsupported-feature diagnostic: " + descriptions,
      descriptions.stream().anyMatch(text -> text.contains("not supported") && text.contains("'8'")));
    assertEquals("IDEA 263 modern highlighting does not consume the legacy message callback", 0, calls.get());
    assertFalse(descriptions.stream().anyMatch(text -> text.contains("configured by GWT facet")));
  }

  private LanguageLevel level(VirtualFile file) {
    return PsiResolveHelper.getInstance(getProject()).getEffectiveLanguageLevel(file);
  }

  private void refresh(LanguageLevel expectedClient, LanguageLevel expectedServer) {
    var refresher = GwtSourcePathsRefresher.getInstance(getProject());
    GwtLanguageLevelPusher.updateConfigurationAndPush(getProject(), true);
    PlatformTestUtil.waitWithEventsDispatching("GWT client language levels were not pushed", () ->
      refresher.isAllExecuted() && level(client.getVirtualFile()) == expectedClient &&
      level(superSource.getVirtualFile()) == expectedClient && level(server.getVirtualFile()) == expectedServer, 30);
    IndexingTestUtil.waitUntilIndexesAreReady(getProject());
  }
}
