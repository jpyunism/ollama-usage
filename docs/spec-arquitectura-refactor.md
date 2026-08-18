# Spec: Refactor de arquitectura — repositorio único, DI manual y calidad

## Objective

Reestructurar la arquitectura de ollama-usage para eliminar duplicación de
lógica, bugs de inconsistencia entre caminos de refresco y deudas técnicas
identificadas en la auditoría de 2026-08-17:

1. **Pipeline de refresh duplicado 3 veces y con comportamiento distinto**:
   `UsageWorker` (WorkManager, ≥15 min) ejecuta fetch → widget → notif
   persistente → **alertas de umbral**; `UsageMonitorService` (FGS, <15 min)
   ejecuta fetch → widget → notif persistente → **SIN alertas**; y
   `UsageViewModel.refresh()` ejecuta fetch → widget → histórico (y tampoco
   alertas). Resultado: con frecuencia 1–14 min las alertas de 80%/95% **nunca
   se disparan** — bug de comportamiento según la frecuencia configurada.
2. **`UsageViewModel` god class** (381 LOC): auth, settings, refresh,
   histórico, update-check y flags de navegación mezclados; recibe un
   `context: Context?` nullable solo para `getString()` en el mapeo de errores
   (anti-patrón, no testeable, esconde bugs).
3. **Lógica "qué credencial → qué fetcher" duplicada** en
   `UsageViewModel.refresh()`, `UsageWorker.fetchCurrentUsage()` y
   `hasAuth()`.
4. **Dependencias muertas**: `androidx.security:security-crypto` +
   `error_prone` declaradas pero sin ningún import (SecurePrefs es 100% custom).
5. **Constantes dispersas**: los 15 `KEY_*` viven en el companion del ViewModel
   y los usan 5 clases (Worker, Notifier, Scheduler, App, Widget).
6. **Duraciones de cuota duplicadas**: `SESSION_DURATION`/`WEEK_DURATION` en
   `UsageNotifier`, `UsageWidgetProvider` y `UsageTab` cuando `HistoryPeriod`
   ya las define.
7. **Sin DI**: wiring manual con `new` inline (`OllamaApiUsage()` en 2+
   lugares, `OkHttpClient` nuevo por descarga en `UpdaterService`).
8. **Sin version catalog**: versiones hardcodeadas en `build.gradle.kts`;
   compose-bom 2024.09.03 desactualizado.
9. **i18n parcial**: `formatReset`/`balanceLabel` construyen "resetea en" /
   "resets in" en Kotlin según `locale.language == "es"` en vez de string
   resources (el resto de la app ya es resources-based).
10. **UpdaterService**: `resp.body!!` puede NPE; el `sha256` de `UpdateInfo`
    se descarga pero **nunca se verifica**; `state` es un `MutableStateFlow`
    global en companion.
11. **Race en `refresh()`**: no cancela el job anterior → refrescos rápidos se
    pisan.
12. **Stats tab**: `collectAsStateWithLifecycle().value` inline en el pager en
    vez de hoistear el flow al `UsageScreen` como las otras tabs.
13. **Widget**: guarda datos no-secretos en `SecurePrefs` cifrado → decrypt
    innecesario en el broadcast del main thread.

**Qué se construye:** una capa de dominio única (`UsageRepository` +
`AlertEngine`), DI manual con `AppContainer` (patrón oficial de Android, sin
Hilt — overkill para este tamaño), ViewModel delgado sin `Context`, e
higiene general (keys centralizadas, version catalog, i18n resources).

**Qué es "éxito":** los 3 caminos de refresco (VM, WorkManager, FGS) ejecutan
exactamente el mismo pipeline; el ViewModel no referencia `Context`; todos los
tests verdes; comportamiento observable idéntico salvo el fix del bug de
alertas en FGS.

### Acceptance criteria (testeables)

- [ ] Los 3 caminos de refresco llaman al mismo `UsageRepository.refreshAndPropagate()`.
- [ ] Con FGS (<15 min) las alertas de umbral se disparan (nuevo test).
- [ ] `UsageViewModel` no recibe `Context` (constructor sin contexto).
- [ ] `UiState.Error` transporta un `UsageError` (sealed), no un String armado en el VM.
- [ ] `security-crypto` y `error_prone` fuera de `build.gradle.kts` y el build sigue verde.
- [ ] Los 15 `KEY_*` viven en `PrefsKeys` y ningún otro archivo define keys propias.
- [ ] `SESSION_DURATION`/`WEEK_DURATION` definidas una sola vez (`HistoryPeriod`).
- [ ] Version catalog `libs.versions.toml` activo.
- [ ] `formatReset`/`balanceLabel` reciben templates desde string resources.
- [ ] El updater verifica el `sha256` del APK descargado antes de instalar.
- [ ] `refresh()` cancela el job previo.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes en cada PR.
- [ ] Validación manual en emulador (AVD `test64`) sin crashes/ANRs.

## Requisitos (IDs)

### PR 1 — Base: repositorio único + DI + higiene

- **REQ-001 — `AppContainer` (DI manual).** Clase en
  `di/AppContainer.kt` que construye y expone: `prefs` (SecurePrefs),
  `usageRepository`, `updateRepository`, `httpClient` compartido y
  `versionName`. Se inicializa en `OllamaUsageApp` (lazy, singleton). Ningún
  otro lugar hace `new` de dependencias de infraestructura.
- **REQ-002 — `UsageRepository` único.** Clase que encapsula: resolución de
  auth (cookie vs API key), fetch (scraper/API), y propagación de
  side-effects. Expone `refreshAndPropagate(): Result<UsageData, UsageError>`
  que ejecuta SIEMPRE el pipeline completo: fetch → widget → notif
  persistente → **alertas de umbral** → histórico → `last_updated`.
- **REQ-003 — Fix bug de alertas en FGS.** El pipeline de alertas
  (REQ-002) corre también cuando el refresh lo dispara `UsageMonitorService`
  (frecuencias <15 min). Test: el pipeline llama al notificador de alertas
  independientemente del invocador.
- **REQ-004 — `UsageError` sealed.** `NoAuth`, `CookieExpired`,
  `InvalidApiKey`, `Network(message)`. El mapeo Throwable → UsageError vive en
  el repository; el mapeo UsageError → String vive en la UI con
  `stringResource`.
- **REQ-005 — `AlertEngine` puro.** `nextLevel()` y `checkThreshold()` salen
  de `UsageWorker` a un object puro (misma lógica, misma firma). El worker
  delega en el repository y deja de tener lógica de negocio.
- **REQ-006 — `PrefsKeys` centralizado.** Los 15 `KEY_*` se mueven a un object
  `PrefsKeys`. `UsageViewModel` ya no define keys; todos los consumidores
  (Worker, Notifier, Scheduler, App, Widget) usan `PrefsKeys`.
- **REQ-007 — `HistoryPeriod` como única fuente de duraciones.** Los
  `SESSION_DURATION`/`WEEK_DURATION` privados de `UsageNotifier`,
  `UsageWidgetProvider` y `UsageTab` se reemplazan por
  `HistoryPeriod.SESSION.durationMillis` / `HistoryPeriod.WEEK.durationMillis`.
- **REQ-008 — Quitar `security-crypto` y `error_prone`.** Se eliminan las
  dependencias del `build.gradle.kts` (nada las importa). Build verde.
- **REQ-009 — Version catalog.** Crear `gradle/libs.versions.toml` y migrar
  todas las versiones/deps del `build.gradle.kts` (sin subir versiones — solo
  reubicar, salvo REQ-010).
- **REQ-010 — ViewModel sin Context.** `UsageViewModel` deja de recibir
  `context: Context?`. El mapeo de errores usa `UsageError` (REQ-004), el
  update-check pasa a `UpdateRepository` (recibe context de app, no el VM), y
  `appVersion` llega inyectado. Los tests construyen el VM sin contexto.

### PR 2 — ViewModel delgado + i18n real

- **REQ-011 — `UpdateRepository`.** Clase con `shouldCheck()`, `check()`,
  `markChecked()`, `currentVersion()` envolviendo `UpdateChecker` (que sigue
  siendo object con la lógica pura de parseo). El VM recibe el repository, no
  el Context.
- **REQ-012 — i18n de `formatReset`.** `formatReset` recibe un
  `ResetStrings` (data class con los templates: `resetsSoon`, `resetsIn`,
  `lessThanMin`, `resetsOn`) armado en la UI con `stringResource` (nuevos
  strings en `values` y `values-en`). Los tests pasan templates literales.
- **REQ-013 — i18n de `balanceLabel`.** Ya recibe templates — se unifica el
  patrón (los callers usan `stringResource(R.string.balance_deficit/surplus)`
  como hoy; sin cambio de firma, solo se verifica cobertura).
- **REQ-014 — Hoistear el flow de histórico.** `UsageScreen` recolecta
  `vm.history` y lo pasa a `StatsTab(history)` (hoy lo recolecta inline dentro
  del pager).
- **REQ-015 — `appVersion` inyectada.** `UsageViewModel.appVersion` deja de
  leer el PackageManager vía context; el factory la inyecta desde
  `UpdateRepository.currentVersion()`.

### PR 3 — Calidad y seguridad

- **REQ-016 — Verificación sha256 del APK.** `UpdaterService` descarga a
  archivo temporal, calcula SHA-256 y lo compara con `UpdateInfo.sha256`
  antes de instalar. Mismatch → `DownloadState.Failed("Firma no coincide")`,
  sin crash, sin instalar.
- **REQ-017 — `OkHttpClient` compartido.** `UpdaterService` y `UpdateChecker`
  usan el cliente del `AppContainer` (timeouts 15/15s) en vez de crear uno
  por descarga.
- **REQ-018 — Cancelación de refrescos.** `UsageViewModel.refresh()` cancela
  el job anterior (`refreshJob?.cancel()`) antes de lanzar uno nuevo; el estado
  `Loading` no pisa un refresh en curso.
- **REQ-019 — Widget con prefs claras.** `UsageWidgetProvider.saveData/loadData`
  usan `SharedPreferences` normales (`getSharedPreferences("widget_data")`),
  no `SecurePrefs` (los datos del widget no son secretos; evita decrypt en el
  main thread del broadcast).
- **REQ-020 — `resp.body!!` sin NPE.** `UpdaterService.downloadApk` valida
  `body != null` y `contentLength()` antes de leer; error controlado si no.
- **REQ-021 — Upgrade compose-bom.** Subir `compose-bom` a la última estable
  (2026.x). Si algún símbolo de M3 cambia, ajustar el código. Build + tests
  verdes; validación visual en emulador.

## Non-goals

- NO introducir Hilt/Koin (DI manual es suficiente para este tamaño).
- NO cambiar la UI (comportamiento visual idéntico salvo strings de i18n).
- NO tocar el widget XML (excepción obligatoria del AGENTS.md).
- NO cambiar el modelo de datos persistido (mismas keys, mismos formatos).
- NO añadir Room/DataStore (SharedPreferences sigue siendo suficiente).

## Architecture Decisions

- **DI manual con `AppContainer`** (patrón de la doc oficial de Android):
  una clase raíz que construye el grafo de dependencias, expuesta desde
  `OllamaUsageApp`. Hilt queda descartado: app pequeña, sin navegación
  compleja ni módulos múltiples; el coste de setup y kapt/ksp no compensa.
- **`UsageRepository.refreshAndPropagate()` como único pipeline**: el VM, el
  Worker y el FGS lo invocan. El histórico pasa a registrarse también en
  refrescos de background (hoy solo manual) — consistente con el doc de
  `UsageHistoryStore` ("snapshots en cada refresh exitoso").
- **`UsageError` sealed en vez de String**: los mensajes de error se resuelven
  en la UI (resources con locale). El VM deja de depender de Context y los
  tests pueden verificar el tipo de error exacto.
- **i18n con templates**: `formatReset` recibe un `ResetStrings` con los
  verbos/formatos ya localizados; la decisión de fecha/hora sigue usando
  `locale`. Las funciones puras siguen testeables sin framework.
- **3 PRs apilados con `gh stack`** (práctica obligatoria del workspace para
  features grandes): base → viewmodel/i18n → calidad/seguridad. Cada PR pasa
  tests, lint y build por separado.

## Open Questions

- (resuelta) ¿Hilt? No — AppContainer.
- (resuelta) ¿Room? No — SharedPreferences sigue.
- (resuelta) ¿Upgrade de BOM? Sí, controlado en PR 3 (REQ-021) con validación
  visual; si rompe sin solución rápida, se reporta y se deja para otro PR.
