# Plan: Pull-to-refresh (swipe hacia abajo) en Uso y Estadísticas

Spec de referencia: `docs/spec-pull-to-refresh.md` (REQ-001 a REQ-008).

## Overview

Feature pequeña y acotada, sin dependencias nuevas (`PullToRefreshBox` ya viene
en material3 1.4.0). El ViewModel gana una bandera `isRefreshing` y un refresh
"silencioso" (`fromPull = true`) que NO oculta el contenido con el spinner
full-screen; las tabs Uso y Estadísticas se envuelven en `PullToRefreshBox`.

## Steps

1. **T1 (RED)** — Tests del ViewModel: `isRefreshing` on/off, refresh silencioso
   no toca `Loading`, error no deja la bandera pegada.
2. **T2 (GREEN)** — `UsageViewModel.refresh(fromPull)` + `isRefreshing`.
3. **T3** — `UsageTab`: `PullToRefreshBox` en la rama Success; botón
   "Actualizar" pasa a refresh silencioso.
4. **T4** — `StatsTab`: nueva firma (`history, isRefreshing, onRefresh`) +
   `PullToRefreshBox`; `UsageScreen` hoistea las props; `EmptyStats` se hace
   scrollable para que el gesto funcione.
5. **T5** — Verificación: `testDebugUnitTest lintDebug assembleRelease`.
6. **T6** — Validación en emulador (AGENTS.md) + release v0.28.0 (versionCode
   40) con APK firmado + screenshots.

## Architecture Decisions

- **`isRefreshing` separado de `UiState`**: `UiState` describe el contenido;
  el indicador de pull necesita su propio estado booleano, testeable.
- **`refresh(fromPull: Boolean = false)`**: default preserva call sites
  existentes (init, saveCookie, saveApiKey) con la UX de carga completa.
- **`StatsTab` sin ViewModel**: recibe `isRefreshing` y `onRefresh` por
  parámetro (state hoisting, mantiene el desacoplamiento actual).
- **Sin strings nuevos**: el indicador M3 no lleva texto; el botón ya tiene
  `R.string.refresh`.

## Riesgos

- **PullToRefreshBox necesita hijo scrollable**: `EmptyStats` (Stats sin
  snapshots) no lo es → se le agrega `verticalScroll` (mínimo).
- **Regresión v0.22.1 (idioma → tab)**: no se toca `rememberPagerState`; la
  verificación en emulador cubre el caso.
- **Bandera pegada en true**: `finally` en la corutina del refresh; test
  explícito para el caso de error.
