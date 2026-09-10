# Spec: Proyeccion de agotamiento en pantalla principal

Issue: #54
Estado: draft
Autor: Hermes (subagente spec, 2026-09-10)

## Contexto y objetivo

Ya existe codigo de proyeccion en el historico (`Projection.kt` +
`ProjectionTest`) pero esta enterrado y no se expone al usuario. Los
usuarios quieren saber "a este ritmo, cuando me quedo sin cuota?" sin
hacer matematicas.

**Objetivo:** mostrar en `UsageTab` una tarjeta de proyeccion visible
cuando hay datos suficientes (>3 snapshots en el historico), que indique
cuando se llegara a X% al ritmo actual de consumo.

**Usuario objetivo:** cualquier usuario con al menos una semana de uso.

## Assumptions

1. El ritmo se calcula con regresion lineal simple sobre los ultimos N
   snapshots (default: 7 dias o el snapshot mas antiguo disponible, lo
   que sea mayor).
2. La proyeccion es **solo informativa** — no genera alertas ni cambia
   el comportamiento del refresh.
3. Se muestra para sesion Y semana por separado.
4. Si el ritmo es estable o decreciente, el texto refleja eso en vez de
   proyectar.
5. La sesion tiene un cap natural (el `resetAt`), asi que la proyeccion
   de sesion muestra "tu sesion se reinicia en X, llegas al reset con
   Y% estimado" ademas del "llegas al 100% en Z".

## Tech stack

- Kotlin 1.9.x
- Jetpack Compose (sin nuevas deps; usa Canvas y Material 3 existentes)
- Sin almacenamiento adicional; consume `UsageHistoryStore` ya existente
- Sin backend; todo en cliente

## Comandos

```bash
./gradlew testDebugUnitTest --tests "com.jpyunism.ollamacloudusage.ProjectionEngineTest"
./gradlew testDebugUnitTest --tests "com.jpyunism.ollamacloudusage.UsageViewModelTest"
./gradlew assembleDebug
```

## Estructura propuesta

```
app/src/main/java/com/jpyunism/ollamacloudusage/
  ProjectionEngine.kt          # logica pura: snapshots -> Projection
  model/
    Projection.kt              # data class resultado (ya existe, ampliar)
  ui/
    UsageTab.kt                # mod: nueva ProjectionCard composable
    ProjectionCard.kt          # UI de la tarjeta
app/src/test/.../
  ProjectionEngineTest.kt      # casos: ritmo creciente, estable, decreciente
                               # sesiones cortas, sin datos suficientes
```

## Code style (ejemplo)

```kotlin
// ProjectionEngine.kt - logica pura, testeable sin Android
object ProjectionEngine {

    data class Result(
        val trend: Trend,           // RISING, STABLE, FALLING
        val percentPerHour: Double, // ritmo calculado
        val hoursToTarget: Double?, // null si no se puede calcular
        val percentAtReset: Double?, // % estimado al momento del reset
    )

    enum class Trend { RISING, STABLE, FALLING }

    fun project(
        snapshots: List<UsageSnapshot>,
        targetPercent: Int = 100,
        resetAt: Instant?,
        now: Instant = Instant.now(),
    ): Result? {
        if (snapshots.size < 3) return null
        val recent = snapshots.takeLast(minOf(snapshots.size, 7 * 24))
        val rate = linearRate(recent) ?: return null
        val trend = when {
            rate > 0.5 -> Trend.RISING
            rate < -0.5 -> Trend.FALLING
            else -> Trend.STABLE
        }
        val current = snapshots.last().weeklyPercent
        val hoursToTarget = if (rate > 0) (targetPercent - current) / rate else null
        val percentAtReset = resetAt?.let {
            val hoursToReset = Duration.between(now, it).toMinutes() / 60.0
            (current + rate * hoursToReset).coerceIn(0.0, 100.0)
        }
        return Result(trend, rate, hoursToTarget, percentAtReset)
    }

    private fun linearRate(snapshots: List<UsageSnapshot>): Double? {
        // Least-squares fit: y = a + b*x donde x = horas desde el primer snapshot
        // ...
    }
}
```

## Testing strategy

- `ProjectionEngineTest` con casos:
  - Sin datos suficientes (<3 snapshots) -> null.
  - 3 snapshots con ritmo creciente -> hoursToTarget > 0, trend = RISING.
  - Snapshots con varianza cero -> trend = STABLE, hoursToTarget = null.
  - Ritmo decreciente -> trend = FALLING, hoursToTarget = null.
  - Calculo de percentAtReset cuando resetAt esta en el futuro.
  - percentAtReset clampeado a [0, 100] cuando el ritmo lo sobrepasa.
- `UsageViewModelTest` extendido: que `Success` ahora incluya
  `projection: ProjectionEngine.Result?` y se exponga al UI.
- Test de regresion: `ProjectionTest` (existente) sigue pasando.

## Boundaries

- **Always do:**
  - Localizar todos los strings (ES/EN).
  - Ocultar la tarjeta si `projection == null` (datos insuficientes).
  - Documentar en CHANGELOG.md.
- **Ask first:**
  - Cambiar la ventana de calculo (default 7 dias / max snapshots).
  - Agregar una nueva dimension de proyeccion (ej. costo estimado).
- **Never do:**
  - Disparar alertas basadas en la proyeccion (solo informativo).
  - Cambiar `Projection.kt` (data class) de forma incompatible sin
    actualizar `ProjectionTest`.

## Success criteria

1. La tarjeta aparece en `UsageTab` cuando hay >= 3 snapshots.
2. Texto: "Al ritmo actual, agotaras la cuota semanal en X dias" con
   icono segun trend (flecha arriba/abajo/dash).
3. Version de sesion: "Tu sesion se reinicia en Y. Llegaras al Z%."
4. Test cases pasan, lint limpio, validado en emulador API 35.

## Estimacion

2h de implementacion + 30min de tests. Total: 2.5h.
