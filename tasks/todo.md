# Tasks: Pull-to-refresh (REQ-001..008, spec-pull-to-refresh.md)

## T1 — RED: tests del ViewModel (REQ-006)

- [x] En `UsageViewModelTest.kt`:
  - `refresh` normal setea `Loading` de inmediato y `isRefreshing` true → false
    tras `advanceUntilIdle`.
  - `refresh(fromPull = true)` con Success previo NO cambia el estado a
    `Loading` (sigue `Success` tras la llamada) e `isRefreshing` true → false.
  - `refresh(fromPull = true)` con fallo: estado `Error` e `isRefreshing` en
    `false` (no queda pegada).
- Acceptance: referencian `vm.isRefreshing` y `refresh(fromPull = true)`
  (aún inexistentes).
- Verify: `./gradlew testDebugUnitTest` → **RED** (no compila). ✅ RED confirmado

## T2 — GREEN: ViewModel (REQ-003, REQ-005)

- [x] `UsageViewModel`: `_isRefreshing`/`isRefreshing: StateFlow<Boolean>`.
- [x] `refresh(fromPull: Boolean = false)`: con `fromPull` NO setea `Loading`;
      `isRefreshing = true` antes del launch, `false` en `finally`;
      `refreshJob?.cancel()` intacto.
- Acceptance: T1 verde; refrescos rápidos no se pisan.
- Verify: `./gradlew testDebugUnitTest` → **GREEN**. ✅
- Files: `UsageViewModel.kt`

## T3 — Pull en la tab Uso (REQ-001, REQ-004)

- [x] `UsageTab`: en la rama Success, envolver `SuccessContent` en
      `PullToRefreshBox` (import `androidx.compose.material3.pulltorefresh.*`);
      `isRefreshing` recolectado del vm; `onRefresh = { vm.refresh(fromPull =
      true) }`.
- [x] Botón "Actualizar" → `vm.refresh(fromPull = true)` (UX no destructiva).
- Acceptance: contenido visible durante el refresh; botón y gesto comparten
  el mismo refresh silencioso.
- Files: `ui/UsageTab.kt`

## T4 — Pull en la tab Estadísticas (REQ-002)

- [x] `StatsTab(history, isRefreshing, onRefresh)`: envolver todo en
      `PullToRefreshBox`; `EmptyStats` con `verticalScroll` (para que el gesto
      funcione sin datos).
- [x] `UsageScreen`: recolectar `vm.isRefreshing` y pasarla a `UsageTab` /
      `StatsTab(history, isRefreshing, onRefresh = { vm.refresh(fromPull = true) })`.
- Acceptance: pull funciona en Stats con y sin datos; StatsTab no recibe el vm.
- Files: `ui/StatsTab.kt`, `ui/UsageScreen.kt`

## T5 — Verificación

- [x] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes. ✅
      (34 tests, 0 errores lint)
- [x] Sin dependencias nuevas (`grep pulltorefresh` solo en material3).
- Files: —

## T6 — Emulador + release (REQ-008, AGENTS.md)

- [x] Bump `versionCode` 40 y `versionName` "0.28.0" en `app/build.gradle.kts`.
- [x] Validación en emulador (AVD `test64`): pull en Uso y Estadísticas con
      contenido y vacío, botón Actualizar, carga inicial, cambio de idioma no
      resetea la tab, logcat sin crash/ANR, screenshots. ✅
      - Pull en Uso: contenido desplazado + spinner circular visible (captura
        `screenshot-pull-refresh-spinner-uso.png`), "Actualizado" refrescó.
      - Pull en Estadísticas: spinner visible (captura
        `screenshot-pull-refresh-spinner-stats.png`).
      - Cambio de idioma (ES→EN→ES) mantiene la tab activa (sin regresión v0.22.1).
      - Force-stop + relanzar: carga normal, sin crash; logcat crash/ANR = 0.
- [x] Commit + push a `main` + tag `v0.28.0`. ✅ (commit 5d6f2b0)
- [x] Release en GitHub con APK firmado + screenshots (nombres descriptivos).
      ✅ https://github.com/jpyunism/ollama-usage/releases/tag/v0.28.0
- [x] Enviar APK por Telegram (chat 15710279). ✅
