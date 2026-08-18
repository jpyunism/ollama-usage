# Plan: Refactor de arquitectura — repositorio único, DI manual y calidad

Spec de referencia: `docs/spec-arquitectura-refactor.md` (REQ-001 a REQ-021).

## Overview

Refactor en 3 PRs apilados (práctica `gh stack` del workspace), cada uno
independiente y con tests+lint+build verdes:

1. **PR 1 — Base (REQ-001..010)**: `AppContainer` (DI manual), `UsageRepository`
   con pipeline único `refreshAndPropagate()` (fix del bug de alertas en FGS),
   `UsageError` sealed, `AlertEngine` puro, `PrefsKeys`, `HistoryPeriod` como
   fuente de duraciones, quitar `security-crypto`/`error_prone`, version
   catalog, ViewModel sin Context.
2. **PR 2 — ViewModel delgado + i18n (REQ-011..015)**: `UpdateRepository`,
   i18n real de `formatReset` (templates desde resources), hoist del flow de
   histórico, `appVersion` inyectada.
3. **PR 3 — Calidad/seguridad (REQ-016..021)**: verificación sha256 del APK,
   OkHttpClient compartido, cancelación de refrescos, widget con prefs claras,
   NPE fix en descarga, upgrade compose-bom.

## Architecture Decisions

- **DI manual con `AppContainer`** (patrón oficial Android): clase raíz que
  construye el grafo en `OllamaUsageApp`. Sin Hilt (overkill).
- **Pipeline único**: `UsageRepository.refreshAndPropagate()` ejecuta SIEMPRE
  fetch → widget → notif persistente → alertas → histórico → last_updated.
  VM/Worker/FGS solo invocan; la lógica de negocio vive en una sola parte.
- **`UsageError` sealed**: el mapeo Throwable→UsageError en el repository; el
  mapeo UsageError→String en la UI con resources.
- **Sin cambio de datos persistidos**: mismas keys (`PrefsKeys`),
  `UsageHistoryStore` intacto, widget solo cambia de contenedor de prefs.
- **i18n por templates**: `formatReset(..., strings: ResetStrings)`; las
  funciones puras siguen testeables sin Android.
- **Orden de merge**: PR1 → PR2 → PR3. Cada uno sobre el anterior
  (`gh stack`).

## Riesgos

- **Cambio de firma del constructor de `UsageViewModel`**: toca
  `UsageViewModelTest` (9+ usos). Se actualiza el helper `buildVm` del test en
  el mismo PR 1 (los tests no son API pública).
- **Upgrade BOM (REQ-021)**: riesgo bajo-medio de símbolos deprecados; se
  mitiga validando en emulador y ajustando si compila roto.
- **i18n de `formatReset` (REQ-012)**: los tests existentes
  (`ResetFormatTest`) cambian de firma — se reescriben con templates literales.
