# Tasks: Refactor de arquitectura (PR1 base · PR2 viewmodel/i18n · PR3 calidad)

## PR 1 — Base (REQ-001..010)

### T1.1 RED — `UsageError` sealed + `AlertEngine` (REQ-004, REQ-005)

- [ ] Crear `app/src/test/.../UsageErrorTest.kt`: construye los 4 tipos
      (`NoAuth`, `CookieExpired`, `InvalidApiKey`, `Network`) y verifica
      `fromThrowable(CookieExpiredException()) == CookieExpired`, idem API key,
      y `RuntimeException("x") == Network("x")` (mapeo del repository).
- [ ] Crear `app/src/test/.../AlertEngineTest.kt`: mover los casos actuales de
      `UsageWorkerTest` (`nextLevel`) a `AlertEngine.nextLevel` + tests de
      `checkThreshold` con prefs mock (notifica ALERT, CRITICAL, no repite,
      resetea).
- Acceptance: referencian `UsageError`/`AlertEngine` (aún inexistentes).
- Verify: `./gradlew testDebugUnitTest` → **RED** (no compila).
- Files: `app/src/test/.../UsageErrorTest.kt`, `AlertEngineTest.kt`

### T1.2 GREEN — `UsageError` + `AlertEngine` + `PrefsKeys` (REQ-004..006)

- [ ] `core/UsageError.kt`: sealed interface con los 4 tipos + companion
      `fromThrowable` (mapea CookieExpired/InvalidApiKey/otros→Network).
- [ ] `core/AlertEngine.kt`: object puro con `nextLevel()` (idéntica lógica a
      la actual) y `checkThreshold(prefs, percent, alert, critical, lastKey,
      notify)` (idéntica).
- [ ] `core/PrefsKeys.kt`: mover los 15 `KEY_*` + `DEFAULT_REFRESH_MINUTES`
      desde `UsageViewModel.companion`; actualizar todos los consumidores
      (Worker, Notifier, Scheduler, App, MainActivity, Widget, ViewModel).
- Acceptance: T1.1 verde; `grep -rn "KEY_"` no muestra keys definidas fuera
  de `PrefsKeys`.
- Verify: `./gradlew testDebugUnitTest` → **GREEN**.
- Files: `core/UsageError.kt`, `core/AlertEngine.kt`, `core/PrefsKeys.kt` + edición de consumidores.

### T1.3 RED — `UsageRepository.refreshAndPropagate` (REQ-002, REQ-003)

- [ ] Crear `app/src/test/.../UsageRepositoryTest.kt` con mockk:
  - `fetch OK` → guarda widget (verify `UsageWidgetProvider.saveData`),
    notif persistente (verify), **notifica alertas si cruzó umbral** (verify
    `UsageNotifier.notifyLimit`) — el caso FGS de REQ-003.
  - `fetch OK bajo umbral` → no notifica alertas.
  - `CookieExpired` → `UsageError.CookieExpired` (sin crash).
  - Registra histórico (`historyStore.record` llamado) y `last_updated`.
- Acceptance: referencian `UsageRepository.refreshAndPropagate` (inexistente).
- Verify: `./gradlew testDebugUnitTest` → **RED**.
- Files: `app/src/test/.../UsageRepositoryTest.kt`

### T1.4 GREEN — `UsageRepository` (REQ-001..003)

- [ ] `data/UsageRepository.kt`: recibe `prefs`, `scraper`, `apiScraper`,
      `historyStore`, `httpClient` (por ahora no usado), `context` (app) y
      `now`. `refreshAndPropagate(): Result<UsageData, UsageError>`:
      resuelve auth (PrefsKeys) → fetch en Dispatchers.IO → pipeline completo
      (widget, notif persistente, alertas vía `AlertEngine`, histórico,
      last_updated) → `Success(data)`. `hasAuth()` aquí.
- [ ] `di/AppContainer.kt`: expone `prefs`, `repository`, `httpClient`
      (OkHttp 15/15s compartido) y `versionName`; `OllamaUsageApp` lo
      inicializa (lazy singleton).
- Acceptance: T1.3 verde; el pipeline corre idéntico para VM/Worker/FGS.
- Verify: `./gradlew testDebugUnitTest` → **GREEN**.
- Files: `data/UsageRepository.kt`, `di/AppContainer.kt`, `OllamaUsageApp.kt`

### T1.5 Refactor — Worker + FGS + ViewModel usan el repository (REQ-002)

- [ ] `UsageWorker.doWork`: delega en `repository.refreshAndPropagate()`;
      `Result.retry()` ante `Network`/fallo; `Result.success()` en
      `CookieExpired`/`InvalidApiKey` (sin martillar); update-check se queda
      (ver PR2). Eliminar `fetchCurrentUsage`, `checkThreshold`, `nextLevel`
      y `KEY_LAST_NOTIFIED_*` → `PrefsKeys` + `AlertEngine`.
- [ ] `UsageMonitorService.refreshOnce`: usa el repository (con alertas —
      REQ-003). El `updateCheck` del FGS queda igual (no existía).
- [ ] `UsageViewModel.refresh()`: delega en el repository; `UiState.Success/
      Error` se arman desde `Result<UsageData, UsageError>`; el histórico y el
      widget ya no se tocan aquí (los hace el pipeline).
- Acceptance: `UsageWorker` y `UsageMonitorService` no contienen lógica de
  umbrales ni keys; los 3 caminos llaman al mismo método.
- Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease` → verdes.
- Files: `UsageWorker.kt`, `UsageMonitorService.kt`, `UsageViewModel.kt`

### T1.6 GREEN — ViewModel sin Context (REQ-010)

- [ ] Quitar `context: Context?` del constructor de `UsageViewModel`; el
      mapeo de errores usa `UsageError` (los strings se resuelven en la UI:
      `UsageTab`/`CookieSetup` con `stringResource` según tipo).
- [ ] `updateLanguage` deja de llamar `LocaleHelper.apply` vía context del VM:
      se mueve a `MainActivity`/`UsageScreen` (callback `onLanguageChange`) o
      el VM expone el cambio y el Activity aplica (decisión: callback en
      `UsageScreen` → `LocaleHelper.apply(context, lang)`).
- [ ] `checkForUpdate`/`checkForUpdateNow` dejan de usar `context` del VM:
      reciben un `UpdateChecker`-wrapper inyectado (ver T2.1, puede ser
      placeholder mínimo en PR1 para compilar: `updateChecker: UpdateCheckerApi`
      con default real).
- [ ] Actualizar `UsageViewModelTest.buildVm` (sin contexto) + tests de
      `checkForUpdateNow` (inyectar checker fake que devuelve
      Available/UpToDate).
- Acceptance: `UsageViewModel` no importa `android.content.Context`.
- Verify: `./gradlew testDebugUnitTest` → **GREEN**.
- Files: `UsageViewModel.kt`, `UsageScreen.kt`, `UsageTab.kt`, `CookieSetup.kt`, tests.

### T1.7 — Duraciones + build hygiene (REQ-007..009)

- [ ] `UsageNotifier`, `UsageWidgetProvider`, `UsageTab`: usar
      `HistoryPeriod.SESSION/WEEK.durationMillis` (borrar los `Duration` privados).
- [ ] Quitar `security-crypto` y `error_prone` de `build.gradle.kts`.
- [ ] Crear `gradle/libs.versions.toml` con todas las versiones actuales y
      migrar `build.gradle.kts` (sin cambiar versiones).
- Acceptance: `grep -rn "SESSION_DURATION"` solo en `HistoryPeriod`;
  build verde con catalog; deps eliminadas no aparecen en `./gradlew :app:dependencies`.
- Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease` → verdes.
- Files: `build.gradle.kts`, `gradle/libs.versions.toml`, `UsageNotifier.kt`,
  `UsageWidgetProvider.kt`, `UsageTab.kt`

### T1.8 — PR1 listo

- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes.
- [ ] Validación emulador (AGENTS.md): flujo básico + settings persisten.
- [ ] Commit + `gh stack` → PR1.

## PR 2 — ViewModel delgado + i18n (REQ-011..015)

### T2.1 — `UpdateRepository` (REQ-011)

- [ ] `data/UpdateRepository.kt`: envuelve `UpdateChecker` (shouldCheck,
      check, markChecked, currentVersion) con `context` de app inyectado.
- [ ] `UsageViewModel` recibe `updateRepository: UpdateRepository` (default
      real); `checkForUpdate*` lo usan.
- [ ] `appVersion` se inyecta desde `updateRepository.currentVersion()`.
- Acceptance: `grep -rn "UpdateChecker" app/src/main` solo en
  `UpdateRepository` (+ tests).
- Verify: tests verdes.

### T2.2 — i18n `formatReset` con templates (REQ-012)

- [ ] `core/ResetStrings.kt`: data class con `resetsSoon`, `resetsIn`,
      `lessThanMin`, `resetsOn` (templates con %s).
- [ ] `formatReset(resetAt, mode, strings, now, zone, locale)` — misma lógica,
      textos desde templates. Nuevos strings en `values/strings.xml` y
      `values-en/strings.xml` (`reset_soon`, `reset_in`, `reset_less_than_min`,
      `reset_on` con placeholders).
- [ ] Callers (UsageTab, UsageNotifier, UsageWidgetProvider) arman
      `ResetStrings` con `stringResource`.
- [ ] Reescribir `ResetFormatTest` con templates literales (es/en).
- Acceptance: `grep -rn "resetea\|resets in" app/src/main --include=*.kt`
  vacío; tests verdes.
- Verify: `./gradlew testDebugUnitTest` → GREEN.

### T2.3 — Hoist histórico + appVersion (REQ-014, REQ-015)

- [ ] `UsageScreen` recolecta `vm.history` y lo pasa a `StatsTab(history)`
      (quitar el `collectAsStateWithLifecycle` inline del pager).
- [ ] `appVersion` del VM se lee de `updateRepository` (inyectado); `SettingsTab`
      sigue leyendo `vm.appVersion`.
- Acceptance: un solo `collectAsStateWithLifecycle` de history; `appVersion`
  funciona sin Context en el VM.
- Verify: tests + build verdes.

### T2.4 — PR2 listo

- [ ] Tests/lint/build verdes; emulador (cambio de idioma → strings nuevos
      visibles en countdown).
- [ ] `gh stack` → PR2.

## PR 3 — Calidad/seguridad (REQ-016..021)

### T3.1 — sha256 del APK (REQ-016)

- [ ] `UpdaterService.downloadApk` calcula SHA-256 del archivo descargado
      (`MessageDigest`); si `UpdateInfo.sha256` no es null y no coincide →
      `DownloadState.Failed` sin instalar. `startUpdateDownload` pasa el
      `sha256` al servicio (extra).
- [ ] Test puro: función `verifySha256(file, expected)` (true/false) en
      `UpdaterService` companion o `UpdateVerifier` object; casos OK/mismatch/
      null.
- Acceptance: test de mismatch no instala; build verde.
- Verify: `./gradlew testDebugUnitTest` → GREEN.

### T3.2 — OkHttp compartido + NPE (REQ-017, REQ-020)

- [ ] `UpdaterService` y `UpdateChecker` reciben el `httpClient` del
      `AppContainer` (default por constructor para tests).
- [ ] `downloadApk`: validar `resp.body != null` y contentLength antes de
      leer; error controlado (`DownloadState.Failed`) en vez de NPE.
- Acceptance: un solo `OkHttpClient` en el grafo; sin `!!` sobre body.
- Verify: tests + build verdes.

### T3.3 — Race refresh + widget prefs claras (REQ-018, REQ-019)

- [ ] `UsageViewModel.refresh()`: `refreshJob?.cancel()` antes de lanzar;
      guardar el Job.
- [ ] `UsageWidgetProvider.saveData/loadData`: usar
      `context.getSharedPreferences("widget_data", MODE_PRIVATE)` (claras).
      Migración: leer legacy de SecurePrefs una vez y re-guardar (best-effort).
- Acceptance: refrescos rápidos no pisan; widget sin decrypt en main thread.
- Verify: tests + build verdes.

### T3.4 — Upgrade compose-bom (REQ-021)

- [ ] Subir `compose-bom` a última estable en `libs.versions.toml`.
- [ ] Ajustar símbolos si compila roto; tests + lint + build.
- [ ] Emulador: verificar visualmente las 3 tabs + settings + stats.
- Acceptance: build verde con BOM nuevo; sin cambios visuales.
- Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease` + emulador.

### T3.5 — PR3 listo

- [ ] Tests/lint/build verdes; emulador completo (AGENTS.md).
- [ ] `gh stack` → PR3; merge de la pila; release v0.26.0 con APK + screenshots.
