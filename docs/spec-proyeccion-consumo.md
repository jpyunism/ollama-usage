# Spec: Línea de proyección y consumo ideal en el gráfico de Estadísticas

## Objective

En el gráfico de línea de la tab **Estadísticas** (que hoy muestra el consumo
real acumulado con marcadores de reset y tooltip), agregar dos referencias
visuales:

1. **Línea de proyección**: si se siguiera consumiendo al mismo ritmo, ¿dónde
   se aterriza al cierre del período en curso? Se calcula por regresión lineal
   sobre los snapshots del período actual y se extiende hasta el próximo reset.
2. **Consumo ideal**: el ritmo lineal esperado (0% al inicio del período →
   100% en el reset), como referencia de "el camino perfecto".

**Qué se construye:** dos líneas sobre el mismo `Canvas` del gráfico + una
leyenda mínima (swatches + etiquetas) bajo el gráfico + una fila de resumen con
el % proyectado al cierre.

**Quién es el usuario:** el usuario final de la app Android (consumo de Ollama
Cloud). **Por qué:** ver a tiempo si el ritmo actual lleva a superar la cuota
del período, comparado contra el ritmo ideal.

**Qué es "éxito":** en el período actual con ≥2 snapshots se dibuja la
proyección hasta el próximo reset (con su valor final), la línea ideal es
visible, la leyenda explica ambas, y los datos históricos siguen intactos.

### Acceptance criteria (testeables)

- [ ] Con ≥2 snapshots del período actual, la proyección termina en el próximo
      reset con el % proyectado correcto (regresión lineal sobre esos puntos).
- [ ] Con <2 snapshots del período actual, no hay proyección (sí puede haber
      línea ideal si hay ≥1).
- [ ] El rango X del gráfico se extiende hasta el fin del período actual solo
      cuando hay proyección, para que la línea llegue visible al reset.
- [ ] La línea ideal va de (inicio período, 0%) a (próximo reset, 100%),
      recortada al rango visible del gráfico.
- [ ] Leyenda bajo el gráfico: "Ideal" (siempre que haya datos del período
      actual) y "Proyección: X%" (si hay proyección).
- [ ] Fila de resumen: "A este ritmo terminarías en X%" solo con proyección.
- [ ] Sin datos del período actual (o sin reset conocido): nada cambia respecto
      a hoy (cero líneas extra, cero leyenda).
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes.
- [ ] Validación manual en emulador (AVD `test64`) sin crashes/ANRs.

## Requisitos (IDs)

- **REQ-001 — Proyección por regresión lineal.** Función pura
  `currentPeriod(snapshots, period, resetAnchor, now, selector)` que devuelve
  el período en curso (`start`, `end` = próximo reset) y una `Projection`
  (`fromTimestamp`, `fromPercent`, `toTimestamp` = `end`, `toPercent`) por
  regresión lineal (mínimos cuadrados) sobre los snapshots dentro del período.
  Requiere ≥ 2 snapshots en el período; si no, `projection = null`.
- **REQ-002 — Línea de proyección en el gráfico.** Se dibuja en el `Canvas`
  desde el último snapshot del período hasta el próximo reset, con el color del
  tema (error si proyectado > 100%, si no `secondary`), trazo continuo, punto
  en el extremo y etiqueta con el % proyectado (reubicada si queda fuera del
  borde derecho).
- **REQ-003 — Extensión del rango X.** El eje X del gráfico termina en
  `max(último snapshot, toTimestamp de la proyección)`. El resto de la serie
  (área, línea, puntos, tooltip) usa el mismo rango extendido.
- **REQ-004 — Línea ideal.** Se dibuja (discontinua, color `tertiary` del tema)
  el segmento de rampa lineal del período actual, recortado al rango visible:
  (inicio, 0%) → (fin, 100%). Solo si hay ≥1 snapshot en el período actual.
- **REQ-005 — Leyenda.** Debajo del gráfico, fila con dos ítems (swatch de
  color + etiqueta): "Ideal" (si aplica) y "Proyección: X%" (si hay
  proyección). Strings es/en.
- **REQ-006 — Resumen textual.** Nueva `SummaryRow` "A este ritmo terminarías
  en X%" (solo con proyección), debajo de la fila del período actual.
- **REQ-007 — Histórico intacto.** Los períodos cerrados siguen igual (solo
  barras/reset markers); la proyección y el ideal solo aplican al período en
  curso con datos.
- **REQ-008 — `HistoryState` con ancla de sesión.** `HistoryState` gana
  `sessionResetAt: Instant?` (se puebla en el refresh desde `UsageData`) para
  poder calcular el período actual también en modo Sesión. Default `null`, no
  rompe nada existente.
- **REQ-009 — TDD RED→GREEN + release.** Tests unitarios de la función pura
  (REQ-001) antes de implementar; release obligatorio al cerrar (regla
  AGENTS.md).

## Tech Stack

- Kotlin + Jetpack Compose (Material 3), `compileSdk 36`, `minSdk 26`.
- Dibujo con el `Canvas` existente del gráfico (sin librerías nuevas).
- **Sin dependencias nuevas.**

## Commands

```bash
export JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8
export ANDROID_HOME=/home/jyunis/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew testDebugUnitTest    # TDD RED→GREEN
./gradlew lintDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest lintDebug assembleRelease   # verificación completa
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/
  UsageHistory.kt  → + CurrentPeriodInfo, Projection, currentPeriod(), linearProjection()
  UsageViewModel.kt → HistoryState + sessionResetAt (REQ-008)
  ui/StatsTab.kt   → + líneas ideal/proyección en UsageChart, leyenda, SummaryRow (REQ-002..006)
app/src/test/java/com/jpyunism/ollamacloudusage/
  ProjectionTest.kt → tests de currentPeriod/linearProjection (REQ-009)
app/src/main/res/values/strings.xml + values-en/strings.xml → stats_ideal, stats_projection, stats_projection_summary
docs/
  spec-proyeccion-consumo.md → esta spec
```

## Code Style

- Material 3 estricto (regla del repo): los colores de las líneas SIEMPRE desde
  `MaterialTheme.colorScheme` (`tertiary`, `secondary`, `error`), nunca
  hardcodeados.
- La lógica pura vive en `UsageHistory.kt` (sin Android); la UI solo dibuja.
- `StatsTab` sigue sin recibir el ViewModel.

Esqueleto de referencia (patrón):

```kotlin
// UsageHistory.kt
data class Projection(
    val fromTimestamp: Long, val fromPercent: Double,
    val toTimestamp: Long, val toPercent: Double,
)
data class CurrentPeriod(
    val start: Long, val end: Long,
    val snapshotCount: Int, val projection: Projection?,
)

fun currentPeriod(snapshots, period, resetAnchor: Long?, now: Long, selector): CurrentPeriod? {
    if (resetAnchor == null || snapshots.isEmpty()) return null
    val d = period.durationMillis
    var end = resetAnchor; while (end <= now) end += d   // próximo reset futuro
    val start = end - d
    val inPeriod = snapshots.filter { it.timestampMillis in start until end }
    return CurrentPeriod(start, end, inPeriod.size, linearProjection(inPeriod, end, selector))
}

// StatsTab — dibujo (patrón)
val idealColor = MaterialTheme.colorScheme.tertiary
val projColor = if (proj.toPercent > 100) error else secondary
drawLine(idealColor, ..., pathEffect = PathEffect.dashPathEffect(...)) // ideal
drawLine(projColor, Offset(xFrom, yFrom), Offset(xTo, yTo))           // proyección
```

## Testing Strategy

- **Framework:** JUnit4 + Mockk + kotlinx-coroutines-test (ya en el repo).
- **Qué se testea (REQ-009)** en `ProjectionTest.kt` (RED primero):
  - `currentPeriod` sin anchor → null.
  - `fallbackResetAnchor`: semana → próximo domingo 21:00 CLT; sesión → null
    (no sintetizable; la ventana móvil no tiene cierre real).
  - Con anchor y snapshots del período: `start`/`end` correctos (end = próximo
    reset, start = end − duration).
  - Proyección con 2 puntos: `toTimestamp == end` y `toPercent` = valor de la
    regresión lineal (caso exacto: pendiente conocida).
  - Proyección con pendiente que supera 100: `toPercent > 100` (sin clamp).
  - Con 1 snapshot en el período: `projection == null`, `snapshotCount == 1`.
  - Snapshots fuera del período (históricos) se excluyen del cálculo.
  - Con `now` fuera del período (período cerrado): comportamiento seguro
    (período actual = siguiente, no anterior).
- **Qué NO se testea en unit:** el dibujo en Canvas (manual en emulador).
- **Verify:** `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes +
  navegación manual en emulador (Stats con y sin datos, toggle Semana/Sesión,
  cambio de idioma sin reset de tab).

## Boundaries

- **Always:**
  - Material 3 exclusivo (regla AGENTS.md); colores del tema.
  - Strings es/en en `values/strings.xml` y `values-en/strings.xml`.
  - Tests verdes + lint limpio antes de release.
  - Validar en emulador (AVD `test64`) antes de publicar.
  - Release obligatorio al cerrar: bump `versionCode` (+1) y `versionName`
    (semver) en `app/build.gradle.kts`, commit + push + tag `v<versionName>`,
    release en GitHub con APK firmado y envío por Telegram (chat 15710279).
- **Ask first:**
  - Agregar dependencias nuevas (no debería hacer falta).
  - Cambiar el criterio de proyección (p.ej. usar solo los últimos N snapshots
    en vez de todos los del período, o exponencial en vez de lineal).
  - Mostrar la proyección en el widget o notificaciones.
- **Never:**
  - Tocar el widget de home screen ni las notificaciones.
  - Hardcodear colores/tipografías.
  - Cambiar el contrato de `AlertSettings`, `PrefsKeys` ni el pipeline de
    `UsageRepository`.
  - Romper la persistencia de la tab activa tras recreación (regresión v0.22.1).

## Success Criteria

- [ ] `currentPeriod()` devuelve período + proyección correctos (REQ-001).
- [ ] Línea de proyección visible hasta el próximo reset con etiqueta de %
      (REQ-002, REQ-003).
- [ ] Línea de ideal discontinua de 0% → 100% visible (REQ-004).
- [ ] Leyenda con Ideal / Proyección bajo el gráfico (REQ-005).
- [ ] Fila de resumen con el % proyectado (REQ-006).
- [ ] Historial previo sin cambios (REQ-007); `HistoryState` con
      `sessionResetAt` (REQ-008).
- [ ] Tests RED→GREEN (REQ-009); sin deps nuevas; M3 estricto.
- [ ] `testDebugUnitTest`, `lintDebug`, `assembleRelease` verdes.
- [ ] Emulador sin crashes/ANRs + release publicado (v0.29.0, versionCode 41)
      con APK y screenshots.

## Decisiones (técnicas)

- **Regresión lineal (mínimos cuadrados) sobre todos los snapshots del período
  actual.** Es la interpretación más directa de "si se siguiera consumiendo a
  este ratio". Alternativas descartadas: solo los últimos 2 puntos (ruidoso),
  media de tasas (menos robusta que la regresión).
- **La proyección se dibuja desde el último snapshot hasta el próximo reset y
  el rango X se extiende hasta ahí** (REQ-003). El valor final queda a la vista
  (p.ej. 87% o 112%), que es la pregunta que responde la proyección.
- **El período se alinea al próximo reset conocido** (`while (end <= now) end
  += duration`), igual que `periodsFor` pero hacia adelante. El `anchor` de la
  sesión llega vía `HistoryState.sessionResetAt` (REQ-008).
- **Fuente sin resets (método API key):** `fallbackResetAnchor` sintetiza el
  ancla semanal (próximo domingo 21:00 CLT, supuesto documentado del repo)
  para que ideal/proyección funcionen en modo Semana. La sesión no es
  sintetizable (ventana móvil sin cierre real) → sin ideal/proyección en modo
  Sesión con esta fuente; con cookie sí (el scraper entrega resets reales).
- **La línea ideal es la rampa lineal 0→100 del período actual** (no de los
  históricos): es la única donde tiene sentido la comparación con el consumo en
  curso. Se recorta al rango visible del gráfico.
- **Colores por semántica:** ideal = `tertiary` (meta), proyección = `error`
  si se pasa de 100% (riesgo de exceder) o `secondary` si no.
- **Leyenda y resumen textual** (REQ-005/006) para que las líneas se
  autoexpliquen; strings es/en.

## Open Questions

1. **Regresión:** ¿todos los snapshots del período o solo los últimos N?
   *(Recomendado: todos los del período.)*
2. **Versión del release:** se asume `0.29.0` / `versionCode 41`. Confirmar.
