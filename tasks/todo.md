# Tasks: Orden y agrupación de modelos en la vista de consumo

## Task 1: UsageSegment + groupModels() + sortedByUsage() en UsageData.kt + tests (TDD)

- [x] `UsageSegment(label, percent, modelCount, colorKey)` data class
- [x] `sortedByUsage(models)`: orden desc por % (tiebreak requests desc)
- [x] `groupModels(models, threshold = 3.0, othersLabel)`: modelos < umbral →
      ≥2 → "Otros" (suma %, modelCount=N, colorKey=null); 1 → individual
- [x] Todos < umbral → sin agrupar
- [x] "Otros" siempre al final
- Verify: `./gradlew testDebugUnitTest --tests "*GroupModelsTest*"` (verde)
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/UsageData.kt`,
  `app/src/test/java/com/jpyunism/ollamacloudusage/GroupModelsTest.kt`

## Task 2: UI en UsageTab.kt — barra usa groupModels, lista usa sortedByUsage

- [x] Barra: segmentos ordenados mayor → menor, "Otros" con color neutro
      (outlineVariant), uniones continuas (clip en contenedor, sin shape por
      segmento)
- [x] Lista: mismo orden, detalle individual de TODOS los modelos
- Verify: `./gradlew testDebugUnitTest lintDebug` (verde) + inspección visual
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageTab.kt`

## Task 3: Strings es/en

- [x] "Otros" / "Others" (label barra + contentDescription)
- Verify: lint (hardcoded strings) limpio
- Files: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml`

## Task 4: Release v0.21.0

- [x] Bump versionCode 28 + versionName 0.21.0
- [x] `./gradlew testDebugUnitTest lintDebug assembleRelease` (JAVA_HOME + ANDROID_HOME)
- [x] Commit + push + tag v0.21.0
- [x] GitHub release con APK firmado + notas
- [x] APK por Telegram (chat 15710279)
