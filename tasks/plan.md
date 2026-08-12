# Implementation Plan: Estadísticas de uso histórico

## Overview

Nueva tab **Estadísticas** con gráfico de consumo porcentual (línea + área) a lo
largo del tiempo, toggle **Semana / Sesión**, marcadores de reset (diente de
sierra), tooltip al tocar y resumen del último período cerrado + período actual.
Los datos se acumulan localmente en cada refresh exitoso (la API no entrega
histórico).

## Architecture Decisions

- **Snapshots locales**: en cada refresh exitoso (manual, worker, arranque) se
  guarda `(timestamp, sessionPercent, weeklyPercent)` en SecurePrefs como JSON.
  - Dedupe: se omite si el % es idéntico al anterior y pasaron < 15 min.
  - Límite: 600 snapshots FIFO (≈25 días con refresco cada 60 min).
  - Sin Room ni deps nuevas (YAGNI, stdlib primero).
- **`UsageHistory.kt`** (puro, sin Android): modelo `UsageSnapshot(ts, sessionPct,
  weeklyPct)` + agregación:
  - `groupByPeriod(snapshots, periodDuration, resetAnchor)`: agrupa por período
    (semana: anclas cada 168 h desde el primer snapshot; sesión: cada 24 h).
  - `periodPeak(snapshots)`: pico (máx %) de un período → "cuánto se consumió
    antes del reset".
  - `resetMarkers(snapshots, periodDuration)`: timestamps de resets dentro del
    rango (para las líneas punteadas).
  - `nearestSnapshot(snapshots, xMillis)`: para el tooltip.
- **`UsageHistoryStore.kt`**: persistencia JSON en SecurePrefs (load/save,
  dedupe, límite FIFO). `record(percent, sessionPct, weeklyPct, now)`.
- **Gráfico**: Compose `Canvas` con `pointerInput` para tap → tooltip (burbuja
  con fecha + % del punto más cercano). Eje Y 0/50/100, labels de fecha en X.
  Línea + área con gradiente del color primario; líneas punteadas de reset.
- **UI**: nueva tab en `NavigationBar` (Usage / Estadísticas / Settings) con
  `Icons.Outlined.BarChart` / `Icons.Filled.BarChart`. Toggle con
  `SingleChoiceSegmentedButtonRow` (M3). Resumen: card con último período
  cerrado ("Semana del 3 ago: 62%") + período actual ("Vas 45% en la semana
  actual"). Estado vacío con hint.
- **Strings**: es/en para todo lo nuevo.
- **Duración**: sesión 24 h, semana 168 h (mismas constantes que Balance).

## Task List

- [ ] Task 1: `UsageHistory.kt` + `UsageHistoryTest.kt` (TDD)
  - Agrupación por período, picos, reset markers, nearestSnapshot
- [ ] Task 2: `UsageHistoryStore.kt` (JSON en SecurePrefs, dedupe, FIFO) + tests
- [ ] Task 3: Integración en `UsageViewModel.refresh()` (guardar snapshot) +
      StateFlow `history` expuesto
- [ ] Task 4: `ui/StatsTab.kt` — toggle, gráfico Canvas, tooltip, resumen,
      estado vacío + strings es/en
- [ ] Task 5: Tab en `UsageScreen.kt` (NavigationBar) + strings
- [ ] Task 6: Release v0.18.0 (bump versionCode 25, tests+lint+assemble, tag,
      GitHub release, APK por Telegram)

### Checkpoint: Tasks 1-3
- [ ] Tests de agregación y store verdes
- [ ] Snapshot guardado en cada refresh (verificación manual con logs)

### Checkpoint: Tasks 4-5
- [ ] Tab Estadísticas funcional: toggle, gráfico, tooltip, resumen, vacío
- [ ] Suite completa verde + lint limpio

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Sin histórico en la API → gráfico vacío al inicio | Med | Estado vacío claro + hint; se llena con el uso |
| Snapshots duplicados por refrescos frecuentes | Bajo | Dedupe 15 min + % idéntico |
| Crecimiento ilimitado del storage | Bajo | Límite 600 FIFO |
| Tooltip complejo en Canvas | Med | Solo tap → punto más cercano en X; sin gestos extra |
| Romper tabs existentes | Bajo | Tab nueva agregada al enum; sin tocar Usage/Settings |

## Open Questions

Ninguna (resueltas en spec: tooltip SÍ, ventana = todo, resumen = ambos).
