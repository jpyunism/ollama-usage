# Plan: Lote Top-5 mejoras (spec `docs/spec-lote-top5-mejoras.md`)

Orden de implementación A (#24 ritmo) → B (#23 backup) → C (#28 semáforo) →
D (#15 ancla) → E (#17 widget 2×1). C y D son independientes entre sí; E
depende de C (semáforo en la barra). TDD por feature.

## Steps

### Feature A — Alerta temprana de ritmo
1. **A1 (RED)** — `PaceAlertTest.kt`: proyección cruza 100 → alerta; no cruza → null; <2 snapshots → null; sin ancla → null; sesión con ancla también aplica.
2. **A2 (GREEN)** — `PaceAlert` + `paceAlert()` en `UsageHistory.kt`.
3. **A3** — `UsageRepository.propagate()`: chequear ritmo (semana; sesión si hay ancla) con guard por período en prefs; strings es/en; test de repo del disparo único.

### Feature B — Backup/restore
4. **B1 (RED)** — tests de `mergeSnapshots` (merge dedupe por timestamp, sort, cap 600).
5. **B2 (GREEN)** — `UsageHistoryStore.mergeSnapshots()`.
6. **B3** — `SettingsTab`: export con `ACTION_CREATE_DOCUMENT` + import con `ACTION_OPEN_DOCUMENT` vía launchers; feedback de error (REQ-013); refresco del StateFlow (REQ-014); strings es/en.

### Feature C — Semáforo
7. **C1 (RED)** — `TrafficLightTest.kt`: bordes (< alerta verde; = alerta ámbar; [alerta,crítica) ámbar; ≥ crítica roja; umbrales custom).
8. **C2 (GREEN)** — `TrafficLight.kt` + colores en `Theme.kt`.
9. **C3** — Aplicar a % de sesión/semana en `UsageTab` y a la barra del widget (`UsageWidgetProvider.saveData` calcula color).

### Feature D — Ancla semanal automática
10. **D1 (RED)** — tests de `detectWeeklyReset`: caída ≥15pp en ≤4h → reset; caída gradual → null; sin datos → null; último reset gana.
11. **D2 (GREEN)** — `detectWeeklyReset()` en `UsageHistory.kt`.
12. **D3** — `UsageRepository`: si auth=API key y no hay `weeklyResetAt` real, detectar y persistir `weekly_reset_anchor`; exponerlo para la UI/Stats (consumidores del ancla).

### Feature E — Widget 2×1
13. **E1** — `widget_compact_info.xml` (receiver 2×1 en manifest) + `widget_compact.xml`.
14. **E2** — `UsageWidgetProvider`: bind por variante, `updateAll` actualiza ambas, semáforo en la barra (REQ-043).
15. **E3** — Verificación en emulador: agregar ambos widgets al launcher, verificar updates.

### Cierre
16. **V1** — Suite completa + lint + build release.
17. **V2** — Validación emulador AVD `test64` (AGENTS.md): alerta de ritmo visible en flujo, export/import en Configuración, semáforo en Uso/widget, ancla detectada en Stats, ambos widgets funcionando; persistencia tras force-stop; sin crashes.
18. **V3** — Release v0.31.0 (versionCode 42): bump, tag, release GitHub con APK + screenshots, envío por Telegram (chat 15710279).

## Architecture Decisions

- Toda la lógica nueva pura y testeable en JVM (`UsageHistory.kt`, `TrafficLight.kt`, `mergeSnapshots`), patrón del repo.
- Side-effects solo en `UsageRepository.propagate()` y `UsageWidgetProvider`.
- Sin dependencias nuevas (SAF nativo, RemoteViews clásicos para widgets).
- Strings es/en para todo lo visible.

## Riesgos

- **Falsos positivos de la detección de reset (D)**: mitigado con umbral 15pp/4h; el override manual siempre gana.
- **Proyección ruidosa (A)**: requiere ≥2 snapshots; la notificación única por período evita spam.
- **Widgets en emulador (E)**: verificar con launcher real del AVD; los widgets no corren en previews de Compose.