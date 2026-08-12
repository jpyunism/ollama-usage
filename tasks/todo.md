# Todo: Balanza de consumo (déficit/superávit)

- [x] Task 1: Balance.kt + BalanceTest.kt
  - Acceptance: `computeBalance(percent, resetAt, now, duration)` devuelve
    DEFICIT/SURPLUS/null; delta redondeado 0 → null; fuera de rango → null;
    `balanceLabel` formatea "Déficit 8%" / "Superávit 5%"
  - Verify: `./gradlew testDebugUnitTest --tests "*BalanceTest*"` (verde)
  - Files: `app/src/main/java/com/jpyunism/ollamacloudusage/Balance.kt` (nuevo),
    `app/src/test/java/com/jpyunism/ollamacloudusage/BalanceTest.kt` (nuevo)
- [x] Task 2: UI UsageTab (Row reset + balanza)
  - Acceptance: en cards Sesión y Semana, el texto de reset se muestra en una Row
    con la balanza al lado (déficit = error, superávit = primary); strings es/en
  - Verify: `./gradlew testDebugUnitTest lintDebug` (verde)
  - Files: `ui/UsageTab.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`
- [x] Task 3: Notificación persistente (UsageNotifier)
  - Acceptance: tras la línea de reset de semana y de sesión se agrega
    " · Déficit 8%" / " · Superávit 5%"; strings es/en
  - Verify: `./gradlew testDebugUnitTest lintDebug` (verde)
  - Files: `UsageNotifier.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`
- [x] Task 4: Widget (UsageWidgetProvider)
  - Acceptance: `widget_session_reset` muestra "<reset> · Déficit 8%"; persistencia
    de datos ya incluye los resets (no cambia); strings widget es/en
  - Verify: build + inspección visual del widget
  - Files: `UsageWidgetProvider.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`
- [ ] Task 5: Release v0.17.0
  - Acceptance: versionCode 24, versionName 0.17.0, suite verde, tag v0.17.0,
    GitHub release con APK firmado, APK por Telegram (15710279)
  - Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease`
  - Files: `app/build.gradle.kts`
