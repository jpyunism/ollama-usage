# Tasks: Estadísticas de uso histórico

## Task 1: UsageHistory.kt + UsageHistoryTest.kt (TDD)

- [ ] Agrupación por período (semana 168 h / sesión 24 h) desde el primer snapshot
- [ ] Pico (máx %) por período → "cuánto se consumió antes del reset"
- [ ] Reset markers dentro del rango (para líneas punteadas)
- [ ] nearestSnapshot(ts) para el tooltip
- Verify: `./gradlew testDebugUnitTest --tests "*UsageHistoryTest*"`
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/UsageHistory.kt` (nuevo),
  `app/src/test/java/com/jpyunism/ollamacloudusage/UsageHistoryTest.kt` (nuevo)

## Task 2: UsageHistoryStore.kt (persistencia) + tests

- [ ] JSON en SecurePrefs: load/save
- [ ] Dedupe: % idéntico al anterior y < 15 min → se omite
- [ ] Límite 600 snapshots FIFO
- Verify: `./gradlew testDebugUnitTest --tests "*UsageHistoryStoreTest*"`
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/UsageHistoryStore.kt` (nuevo),
  `app/src/test/java/com/jpyunism/ollamacloudusage/UsageHistoryStoreTest.kt` (nuevo)

## Task 3: Integración en UsageViewModel

- [ ] En `refresh()` exitoso: `historyStore.record(...)` con los % del fetch
- [ ] StateFlow `history` expuesto (lista de snapshots)
- Verify: refresh manual guarda snapshot (log/inspección)
- Files: `UsageViewModel.kt`

## Task 4: ui/StatsTab.kt + strings es/en

- [ ] Toggle Semana/Sesión (SingleChoiceSegmentedButtonRow M3)
- [ ] Gráfico Canvas: línea + área gradiente, eje Y 0/50/100, labels fecha en X
- [ ] Líneas punteadas de reset (diente de sierra)
- [ ] Tooltip al tocar: burbuja con fecha + % del punto más cercano
- [ ] Resumen: último período cerrado + período actual en curso
- [ ] Estado vacío con hint
- Verify: `./gradlew testDebugUnitTest lintDebug` + inspección visual
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/StatsTab.kt` (nuevo),
  `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

## Task 5: Tab en UsageScreen.kt

- [ ] Nueva tab en NavigationBar (Usage / Estadísticas / Settings) con icono BarChart
- Verify: navegación entre las 3 tabs
- Files: `ui/UsageScreen.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`

## Task 6: Release v0.18.0

- [ ] Bump versionCode 25, versionName 0.18.0
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` (verde)
- [ ] Commit + push + tag v0.18.0
- [ ] GitHub release con APK firmado
- [ ] APK por Telegram (15710279)
- Files: `app/build.gradle.kts`
