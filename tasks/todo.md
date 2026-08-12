# Tasks: Estadísticas de uso histórico

## Task 1: UsageHistory.kt + UsageHistoryTest.kt (TDD)

- [x] Agrupación por período (semana 168 h / sesión 24 h) desde el primer snapshot
- [x] Pico (máx %) por período → "cuánto se consumió antes del reset"
- [x] Reset markers dentro del rango (para líneas punteadas)
- [x] nearestSnapshot(ts) para el tooltip
- Verify: `./gradlew testDebugUnitTest --tests "*UsageHistoryTest*"` (verde)
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/UsageHistory.kt` (nuevo),
  `app/src/test/java/com/jpyunism/ollamacloudusage/UsageHistoryTest.kt` (nuevo)

## Task 2: UsageHistoryStore.kt (persistencia) + tests

- [x] JSON en SecurePrefs: load/save
- [x] Dedupe: % idéntico al anterior y < 15 min → se omite
- [x] Límite 600 snapshots FIFO
- Verify: `./gradlew testDebugUnitTest --tests "*UsageHistoryStoreTest*"` (verde)
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/UsageHistoryStore.kt` (nuevo),
  `app/src/test/java/com/jpyunism/ollamacloudusage/UsageHistoryStoreTest.kt` (nuevo)

## Task 3: Integración en UsageViewModel

- [x] En `refresh()` exitoso: `historyStore.record(...)` con los % del fetch
- [x] StateFlow `history` expuesto (lista de snapshots + weeklyResetAt)
- Verify: refresh manual guarda snapshot (log/inspección)
- Files: `UsageViewModel.kt`

## Task 4: ui/StatsTab.kt + strings es/en

- [x] Toggle Semana/Sesión (SingleChoiceSegmentedButtonRow M3)
- [x] Gráfico Canvas: línea + área gradiente, eje Y 0/50/100, labels fecha en X
- [x] Líneas punteadas de reset (diente de sierra)
- [x] Tooltip al tocar: burbuja con fecha + % del punto más cercano
- [x] Resumen: último período cerrado + período actual en curso
- [x] Estado vacío con hint
- Verify: `./gradlew testDebugUnitTest lintDebug` (verde) + inspección visual
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/StatsTab.kt` (nuevo),
  `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

## Task 5: Tab en UsageScreen.kt

- [x] Nueva tab en NavigationBar (Usage / Estadísticas / Settings) con icono BarChart
- Verify: navegación entre las 3 tabs
- Files: `ui/UsageScreen.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`

## Task 6: Release v0.18.0

- [ ] Bump versionCode 25, versionName 0.18.0
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` (verde)
- [ ] Commit + push + tag v0.18.0
- [ ] GitHub release con APK firmado
- [ ] APK por Telegram (15710279)
- Files: `app/build.gradle.kts`
