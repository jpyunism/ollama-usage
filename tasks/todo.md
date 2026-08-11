# Todo: Modo claro/oscuro

- [ ] Task 1: AppDarkMode + resolución + ViewModel
  - Acceptance: `resolveDarkMode` resuelve System/Light/Dark; VM expone `darkMode`
    StateFlow, `updateDarkMode` persiste `KEY_DARK_MODE`, default System, inválido cae a System
  - Verify: `./gradlew testDebugUnitTest` (AppDarkModeTest + UsageViewModelTest)
  - Files: `app/src/main/java/com/jpyunism/ollamacloudusage/AppDarkMode.kt`,
    `UsageViewModel.kt`, `app/src/test/java/.../AppDarkModeTest.kt`, `UsageViewModelTest.kt`
- [ ] Task 2: Theme + MainActivity + SettingsTab UI + strings
  - Acceptance: `OllamaUsageTheme(theme, darkMode)` resuelve darkTheme; MainActivity
    pasa el estado; SettingsTab muestra Card con chips Sistema/Claro/Oscuro en
    Apariencia, arriba de los temas de color; strings es/en agregados
  - Verify: `./gradlew testDebugUnitTest lintDebug`
  - Files: `ui/Theme.kt`, `MainActivity.kt`, `ui/SettingsTab.kt`,
    `res/values/strings.xml`, `res/values-en/strings.xml`
- [ ] Task 3: values-night/themes.xml
  - Acceptance: tema de ventana oscuro en modo noche (parent Material.NoActionBar)
  - Verify: build + inspección del resource
  - Files: `app/src/main/res/values-night/themes.xml` (nuevo)
- [ ] Task 4: Release v0.16.0
  - Acceptance: versionCode 23, versionName 0.16.0, suite verde, tag v0.16.0,
    GitHub release con APK firmado, APK enviado por Telegram (15710279)
  - Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease`
  - Files: `app/build.gradle.kts`
