# Spec P2: Hero de sesión + pills de estado + sparkline por modelo

Issue: #114 — "UI: hero card de sesión, pills ritmo/proyección/semana, sparkline por modelo"
Estado: draft
Autor: Hermes (2026-09-13)
Variante: **Strong-fit** — la sesión domina; el resto se compacta.

## Contexto y objetivo

Hoy la información de sesión está repartida en 3 tarjetas (`Header`, `ProjectionCard` sesión, `UsageMeterCard` sesión) y el dato más accionable (el % de la ventana de 5 h) es un texto más entre muchos. Las proyecciones y el balance viven separados del número al que se refieren.

**Objetivo:** consolidar todo lo de sesión en UNA "hero card" — % enorme + 3 pills (Ritmo / Proyección / Semana) — y convertir el detalle por modelo en filas compactas con mini-sparkline, eliminando tarjetas duplicadas de la pila.

**No es objetivo:** tocar StatsTab (que ya tiene su gráfico grande), ni el widget, ni la lógica de `ProjectionEngine`/`computeBalance` (solo se reusa su output).

## Assumptions

1. Se toca `:feature:usage` (UsageTab.kt, y se ELIMINA el uso de `ProjectionCard` para sesión) + composable nuevo `Sparkline.kt` en `:core:ui`. `ProjectionCard` queda solo para semanal… o se absorbe también; decisión: semanal pasa a ser la tercera pill + su `UsageMeterCard` compacta (P1-style sin anillo), `ProjectionCard` semanal se elimina de la pantalla (queda el componente por si se usa en stats).
2. El sparkline por modelo consume `historySnapshots.snapshots` ya disponibles en el VM (`vm.history`), función `modelPercent(snapshot, model)` ya existe en `:core:model`. Cero lógica nueva de datos.
3. Las 3 pills: **Ritmo** (de `computeBalance`: déficit/superávit/% sobre ritmo), **Proyección** (de `ProjectionEngine.Result`: % proyectado al cierre o fecha de agotamiento), **Semana** (weeklyPercent actual). Datos YA calculados en `UiState.Success`.
4. Tipografía: % héroe en `displayLarge`-ish (~56.sp) con `tnum`. Sigue siendo `MaterialTheme.typography` escalado, no fuente custom.
5. Strings ES/EN nuevos: títulos de pills ("Ritmo", "Proyección", "Semana"), CDs de accesibilidad.
6. Todo M3/Canvas; sparkline = `Canvas` con `Path`, mismo patrón que el `ModelEvolutionChart` existente (2dp stroke, cap round).

## Estructura propuesta

### Nuevo: `SessionHeroCard.kt` (en `:feature:usage/ui/`, es wiring de datos de la feature)

```kotlin
@Composable
fun SessionHeroCard(
    percent: Double,
    resetAt: Instant?,
    balance: Balance?,            // computeBalance(...)
    projection: ProjectionEngine.Result?,
    weeklyPercent: Double,
    alertThreshold: Int, criticalThreshold: Int,
)
```

- Fila 1: label "SESIÓN ACTUAL" (labelSmall, tracking) + hora actualización (mueve del Header).
- % gigante: `formatPercent(percent)` con color de `TrafficLightColors[paceColor(...)]`.
- Sub-línea: "en uso · restablece en X" (reusa `formatReset` + `ResetStrings`).
- `Row` de 3 `Pill` (surfaceVariant, radius 10dp, weight 1f): label chico + valor bold.
- El Header de pantalla queda solo con plan + switcher de cuentas (mover switcher ahí, como en P1).

### Nuevo: `Sparkline.kt` (en `:core:ui/ui/`)

```kotlin
@Composable
fun Sparkline(
    points: List<Pair<Long, Double>>,  // (timestampMillis, percent)
    color: Color,
    modifier: Modifier = Modifier,     // default: width 64.dp, height 20.dp
)
```

- `Canvas`: normaliza Y al rango real de puntos (no 0..100 fijo; si min==max, línea plana a la mitad), stroke 1.5.dp, cap round.
- Sin grid, sin ejes (es decorativo-informativo; el valor exacto va en el texto al lado).
- CD: "tendencia" o nada (las filas ya tienen el % exacto en texto → TalkBack usa ese).

### UsageTab.kt (SuccessContent) tras el cambio

1. `Header` reducido: plan + hora + `AccountSwitcherRow` (si >1 cuenta).
2. `SessionHeroCard(...)` — reemplaza a: ProjectionCard sesión + UsageMeterCard sesión.
3. `UsageMeterCard` semanal versión compacta (sin cambio de P1, o la actual tal cual; mínimo: la actual).
4. Card "Por modelo (sesión)": filas `Row(nombre · barrita color dot, % exacto)` + `Sparkline` a la derecha, clickable → `onModelClick` (ModelEvolutionSheet se conserva).
5. Botones (refresh/share/change auth) sin cambio.

**Eliminados de la pantalla:** `ProjectionCard` (sesión y quizá semanal), `UsageMeterCard` sesión. La cuenta: pasa de 5-6 tarjetas apiladas a 3-4.

## Plan de tareas

1. **[T1]** `Sparkline.kt` en `:core:ui` + previews (puntos planos, creciente, 1 solo punto → no crash, línea plana).
2. **[T2]** `SessionHeroCard.kt` en `:feature:usage` con `Pill` interno (privado). Previews light/dark/ámbar/rojo.
3. **[T3]** Strings ES/EN: `pill_pace`, `pill_projection`, `pill_week`, `hero_session_label`, `hero_in_use_reset`, CD del hero.
4. **[T4]** Rewire `SuccessContent`: header reducido + hero + semanal compact + card de modelos con sparkline. Eliminar llamadas a `ProjectionCard`/`UsageMeterCard` sesión (los composables quedan en el repo; no borrar el archivo `ProjectionCard.kt` todavía — lo usa potencialmente nadie, decidir deprecation en PR).
5. **[T5]** Tests :feature:usage: hero renderiza % y 3 pills; fila de modelo expone sparkline con CD; click en fila abre sheet (test existente de ModelEvolutionSheet no debe romperse). Actualizar tests que busquen "Proyección semanal" como tarjeta si se elimina.
6. **[T6]** Tests de `Sparkline`: normalización Y con min==max no divide por cero (extraer `sparklineYs(points): List<Float>` pura a `:core:model` y testearla ahí).
7. **[T7]** Validación emulador completa: hero glanceable, pills con datos reales vs. web ollama.com, sparklines con 2+ días de history (sembrar snapshots de prueba si hace falta via `UsageHistoryStore`).
8. **[T8]** Release estándar (tests+lint+assembleRelease, tag, GitHub release, APK Telegram).

## Archivos tocados

- `core/ui/.../ui/Sparkline.kt` (nuevo, ~50 líneas)
- `feature/usage/.../ui/SessionHeroCard.kt` (nuevo, ~150 líneas)
- `feature/usage/.../ui/UsageTab.kt` (rewire SuccessContent; -60/+80 líneas aprox)
- `core/model/.../` solo si se extrae `sparklineYs` (T6) (~15 líneas + test)
- strings ES/EN (+6 u 8)
- tests

## Riesgos

- **Regresión de información:** al fusionar tarjetas hay que garantizar que NADA de lo que mostraba `ProjectionCard` (trend icon, "agotamiento el sáb", "al ritmo actual X%") se pierde — mapearlo 1:1 a la pill Proyección + sub-línea. Checklist en el PR.
- Sparkline con <2 puntos → no dibujar, mostrar "—" (mismo criterio que ModelEvolutionSheet con `model_history_empty`).
- Shadowing Kotlin (bug histórico del repo): nombres únicos para `sparkline`, sin fun locales colisionando con imports.
- Pills en pantallas angostas: 3 × weight(1f) con texto chico; si no cabe, label en 2 líneas controlado (maxLines=2) o reducir con autoSize como los botones.

## Criterios de aceptación

1. La pantalla principal tiene ≤ 4 bloques scrolleables; el % de sesión domina visualmente.
2. Ritmo, proyección y % semanal se leen sin scroll ni tap adicional.
3. Cada modelo muestra mini-tendencia sin abrir el bottom sheet.
4. Información mostrada antes (proyecciones, reset, balance) sigue disponible en algún punto del hero/compact cards.
5. Tests + lint + assembleRelease verdes; emulador validado end-to-end.
