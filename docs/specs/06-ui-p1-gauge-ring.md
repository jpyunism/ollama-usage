# Spec P1: Gauge ring + tarjeta Sesión compacta

Issue: #113 — "UI: anillo de consumo de sesión + tarjetas compactas"
Estado: draft
Autor: Hermes (2026-09-13)
Variante: **Conservadora** — mínima disrupción del layout actual.

## Contexto y objetivo

Hoy `UsageTab` apila: Header → ProjectionCard(s) → AccountSwitcher → UsageMeterCard(Sesión) → UsageMeterCard(Semanal) → botones. Las dos ventanas pesan igual visualmente, pero la sesión (5 h) es la que decide el día a día; el % queda perdido como texto dentro de una tarjeta con mucho detalle por modelo.

**Objetivo:** que el % de sesión sea leíble "al vistazo" (glanceable) sin abrir la tarjeta, convirtiéndolo en un anillo de progreso con el número al centro, y compactando la presentación del resto.

**No es objetivo:** cambiar la navegación por tabs, el widget, ni tocar `StatsTab`/`SettingsTab`. No se altera la lógica de datos (scraper, repo, history).

## Assumptions

1. Solo se toca `:feature:usage` (UsageTab.kt) y, si hace falta, un composable nuevo en `:core:ui`. Cero cambios en `:core:model` / `:core:data`.
2. El anillo reusa `TrafficLightColors` y `TrafficLight.paceColor(percent, alert, critical)` para el color — mismo criterio que el % textual actual, sin inventar thresholds nuevos.
3. El detalle por modelo dentro de `UsageMeterCard` NO se elimina; se mantiene tal cual (la propuesta solo cambia el header de la tarjeta).
4. Tipografía tabular para el % del anillo (evita "saltos" al refrescar). En Compose: `style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum")`.
5. Strings nuevos mínimos (contentDescription del anillo). ES + EN en `:core:ui/res/values*/strings.xml`.
6. Cambio 100% M3: `Canvas`, `CircularProgressIndicator` como referencia visual, sin librerías externas.

## Estructura propuesta

### Nuevo composable: `UsageRing.kt` (en `:core:ui/ui/`)

```kotlin
@Composable
fun UsageRing(
    percent: Double,            // 0..100+
    level: TrafficLightLevel,   // de paceColor()
    label: String,              // "SESIÓN" / "SEMANAL"
    modifier: Modifier = Modifier,   // default size 96.dp
)
```

- `Canvas` dibuja: track (colorScheme.surfaceVariant) + arco con `StrokeCap.Round`, color = `TrafficLightColors[level]`.
- Texto centrado: % en `headlineMedium` + `tnum`, label debajo en `labelSmall` con tracking.
- `semantics { contentDescription = "$label ${formatPercent(percent)}% usado" }` (string plurals ES/EN).
- Sweep = `(percent.coerceIn(0,100) / 100) * 360f`. Si percent > 100, arco completo + color RED (ya lo da paceColor).

### Cambios en UsageTab.kt

- `UsageMeterCard`: prop `compact: Boolean = false`.
  - `compact = true` → header = `Row { UsageRing() ; Column(título, reset, balance) }`, barra segmentada se mantiene debajo, detalle por modelo intacto.
  - Sesión → `compact = true`. Semanal → se mantiene `compact = false` (o compact según decisión final; default: sesión compact, semanal igual que hoy).
- `AccountSwitcherRow` se mueve al Header (misma fila que plan/hora, scroll horizontal si hay >1 cuenta). Sale de entre proyecciones y medidores.

## Plan de tareas

1. **[T1]** Crear `UsageRing.kt` en `:core:ui` + preview `@Preview` (light/dark, percent 41/69/97). Tests de screenshot no requeridos; test unitario del sweep si se extrae a función pura en `:core:model` (ej. `ringSweep(percent): Float`).
2. **[T2]** Strings ES/EN: `usage_ring_cd` ("%1$s: %2$s%% usado"), nada más.
3. **[T3]** `UsageMeterCard(..., compact = true)` — header con anillo. Mantener la firma público-compatible (default false) para no romper usos existentes.
4. **[T4]** Mover `AccountSwitcherRow` al Header (fila de título). Ajustar paddings.
5. **[T5]** Tests: `:feature:usage` — verificar que SuccessContent renderiza con anillo (Compose test: assert nodo con contentDescription "Sesión"). Actualizar tests existentes que asuman el Row anterior del header.
6. **[T6]** Validación emulador (protocolo AGENTS.md): force-dark/light, comparar lectura glanceable del %, pull-to-refresh, cambio de cuenta.
7. **[T7]** Release: bump version, `./gradlew testDebugUnitTest lintDebug assembleRelease`, tag, GitHub release, APK por Telegram.

## Archivos tocados

- `core/ui/src/main/java/.../ui/UsageRing.kt` (nuevo, ~80 líneas)
- `core/ui/src/main/res/values/strings.xml`, `values-en/strings.xml` (+1 string c/u)
- `feature/usage/.../ui/UsageTab.kt` (compact mode + mover switcher, ~40 líneas netas)
- tests en `feature/usage/src/test/`

## Riesgos

- **StackOverflow por shadowing** (bug conocido del repo): NO declarar un `fun usageRing` local que haga shadow del composable; importar con alias si hay colisión (`import ...UsageRing as ...` no aplica aquí, pero mantener nombres únicos). Seguir el patróon del `coreModelColor` alias ya usado.
- Accesibilidad: el anillo sin contentDescription sería un elemento decorativo que TalkBack ignora → el string CD es obligatorio, no opcional.
- Ancho en pantallas chicas: el Row ring+columna debe usar `weight(1f)` en la Column para no desbordar (mismo patrón `maxLines=1 + autoSize` ya resuelto para los botones).

## Criterios de aceptación

1. El % de sesión se lee sin hacer scroll, como anillo con color de semáforo.
2. Detalle por modelo y proyecciones siguen presentes e idénticos en contenido.
3. Dark + light se ven correctos (tokens M3, sin colores hardcodeados salvo `TrafficLightColors` existente).
4. `testDebugUnitTest lintDebug assembleRelease` en verde.
5. APK validado en emulador (protocolo completo, con capturas).
