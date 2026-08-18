# Plan: Línea de proyección y consumo ideal en el gráfico de Estadísticas

Spec de referencia: `docs/spec-proyeccion-consumo.md` (REQ-001 a REQ-009).

## Overview

Sobre el gráfico de línea existente de la tab Estadísticas se agregan dos
referencias para el período en curso: la **proyección** (regresión lineal del
consumo real extendida hasta el próximo reset) y el **consumo ideal** (rampa
lineal 0% → 100%). La lógica pura vive en `UsageHistory.kt`; el Canvas dibuja.

## Steps

1. **T1 (RED)** — `ProjectionTest.kt`: período actual, proyección lineal,
   casos borde (sin anchor, 1 snapshot, fuera de rango, >100%).
2. **T2 (GREEN)** — `currentPeriod()` + `Projection` + `linearProjection()`
   en `UsageHistory.kt`.
3. **T3** — `HistoryState.sessionResetAt` (REQ-008): populate en refresh.
4. **T4** — StatsTab: rango X extendido, línea ideal discontinua, línea de
   proyección + etiqueta, leyenda, SummaryRow (REQ-002..006).
5. **T5** — Strings es/en (stats_ideal, stats_projection, stats_projection_summary).
6. **T6** — Verificación completa: tests + lint + build.
7. **T7** — Emulador (AGENTS.md) + release v0.29.0 (versionCode 41).

## Architecture Decisions

- **Lógica pura en `UsageHistory.kt`**: `currentPeriod()` y
  `linearProjection()` sin Android, testeables en JVM (patrón del repo).
- **Ancla de sesión**: `HistoryState.sessionResetAt` (nullable, default null)
  — mínimo cambio, no rompe consumidores existentes.
- **Rango X = max(último snapshot, fin de proyección)**: el valor de la
  proyección queda visible al cerrar el período.
- **Sin clamp del % proyectado**: si la regresión da 112%, se dibuja y se
  etiqueta 112% con color error (la advertencia es parte de la feature).
- **Colores del tema**: ideal = `tertiary`, proyección = `secondary` o `error`
  (si >100). Cero colores hardcodeados (regla del repo).

## Riesgos

- **Gráfico con pocos snapshots**: con <2 en el período actual no hay
  proyección (guard explícito + test).
- **Aglomeración de etiquetas**: la etiqueta de proyección se reubica si queda
  fuera del borde derecho del canvas.
- **Regresión v0.22.1**: no se toca `rememberPagerState`; verificación en
  emulador cubre el caso.
