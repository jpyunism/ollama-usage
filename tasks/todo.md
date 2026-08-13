# Tasks: Gráfico de barras de consumo por período (Stats)

## Task 1: periodBars() + PeriodBar en UsageHistory.kt + tests (TDD)

- [ ] `PeriodBar(start, end, peakPercent, inProgress)` data class
- [ ] `periodBars(snapshots, period, resetAnchor, now, selector)`: una barra por
      período con datos, orden cronológico, períodos vacíos omitidos
- [ ] `peakPercent` = pico (máx %) del período; `inProgress = end > now`
- Verify: `./gradlew testDebugUnitTest --tests "*UsageHistoryTest*"` (verde)
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/UsageHistory.kt`,
  `app/src/test/java/com/jpyunism/ollamacloudusage/UsageHistoryTest.kt`

## Task 2: Card "Consumo por período" en ui/StatsTab.kt (bar chart Canvas)

- [ ] Card nueva debajo de la card del gráfico de línea (mismo toggle Semana/Sesión)
- [ ] Barras Canvas: eje Y 0/50/100, fecha en X, % encima de cada barra,
      esquinas superiores redondeadas, color primario
- [ ] Período actual: alpha 0.45 + etiqueta "en curso"
- [ ] Labels: n > 8 → solo fecha primero/último; n > 14 → además sin %;
      ancho mínimo de barra 4.dp
- Verify: `./gradlew testDebugUnitTest lintDebug` (verde) + inspección visual
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/StatsTab.kt`

## Task 3: Strings es/en

- [ ] Título card "Consumo por período" / "Usage per period"
- [ ] "En curso" / "In progress"
- [ ] contentDescription del gráfico
- Verify: lint (hardcoded strings) limpio
- Files: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

## Task 4: Release v0.19.0

- [ ] Bump versionCode 26, versionName 0.19.0
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` (verde)
- [ ] Commit + push + tag v0.19.0
- [ ] GitHub release con APK firmado
- [ ] APK por Telegram (15710279)
- Files: `app/build.gradle.kts`
