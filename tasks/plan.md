# Plan: Orden y agrupación de modelos en la vista de consumo

## Overview

En las cards de consumo (Sesión y Semana): ordenar modelos por % desc (barra y
lista) y agrupar los de bajo % (< 3%) en un segmento "Otros" con color neutro
para que no se vean como líneas de 1px.

## Architecture Decisions

- **Función pura `groupModels(models, threshold = 3.0, othersLabel)`** en
  `UsageData.kt` (junto a `formatPercent`): entrada `List<ModelUsage>`, salida
  `List<UsageSegment>` ya ordenada. Sin estado, testeable sin Android. El label
  "Otros" se pasa desde la UI (stringResource) para i18n.
- **`sortedByUsage(models)`**: orden desc por % (tiebreak requests desc) —
  reutilizada por la lista (que muestra TODOS los modelos individuales).
- **`UsageSegment(label, percent, modelCount, colorKey)`**: `colorKey` = nombre
  del modelo para la paleta; `null` para "Otros" (color neutro del tema).
- **Umbral 3%** (aprobado): modelos < 3% se agrupan solo si son ≥ 2; si es uno
  solo se mantiene individual; si todos < umbral no se agrupa (la barra
  conserva todos los colores).
- **"Otros" siempre al final** (por construcción: es la suma de los menores).
- **Barra**: `LinearUsageBar` consume `groupModels`; contenedor con
  `clip(RoundedCornerShape(6.dp))` y segmentos sin shape → uniones continuas,
  solo bordes exteriores curvos (aprobado). "Otros" con `outlineVariant`.
- **Lista**: consume `sortedByUsage` — detalle individual de TODOS los modelos
  (aprobado: sin fila "Otros" en la lista).
- **Strings es/en**: "Otros" / "Others" (solo para la barra).
- **Sin cambios** en widget ni notificación (boundary del spec).

## Task List

- [ ] Task 1: `UsageSegment` + `groupModels()` + `sortedByUsage()` en
      `UsageData.kt` + tests TDD
  - Orden desc por % (tiebreak requests desc)
  - Agrupación < 3% (≥2 → "Otros"; 1 → individual; todos → sin agrupar)
  - Suma y modelCount de "Otros" correctos; "Otros" al final; label custom
  - Verify: `./gradlew testDebugUnitTest --tests "*GroupModelsTest*"` (verde)
  - Files: `UsageData.kt`, `GroupModelsTest.kt`
- [ ] Task 2: UI en `UsageTab.kt` — barra usa `groupModels`, lista usa
      `sortedByUsage`
  - Barra: segmentos ordenados, "Otros" con color neutro, uniones continuas
    (clip en contenedor, sin shape por segmento)
  - Lista: mismo orden, detalle individual de todos los modelos
  - Verify: `./gradlew testDebugUnitTest lintDebug` (verde) + inspección visual
  - Files: `ui/UsageTab.kt`
- [ ] Task 3: Strings es/en ("Otros")
  - Files: `res/values/strings.xml`, `res/values-en/strings.xml`
- [ ] Task 4: Release v0.21.0 (bump versionCode 28, tests+lint+assemble, tag,
      GitHub release, APK por Telegram)

### Checkpoint: Tasks 1-3
- [ ] Tests de `groupModels` verdes
- [ ] Barra y lista ordenadas mayor → menor %
- [ ] "Otros" visible con color neutro, uniones continuas, sin líneas de 1px
