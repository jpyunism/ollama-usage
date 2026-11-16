# Plan: refresh con precisión al minuto (spec `docs/specs/12-refresh-interval-precision.md`)

P1 + P2 elegidas por JuanPa: control nuevo (presets + minuto exacto + modos) y
fix del cap de 6 h/día del FGS `dataSync`. TDD RED→GREEN por unidad.

## Steps

1. **T1 (RED)** — `RefreshOptionsTest` (`:feature:settings`): presets
   `1,5,10,15,30,60,120,360,720` ordenados y dentro de rango; `modeFor`
   (≤5 real, ≤30 equilibrado, resto ahorro); `sanitize` (0, 721, -5 → default);
   `modeDefault` (3/15/120).
2. **T2 (GREEN)** — `RefreshOptions` (objeto puro, sin Android) en
   `feature/settings/.../ui/settings/`.
3. **T3 (RED)** — `UsageSchedulerTest` (`:core:notify`): `engineFor(1|14)` =
   FOREGROUND_SERVICE; `engineFor(15|720)` = WORK_MANAGER.
4. **T4 (GREEN)** — `RefreshEngine` enum + `UsageScheduler.engineFor` +
   `fallbackToPeriodic(context)` (WM 15 min, `ExistingPeriodicWorkPolicy.UPDATE`).
5. **T5 (GREEN)** — `UsageMonitorService`: `onTimeout` (API 35+) y `onDestroy`
   sin parada explícita → `fallbackToPeriodic` + `KEY_PENDING_FGS` + stopSelf.
6. **T6 (UI)** — `RefreshSection`: 3 modos (chips), 9 presets (chips), stepper
   −/+, campo numérico, línea de motor/precisión. Strings es/en.
7. **T7 (androidTest)** — `fallbackToPeriodic` deja el work
   `usage_periodic_check` encolado en WorkManager.
8. **T8** — Validación en emulador (valor escrito a mano, persistencia tras
   force-stop, sin crash) + release v0.38.8.

## Riesgos

- El cap de 6 h no se puede esperar en un test: se cubre la pieza que importa
  (el fallback a WorkManager) y `onTimeout` delega en ella.
- `TextField` dentro de una sección plegable: cuidar foco del IME y que el
  debounce de guardado no pierda el valor.
- Presets en dos filas (`FlowRow`) para no desbordar en pantallas angostas.

## Verificación

- `./gradlew testDebugUnitTest lintDebug assembleRelease`.
- Emulador: escribir `45` → texto WorkManager; preset `3` → servicio en primer
  plano; force-stop + relanzar → valor persistido; `adb logcat -b crash` limpio.
