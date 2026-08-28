# Todo: Lote Top-5 mejoras

## Feature A — Alerta temprana de ritmo (#24)
- [x] A1 (RED) PaceAlertTest: cruza 100 → alerta; no cruza → null; <2 snapshots → null; sin ancla → null
- [x] A2 (GREEN) PaceAlert + paceAlert() en UsageHistory.kt
- [x] A3 Chequeo en UsageRepository.propagate + guard por período + strings es/en

## Feature B — Backup/restore snapshots (#23)
- [x] B1 (RED) tests mergeSnapshots (dedupe, sort, cap 600)
- [x] B2 (GREEN) UsageHistoryStore.mergeSnapshots()
- [x] B3 UI export/import SAF en SettingsTab + feedback + strings

## Feature C — Semáforo (#28)
- [x] C1 (RED) TrafficLightTest (bordes y umbrales)
- [x] C2 (GREEN) TrafficLight.kt + colores Theme.kt
- [x] C3 Aplicar en UsageTab y barra del widget

## Feature D — Ancla semanal automática (#15)
- [x] D1 (RED) tests detectWeeklyReset (caída ≥15pp ≤4h, gradual → null)
- [x] D2 (GREEN) detectWeeklyReset() en UsageHistory.kt
- [x] D3 Persistencia + uso del ancla en repo/UI

## Feature E — Widget 2×1 (#17)
- [ ] E1 widget_compact_info.xml + manifest + widget_compact.xml
- [ ] E2 UsageWidgetProvider bind por variante + updateAll ambas + semáforo
- [ ] E3 Verificación emulador de ambos widgets

## Cierre
- [ ] V1 Suite completa + lint + build release
- [ ] V2 Validación emulador test64 (AGENTS.md)
- [ ] V3 Release v0.31.0 (versionCode 42) + APK + screenshots + Telegram