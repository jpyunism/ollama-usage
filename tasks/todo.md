# Tasks: Línea de proyección + consumo ideal (REQ-001..009, spec-proyeccion-consumo.md)

## T1 — RED: `ProjectionTest.kt` (REQ-001, REQ-009)

- [x] Tests de `currentPeriod(snapshots, period, resetAnchor, now, selector)`:
  - Sin anchor → null.
  - Período correcto: `end` = próximo reset futuro (> now), `start` = end −
    duration; snapshots del período incluidos.
  - Proyección con 2 puntos con pendiente conocida: `toTimestamp == end` y
    `toPercent` = valor exacto de la regresión lineal.
  - Pendiente que supera 100: `toPercent > 100` (sin clamp).
  - Con 1 snapshot en el período: `projection == null`, `snapshotCount == 1`.
  - Snapshots de períodos anteriores se excluyen del cálculo.
  - `now` justo antes de un reset: `end` es ESE reset (no el siguiente).
- [x] Tests de `fallbackResetAnchor`: semana → próximo domingo 21:00 CLT
      (martes→domingo y caso justo antes del domingo 21); sesión → null.
- Acceptance: referencian `currentPeriod`/`Projection`/`fallbackResetAnchor`
  (aún inexistentes).
- Verify: `./gradlew testDebugUnitTest` → **RED** (no compila). ✅ RED confirmado

## T2 — GREEN: lógica pura en `UsageHistory.kt` (REQ-001)

- [x] `data class Projection(fromTimestamp, fromPercent, toTimestamp, toPercent)`.
- [x] `data class CurrentPeriod(start, end, snapshotCount, projection)`.
- [x] `linearProjection(snapshotsInPeriod, end, selector)`: mínimos cuadrados
      (x = timestamp, y = %); con < 2 puntos → null; toPercent sin clamp.
- [x] `currentPeriod(...)`: próximo reset = primero > now alineado a duration;
      filtra snapshots en [start, end); arma `CurrentPeriod`.
- [x] `fallbackResetAnchor(period, now, zone)`: semana → próximo domingo
      21:00 CLT; sesión → null (no sintetizable).
- Acceptance: T1 verde.
- Verify: `./gradlew testDebugUnitTest` → **GREEN**. ✅ (12 tests)
- Files: `UsageHistory.kt`

## T3 — `HistoryState.sessionResetAt` (REQ-008)

- [x] `HistoryState` + `sessionResetAt: Instant? = null`.
- [x] `UsageViewModel.refresh()`: popular con `data.sessionResetAt`;
      `loadHistory()` sigue con null.
- Acceptance: tests existentes del VM verdes (default null). ✅
- Files: `UsageViewModel.kt`

## T4 — StatsTab: dibujo + leyenda + resumen (REQ-002..006)

- [x] `UsageChart` recibe `current` (período actual con proyección) y usa
      `anchor` real o `fallbackResetAnchor` (semana) según la fuente.
- [x] Rango X: `xFor` usa `max(lastSnapshot, projection.toTimestamp)` cuando
      hay proyección (REQ-003). `xToTimestamp` (tooltip) igual.
- [x] Línea ideal: `tertiary` discontinua, de (start, 0%) a (end, 100%),
      recortada al rango visible; solo si hay ≥1 snapshot en el período actual
      (REQ-004).
- [x] Línea de proyección: de (fromTimestamp, fromPercent) a (toTimestamp,
      toPercent), color `error` si toPercent > 100 sino `secondary`, punto en
      el extremo + etiqueta "%" reubicada si sale del borde (REQ-002).
- [x] Leyenda bajo el Canvas: swatch + "Ideal" (si aplica) y swatch +
      "Proyección: X%" (si hay proyección) (REQ-005).
- [x] Nueva `SummaryRow`: "A este ritmo terminarías en %P%%" (solo con
      proyección), tras la fila del período actual (REQ-006).
- Acceptance: con datos reales se ven las 3 líneas diferenciadas; sin datos
  del período actual no aparece nada extra (REQ-007). ✅
- Files: `ui/StatsTab.kt`

## T5 — Strings es/en (REQ-005, REQ-006)

- [x] `values/strings.xml`: `stats_ideal`, `stats_projection`,
      `stats_projection_summary`.
- [x] `values-en/strings.xml`: "Ideal", "Projection: %1$s",
      "At this rate you\'d end at %1$s%%".
- Acceptance: `grep stats_` muestra las 3 keys en ambos locales. ✅
- Files: `app/src/main/res/values/strings.xml`, `values-en/strings.xml`

## T6 — Verificación

- [x] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes. ✅
      (12 tests de proyección; 0 errores lint)
- [x] Sin dependencias nuevas.
- Files: —

## T7 — Emulador + release (REQ-009, AGENTS.md)

- [x] Bump `versionCode` 41 y `versionName` "0.29.0" en `app/build.gradle.kts`.
- [x] Validación en emulador (AVD `test64`):
  - Semana (con API key): Ideal discontinua + Proyección ~184% + leyenda +
    "A este ritmo terminarías en X%" (capturas).
  - Sesión (con API key): sin ideal/proyección (correcto: la API no expone
    resets de sesión; la ventana móvil no es sintetizable).
  - Force-stop + relanzar OK; sin crashes de la app (el FATAL visto en logcat
    fue del propio `uiautomator dump` del host, no de la app).
- [ ] Commit + push a `main` + tag `v0.29.0`.
- [ ] Release en GitHub con APK firmado + screenshots (nombres descriptivos).
- [ ] Enviar APK por Telegram (chat 15710279).
