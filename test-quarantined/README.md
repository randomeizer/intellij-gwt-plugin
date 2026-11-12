# Quarantined tests

These test sources were extracted from the original `intellij.gwt` plugin but are **not compiled**
by the standalone Gradle build. Reasons for their original exclusion include test frameworks and/or
SDKs unavailable on the standalone classpath, plus suites awaiting targeted restoration:

- **JavaEE-web test framework** — `com.intellij.ee.core.javaee.JavaeeTestUtil`,
  `com.intellij.testFramework.builders.WebModuleFixtureBuilder`
  (base classes `GwtFixtureTestCase` → `GwtCodeInsightTestCase` and all their subclasses).
- **JavaScript test framework** — `com.intellij.lang.javascript.Base*/…TestCase`,
  `com.intellij.javascript.testFramework.*`, `org.intellij.idea.lang.javascript.intention.*`.
- **Spring test framework** — `com.intellij.spring.testFramework.*`.
- **Grazie / spellchecker test support** — `com.intellij.grazie.*`.
- **Artifact-compiler test base** — `com.intellij.compiler.artifacts.ArtifactCompilerTestCase`.
- **GWT SDK** — `com.google.gwt.dev.*` (requires a real GWT distribution on the test classpath).
- **Legacy Maven importer suite** — `GwtLtgtFacetImporterTest.kt` remains excluded pending its own
  dependency and version-matrix validation. The 263.7307.200 Maven framework now includes `getFacet`
  and `waitForImportWithinTimeout`; the active `GwtCodehausSourcesImportTest.kt` uses the published
  Maven fixtures. This availability does not automatically restore the legacy suite.

The tests that only need the publicly available Platform / Java / Maven test frameworks remain under
`../test/` and are compiled by the build.

To revive any of these, move the file back under `../test/` (preserving its package path) and add the
required test-framework dependency in `../build.gradle.kts`. If the needed framework is not published
for standalone consumption, the test must stay here.

## Quarantined files
- org/randombits/intellij/gwt/actions/GenerateUiHandlerMethodHandlerTest.java
- org/randombits/intellij/gwt/css/GwtCssCompletionTest.java
- org/randombits/intellij/gwt/css/GwtCssFindUsagesTest.java
- org/randombits/intellij/gwt/css/GwtCssInspectionTest.java
- org/randombits/intellij/gwt/css/GwtCssTypingTest.java
- org/randombits/intellij/gwt/html/CssClassReferencesTest.java
- org/randombits/intellij/gwt/html/HtmlIdRefactoringTest.java
- org/randombits/intellij/gwt/injected/GwtInjectedLanguageHighlightingTest.java
- org/randombits/intellij/gwt/injected/GwtJsEditingTest.java
- org/randombits/intellij/gwt/injected/GwtJsEditorTest.java
- org/randombits/intellij/gwt/injected/GwtJsIntentionTest.java
- org/randombits/intellij/gwt/injected/GwtJsIntroduceVariableTest.java
- org/randombits/intellij/gwt/inspections/GwtAllowedClientCodeInspectionsTest.java
- org/randombits/intellij/gwt/inspections/GwtClientBundleHighlightingTest.java
- org/randombits/intellij/gwt/inspections/GwtI18nInspectionTest.java
- org/randombits/intellij/gwt/inspections/GwtReferencesTest.java
- org/randombits/intellij/gwt/inspections/GwtRpcInspectionsTest.java
- org/randombits/intellij/gwt/inspections/GwtSerializableTest.java
- org/randombits/intellij/gwt/inspections/GwtToCssClassReferencesInspectionTest.java
- org/randombits/intellij/gwt/inspections/GwtToHtmlTagReferencesInspectionTest.java
- org/randombits/intellij/gwt/inspections/GwtUiBinderInspectionsTest.java
- org/randombits/intellij/gwt/intentions/CreateUiFieldIntentionTest.java
- org/randombits/intellij/gwt/intentions/CreateUiHandlerIntentionTest.java
- org/randombits/intellij/gwt/intentions/GwtIntentionTestBase.java
- org/randombits/intellij/gwt/javascript/GwtJavaScriptParsingTest.java
- org/randombits/intellij/gwt/jsni/CompleteJsniMethodBodyTest.java
- org/randombits/intellij/gwt/jsni/CompletionInInjectedTest.java
- org/randombits/intellij/gwt/jsni/GwtJSCompletionTest.java
- org/randombits/intellij/gwt/jsni/GwtJavaFromJSMethodCallsInspectionTest.java
- org/randombits/intellij/gwt/jsni/GwtJsLiveTemplateTest.java
- org/randombits/intellij/gwt/jsni/GwtJsniInspectionTest.java
- org/randombits/intellij/gwt/jsni/JsniRefactoringTest.java
- org/randombits/intellij/gwt/maven/GwtLtgtFacetImporterTest.kt
- org/randombits/intellij/gwt/model/GwtFacetConfigurationTest.java
- org/randombits/intellij/gwt/model/GwtFacetTest.java
- org/randombits/intellij/gwt/model/GwtHtmlUtilTest.java
- org/randombits/intellij/gwt/model/GwtLanguageLevelPusherTest.java
- org/randombits/intellij/gwt/model/GwtLanguageLevelPusherWithDefaultClientDirectoryTest.java
- org/randombits/intellij/gwt/navigation/GwtAsyncServiceNavigationTest.java
- org/randombits/intellij/gwt/references/ClientBundleReferencesTest.java
- org/randombits/intellij/gwt/references/GwtCodeInsightTestCase.java
- org/randombits/intellij/gwt/references/GwtFixtureTestCase.java
- org/randombits/intellij/gwt/references/GwtModuleInHtmlReferencesTest.java
- org/randombits/intellij/gwt/references/GwtRenamePackageTest.java
- org/randombits/intellij/gwt/references/GwtSpringXmlHighlightingTest.java
- org/randombits/intellij/gwt/references/GwtTagNameReferenceTest.java
- org/randombits/intellij/gwt/references/GwtUiBinderFindUsagesTest.java
- org/randombits/intellij/gwt/references/GwtUiBinderHighlightingTest.java
- org/randombits/intellij/gwt/references/ImageResourceHighlightingTest.java
- org/randombits/intellij/gwt/references/UiBinderCssReferencesTest.java
- org/randombits/intellij/gwt/references/UiBinderReferencesTest.java
- org/randombits/intellij/gwt/references/UiRendererXmlFileCorrespondenceInUiBinderMappingServiceTest.java
- org/randombits/intellij/gwt/references/UiWithTagReferencesTest.java
- org/randombits/intellij/gwt/run/remoteUi/GwtErrorFilterTest.java
- org/randombits/intellij/gwt/uiBinder/GwtUiBinderCompletionTest.java
- org/randombits/intellij/gwt/uiBinder/GwtUiXmlCompletionTest.java
