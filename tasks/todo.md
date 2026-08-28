# Todo: Lote 2 top-5 mejoras

## Feature A — Multi-cuenta API keys (#25)
- [x] A1 (RED) AccountStoreTest: JSON accounts, CRUD, activa, migración
- [x] A2 (GREEN) AccountStore
- [x] A3 (RED) UsageHistoryStore con storageKey paramétrico
- [x] A4 Repo: credencial + history por cuenta activa
- [ ] A5b UI (chips en Uso, gestión en Configuración) + strings

## Feature B — Resumen diario programado (#19)
- [ ] B1 (RED) nextDailyRunMillis + dailySummaryText
- [ ] B2 (GREEN) funciones puras
- [ ] B3 DailySummaryWorker (delay, reprograma, fallback snapshot)
- [ ] B4 Settings switch + time picker + reprogramación + strings

## Feature C — Histórico por modelo (#20)
- [ ] C1 (RED) tests snapshot.models + serialización m + dedupe
- [ ] C2 (GREEN) UsageSnapshot.models + encode/parse + record + modelPercent
- [ ] C3 UI bottom sheet evolución por modelo + strings

## Feature D — Comparativa semana anterior (#18)
- [ ] D1 (RED) comparisonSeries (últimos 2 períodos, horas vs %, recorte)
- [ ] D2 (GREEN) función pura
- [ ] D3 Overlay en UsageChart + toggle + solo WEEK

## Feature E — Compartir consumo (#22)
- [ ] E1 (RED) shareSummaryText
- [ ] E2 (GREEN) función pura + strings
- [ ] E3 Botón Share: clipboard + ACTION_SEND + snackbar

## Cierre
- [ ] V1 Suite completa + lint + assembleRelease
- [ ] V2 Validación emulador test64 (AGENTS.md)
- [ ] V3 Release v0.32.0 (versionCode 44) + APK + screenshots + Telegram