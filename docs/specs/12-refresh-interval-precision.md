# 12 — Control de frecuencia de refresco con precisión al minuto

> Implementa **P1 + P2** de `11-refresh-interval-control.options.md`
> (elegido por JuanPa el 2026-11-15).

## Objetivo

Reemplazar el slider de frecuencia (31 posiciones, saltos de ~23 min) por un
control que permita fijar **cualquier minuto entre 1 y 720** y que diga con
honestidad **qué motor** se activa y **qué precisión** se puede esperar.

## Por qué

- Hoy `Slider(valueRange = 1f..720f, steps = 30)` → 31 valores discretos:
  no se puede elegir 5, 10, 20, 45 ni 90 min.
- En el rango 1–14 min el motor es un FGS `dataSync`; con `targetSdk 36`,
  Android 15+ lo corta a las **6 h/día** y la app no lo maneja: el refresco se
  detiene en silencio hasta que el usuario abre la app.
- La UI no dice qué motor usa cada valor, así que el usuario cree que "15 min"
  es exacto (WorkManager no lo es).

## Alcance y límites

Tocar:

- `feature/settings/src/main/java/com/jpyunism/ollamacloudusage/ui/settings/RefreshSection.kt`
- `feature/settings/build.gradle.kts` (solo si hace falta `testImplementation`)
- `core/notify/.../UsageScheduler.kt` (fallback + API pública del motor)
- `core/notify/.../UsageMonitorService.kt` (`onTimeout` del FGS)
- `core/ui/src/main/res/values/strings.xml` + `values-en/strings.xml`
- tests: `core/notify/src/test/.../UsageMonitorServiceTest.kt`,
  nuevo `feature/settings/src/test/.../RefreshOptionsTest.kt`

**NO** tocar: `UsageWorker`, `UsageRepository`, `AlertSettings` (el campo
`refreshIntervalMinutes: Int` se queda igual), la pantalla Usage/Stats ni el
widget. Sin refactors de paso.

## Contexto embebido

- `AlertSettings.refreshIntervalMinutes: Int` (core/model) ya es entero; el
  slider lo trunca con `toInt()`. No cambia el modelo.
- `UsageScheduler.MIN_REFRESH_MINUTES = 1`, `MAX_REFRESH_MINUTES = 720`,
  `MIN_PERIODIC_MINUTES = 15`. `schedule()` decide: `< 15` → FGS
  (`UsageMonitorService.start`), `>= 15` → WorkManager periódico
  (`ExistingPeriodicWorkPolicy.UPDATE`).
- `UsageMonitorService` corre un `while (isActive)` con `delay(minutes*60_000)`
  y `startForeground(UsageNotifier.PERSISTENT_ID, …)`; ya captura
  `IllegalStateException` (FGS denegado en background) dejando
  `KEY_PENDING_FGS`.
- Android 15+ (`targetSdk >= 35`) corta los FGS `dataSync` a 6 h/día y, si el
  servicio no se detiene, **el sistema lo mata**. Desde API 35 existe
  `Service.onTimeout(startId: Int, fgsType: Int)` para reaccionar.
- `UsageScheduler.retryPending()` restaura el FGS pendiente al arrancar en
  foreground (llamado desde `MainActivity.onCreate`).
- Strings actuales: `refresh_frequency`, `refresh_frequency_description`,
  `min_1`, `hours_12`, `interval_minutes`, `interval_hours`,
  `interval_hours_minutes`. Los recursos viven en `:core:ui` y se exponen por `api`.

## Diseño decidido

### UI (`RefreshSection`)

1. **Tres modos** (chips de filtro M3) que fijan un valor típico:
   - `Tiempo real` → 3 min (FGS, notificación persistente)
   - `Equilibrado` → 15 min (WorkManager)
   - `Ahorro` → 120 min (WorkManager)
   El modo se **deriva** del valor (`<= 5` tiempo real, `<= 30` equilibrado,
   resto ahorro); pulsar un modo solo cambia el valor.
2. **Presets** (chips): 1, 5, 10, 15, 30, 60, 120, 360, 720 min, con etiqueta
   formateada (`1 min`, `1 h`, `6 h`…).
3. **Ajuste fino**: botones −/+ de 1 min y campo numérico editable (1–720).
   Entrada inválida o fuera de rango → se ignora y se restaura el valor actual.
4. **Línea de estado** bajo el control, derivada del valor:
   - `< 15`: "Servicio en primer plano · notificación persistente. Android
     puede limitarlo a 6 h al día."
   - `>= 15`: "WorkManager · puede retrasarse si el teléfono está en reposo."

### Motor (`UsageScheduler` / `UsageMonitorService`)

- `UsageScheduler.engineFor(minutes): RefreshEngine` (enum `FOREGROUND_SERVICE`
  / `WORK_MANAGER`), pública y testeable — la UI la usa para el texto.
- `UsageScheduler.fallbackToPeriodic(context, minutes = 15)`: programa el
  WorkManager de 15 min como red de seguridad. Reutilizada por el servicio.
- `UsageMonitorService.onTimeout(startId, fgsType)`: al agotarse el tope
  diario, llama `fallbackToPeriodic` + guarda `KEY_PENDING_FGS` + `stopSelf`.
- `onDestroy` sin parada explícita → mismo fallback (cubre a los OEM que matan
  el servicio sin llamar a `onTimeout`).

### Tests (TDD, rojo primero)

- `RefreshOptionsTest` (JVM, `:feature:settings`): `engineFor`, `modeFor`,
  `sanitize(minutes)` (`0`/`721`/`-5` → valor por defecto), presets ordenados y
  dentro de rango.
- `UsageMonitorServiceTest`: `onTimeout` deja `KEY_PENDING_FGS` y no relanza el
  loop; el fallback se programa (verificable con el flag persistido).
- `UsageSchedulerTest` (nuevo, JVM): `engineFor` 1/14 → FGS; 15/720 → WM.

## Criterios de aceptación

1. **Dado** la pantalla Configuración, **cuando** se pulsa el preset `45`, no
   existe: los presets son 1,5,10,15,30,60,120,360,720; **pero** escribiendo
   `45` en el campo, el valor queda en 45 min y el texto dice "WorkManager".
2. **Dado** el campo, **cuando** se escribe `0`, `900` o vacío, **entonces** el
   valor no cambia y el control vuelve a mostrar el último válido.
3. **Dado** un valor `10`, **entonces** el texto dice "servicio en primer
   plano" y `UsageScheduler.engineFor(10) == FOREGROUND_SERVICE`; con `15` dice
   WorkManager.
4. **Dado** el FGS activo, **cuando** Android llama `onTimeout`, **entonces**
   el servicio se detiene, deja el intervalo pendiente y queda programado el
   WorkManager de 15 min (el refresco no muere en silencio).
5. El valor persiste tras force-stop y relanzar (regresión ya cubierta por
   `AlertSettings`/prefs).
6. `./gradlew testDebugUnitTest lintDebug assembleRelease` verde.

## Evidencia

- Captura del control nuevo con 3 modos, presets, stepper y campo.
- Captura con un valor escrito a mano (p. ej. 45) mostrando el motor correcto.
- Cambio de valor → force-stop → relanzar → el valor sigue.
- Tests verdes + emulador sin crash/ANR.
