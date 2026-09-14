# Spec P3: Sparkline inline 24 h + marcador de ritmo esperado (pace marker)

Issue: #30 — "UI: sparkline por ventana + pace marker sobre la barra"
Estado: draft
Autor: Hermes (2026-09-13)
Variante: **Divergente** — Uso se vuelve mini-dashboard sin ir a Estadísticas.

## Contexto y objetivo

La app calcula pero NO muestra dos cosas que el usuario quiere saber de un vistazo: (a) la trayectoria reciente del consumo dentro de la ventana (solo visible en StatsTab), y (b) si el % actual va "adelantado" o "atrasado" respecto al tiempo transcurrido de la ventana (hoy `computeBalance` da déficit/superávit en texto, sin referencia visual sobre la barra).

**Objetivo:** cada UsageMeterCard (sesión y semanal) gana (1) un sparkline del % en la ventana actual (área + línea), y (2) un pace marker: una línea vertical sobre la barra segmentada en la posición = fracción de tiempo transcurrido, más un tag "+N pts adelantado / ritmo OK".

**No es objetivo:** reemplazar StatsTab (su gráfico grande con selector de período y tooltips sigue), ni cambiar el widget, ni alterar cómo se capturan snapshots.

## Assumptions

1. Se toca `:feature:usage` (UsageTab / UsageMeterCard) + `Sparkline.kt` nuevo en `:core:ui` (compartido, variante con fill) + helpers puros en `:core:model` (testeables).
2. Datos sparkline: `historySnapshots.snapshots` filtrados a la ventana actual (desde `resetAt - duration` hasta ahora), selector `sessionPercent` / `weeklyPercent`. Ya existen en el VM; solo filtrar.
3. Pace esperado: `elapsedFraction = (now - windowStart) / duration` (0..1). Posición del marcador = `elapsedFraction * width`. Tag = `(percent/100 - elapsedFraction) * 100` puntos; zona OK si |diff| < umbral (propuesta: 5 pts, constante en `:core:model` para reusar en widget a futuro).
4. Área bajo la línea: mismo color de línea con alpha 0.3 — única excepción cromática nueva, derivada del color de estado (no hardcode).
5. El marcador NO es un componente interactivo: decorativo + CD ("tiempo transcurrido de la ventana: 57%").
6. Strings ES/EN: `pace_ahead` ("+%1$d pts adelantado"), `pace_behind`, `pace_on_track` ("ritmo OK"), `pace_marker_cd`, `expected_by_now` ("esperado a esta hora: %1$s%%").

## Estructura propuesta

### `:core:model` — helpers puros (testeables, sin Compose)

```kotlin
// PaceMetrics.kt (nuevo)
data class PaceMetrics(
    val elapsedFraction: Double,   // 0..1
    val deltaPoints: Int,          // percent - esperado, en puntos
    val onTrack: Boolean,          // |delta| < PACE_OK_THRESHOLD_PTS (5)
)
fun computePace(percent: Double, windowStart: Instant, duration: Duration, now: Instant): PaceMetrics

// Sparkline normalize (si no vino ya de P2)
fun sparklinePoints(snapshots: List<UsageSnapshot>, windowStart: Instant, now: Instant,
                    selector: (UsageSnapshot) -> Double): List<Pair<Long, Double>>
```

### `:core:ui` — `Sparkline.kt` (variante filled)

```kotlin
@Composable
fun Sparkline(
    points: List<Pair<Long, Double>>,
    color: Color,
    fill: Boolean = false,          // P3 usa fill=true + alpha 0.3
    paceMarkerFraction: Float? = null,  // dibuja línea vertical dashed en x = f*w
    modifier: Modifier = Modifier,  // fillMaxWidth, altura default 48.dp para P3
)
```

- Y scale: 0..100 fijo (los % de ventana viven ahí; consistente con ModelEvolutionChart) — evita que 2%→3% se vea como montaña.
- Si `points.size < 2` → no renderizar nada (la tarjeta oculta el bloque; criterio ya usado en `model_history_empty`).

### UsageTab.kt — UsageMeterCard extendida

```kotlin
UsageMeterCard(
    ..., 
    sparkline: List<Pair<Long, Double>>? = null,   // null = no mostrar
    pace: PaceMetrics? = null,                     // null = no mostrar
)
```

- Tras el header (título + %): `Sparkline(fill=true, color=TrafficLightColors[level], paceMarkerFraction=pace.elapsedFraction)`.
- Fila bajo el sparkline: izq `expected_by_now` (onSurfaceVariant), der `PaceTag` (tonal: fondo = color estado al 15%, texto = color estado; green "ritmo OK").
- La barra segmentada por modelo se mantiene debajo, ahora CON el marcador de pace también (BoxWithConstraints + Box 2.dp width offset a `fraction * width`, color outline).

### SuccessContent

```kotlin
val sessionPace = computePace(data.sessionPercent, data.sessionResetAt?.minus(SESSION.duration) ?: fallback, SESSION.duration, now)
```
Ojo: la ventana real = `resetAt - duration` (resetAt es el FIN). WindowStart derivado; si `resetAt == null` → pace = null (no mostrar).

## Plan de tareas

1. **[T1]** `:core:model`: `PaceMetrics` + `computePace` + `sparklinePoints` (filtro ventana + sort por timestamp). Tests unitarios: elapsed 0 / 1 / medio, percent>esperado → delta+, constante umbral 5.
2. **[T2]** `:core:ui`: `Sparkline` con fill + paceMarker. Previews: fill+marker, sin puntos, marker al 100%.
3. **[T3]** Strings ES/EN (5 nuevas).
4. **[T4]** `UsageMeterCard`: integrar sparkline + pace tag + marcador sobre la barra (`BoxWithConstraints`). Mantener firma retrocompatible (params opcionales).
5. **[T5]** `SuccessContent`: calcular paces/sparklines para sesión y semanal; pasarlos. Mover `AccountSwitcherRow` al header (mismo criterio que P1/P2 — hacerlo una vez, conflictua si se mergean varias: decidir orden de merge).
6. **[T6]** Tests Compose: tag visible con "+N", oculto cuando pace=null; CD del marcador presente.
7. **[T7]** Emulador: sembrar history (varios snapshots en las últimas horas/días vía `UsageHistoryStore.add` desde test o debug screen), verificar sparkline + marcador en ambas ventanas, dark/light, ventana recién reseteada (pocos puntos → sin sparkline).
8. **[T8]** Release estándar.

## Archivos tocados

- `core/model/.../PaceMetrics.kt` (nuevo ~40 líneas + test ~60)
- `core/ui/.../ui/Sparkline.kt` (nuevo ~90 líneas; si P2 ya lo creó, extenderlo con fill/marker)
- `feature/usage/.../ui/UsageTab.kt` (+~80 líneas)
- strings ES/EN (+5)
- tests

## Riesgos

- **Densidad:** sparkline + barra + tag + modelos en UNA tarjeta puede saturar. Mitigación: sparkline de 48.dp máx, sin ejes; el bloque de modelos queda tras HorizontalDivider como hoy.
- **Datos escasos tras reset:** ventana nueva = pocos snapshots → ocultar sparkline (regla <2 puntos) y mostrar solo pace tag.
- **`now` en previews/tests:** `computePace` recibe `now: Instant` explícito (inyectable) — nada de `Instant.now()` dentro del helper puro (misma razón por la que `UsageRepository` inyecta `now`).
- **Conflicto con P1/P2 si se mergean juntas:** las tres tocan `UsageMeterCard` y el header. Si el usuario elige más de una, orden de merge: P1 primero (mechanical), luego P3 (extiende la card), P2 es alternativa al header compacto de P1 (elegir una).

## Criterios de aceptación

1. Sesión y semanal muestran trayectoria inline sin ir a Estadísticas.
2. El usuario ve "+N adelantado / ritmo OK" sin leer el texto de balance.
3. Ventana recién reseteada no muestra sparkline vacío ni tag engañoso.
4. Sparkline oculto con <2 puntos; marcador siempre que haya resetAt.
5. Tests + lint + assembleRelease verdes; validación emulador completa.
