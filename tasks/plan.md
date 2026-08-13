# Implementation Plan: Gráfico de barras de consumo por período (Stats)

## Overview

Nueva card **"Consumo por período"** en la tab Estadísticas: un gráfico de
barras (Compose Canvas) con **una barra por semana/sesión** (toggle existente)
que muestra el **% de consumo antes del reset** (pico del período). El período
actual en curso se incluye como barra distinguible ("en curso"). Se agrega
debajo del gráfico de línea existente, sin reemplazarlo.

## Architecture Decisions

- **Sin datos nuevos**: reutiliza los snapshots acumulados y la agrupación
  existente (`periodsFor` + `peakPercent`). Solo se agrega una función pura
  `periodBars()` que mapea períodos → barras.
- **`PeriodBar(start, end, peakPercent, inProgress)`**: `peakPercent` = pico
  (máx %) del período (decisión usuario); `inProgress = end > now` (decisión
  usuario: incluir período actual).
- **Gráfico**: Compose `Canvas` (sin librerías — YAGNI). Eje Y 0/50/100 con
  grid (mismo patrón que `UsageChart`), labels de fecha en X, barras con
  esquinas superiores redondeadas, % encima de cada barra, barra "en curso"
  con alpha 0.45 + etiqueta.
- **Labels con muchos períodos**: `n > 8` → solo fecha primero/último;
  `n > 14` → además se omiten los % (evita solapamiento). Ancho mínimo de
  barra 4.dp.
- **UI**: card nueva debajo de la card del gráfico de línea, mismo toggle
  Semana/Sesión (el `period` state ya vive en `StatsTab`).
- **Strings**: es/en para título de card, "en curso" y contentDescription.
- **Duración**: sesión 24 h, semana 168 h (mismas constantes que Balance).

## Task List

- [ ] Task 1: `periodBars()` + `PeriodBar` en `UsageHistory.kt` + tests TDD
  - Una barra por período con datos, orden cronológico
  - Pico correcto, `inProgress` correcto, períodos vacíos omitidos
  - Verify: `./gradlew testDebugUnitTest --tests "*UsageHistoryTest*"` (verde)
  - Files: `UsageHistory.kt`, `UsageHistoryTest.kt`
- [ ] Task 2: Card "Consumo por período" en `ui/StatsTab.kt` (bar chart Canvas)
  - Barras + % encima + fecha en X + "en curso" (alpha + etiqueta)
  - Reglas de labels con n > 8 / n > 14
  - Verify: `./gradlew testDebugUnitTest lintDebug` (verde) + inspección visual
  - Files: `ui/StatsTab.kt`
- [ ] Task 3: Strings es/en (título card, "en curso", contentDescription)
  - Files: `res/values/strings.xml`, `res/values-en/strings.xml`
- [ ] Task 4: Release v0.19.0 (bump versionCode 26, tests+lint+assemble, tag,
      GitHub release, APK por Telegram)

### Checkpoint: Tasks 1-3
- [ ] Tests de `periodBars` verdes
- [ ] Card con barras funcional: toggle, "en curso", labels sin solapamiento
- [ ] Suite completa verde + lint limpio

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Muchos períodos → labels solapados | Med | Reglas n > 8 / n > 14 (omitir labels/%); ancho mínimo 4.dp |
| Confundir período actual con cerrado | Med | Alpha 0.45 + etiqueta "en curso" |
| Romper gráfico de línea existente | Bajo | Card nueva separada; no se toca `UsageChart` |
| Pico de período con 1 snapshot = valor plano | Bajo | Correcto por definición (pico = ese valor) |

## Open Questions

Ninguna (resueltas en spec: período actual SÍ con distinción visual; valor =
pico).
