# Plan: Lote 2 top-5 mejoras (spec `docs/spec-lote2-top5-mejoras.md`)

Orden de implementación A (#25 multi-cuenta) → B (#19 resumen diario) →
C (#20 histórico por modelo) → D (#18 comparativa semanal) → E (#22 compartir).
E es independiente; D depende del gráfico existente; A es la de mayor alcance.
TDD RED→GREEN por feature.

## Steps

### Feature A — Multi-cuenta API keys (#25)
1. **A1 (RED)** — `AccountStoreTest.kt`: JSON accounts en prefs; add/rename/remove/active; migración de API key única; sin cookie multi-cuenta.
2. **A2 (GREEN)** — `AccountStore` (puro + prefs).
3. **A3 (RED)** — `UsageHistoryStore` con `storageKey` paramétrico (default legacy).
4. **A4** — `UsageRepository`: credencial desde cuenta activa; history store por cuenta activa; test.
5. **A5** — `UsageViewModel.switchAccount(id)` + refresh; UI: switcher en Uso (chips) y gestión en Configuración (add/rename/remove); strings es/en; tests de VM.

### Feature B — Resumen diario programado (#19)
6. **B1 (RED)** — `nextDailyRunMillis` (hora futura hoy, mañana si pasó, zona) + `dailySummaryText` (% semana/sesión, proyección opcional, sin datos → null).
7. **B2 (GREEN)** — funciones puras.
8. **B3** — `DailySummaryWorker` (OneTime con delay, reprograma al terminar; refresh via repository; fallback al último snapshot si falla la red; notificación propia en canal `usage_alerts`).
9. **B4** — Settings: switch + time picker + reprogramación al cambiar; strings es/en.

### Feature C — Histórico por modelo (#20)
10. **C1 (RED)** — tests: snapshot con `models` (nullable), serialización `m` opcional, parse tolerante, dedupe considera modelos.
11. **C2 (GREEN)** — `UsageSnapshot.models`, encode/parse, `record` guarda weeklyModels, `modelPercent()`.
12. **C3** — UI: al tocar modelo en lista de Uso → bottom sheet con gráfico de evolución (reusa patrón UsageChart); estado vacío; strings.

### Feature D — Comparativa semana anterior (#18)
13. **D1 (RED)** — `comparisonSeries`: dos últimos períodos cerrados + actual, puntos (horas desde inicio, %), recorte a duración transcurrida.
14. **D2 (GREEN)** — función pura en `UsageHistory.kt`.
15. **D3** — `UsageChart` con parámetro `comparison` + overlay punteado + toggle chip (default ON, solo si hay datos); solo WEEK.

### Feature E — Compartir consumo (#22)
16. **E1 (RED)** — `shareSummaryText`: formato, top modelo semanal, sin modelos omite top.
17. **E2 (GREEN)** — función pura + strings.
18. **E3** — Botón Share en Uso: clipboard + ACTION_SEND chooser + snackbar "Copiado".

## Cierre
19. **V1** — Suite completa + lint + `assembleRelease` verde.
20. **V2** — Validación emulador test64 (AGENTS.md): flujos afectados, persistencia tras force-stop, ambos flujos de cuenta, logcat sin crash/ANR.
21. **V3** — Release v0.32.0 (versionCode 44) + APK firmado + screenshots + Telegram.