# Spec: Lote Top-5 mejoras (issues #24, #23, #28, #15, #17)

Fuente: backlog GitHub jpyunism/ollama-usage. 5 features independientes que
comparten el pipeline de refresco existente (`UsageRepository.propagate`).
Implementación en orden de valor: A → B → C → D → E, TDD RED→GREEN por feature.

---

## Feature A — Alerta temprana de ritmo (#24)

### Contexto

La proyección lineal ya existe (`linearProjection()` en `UsageHistory.kt`,
dibujada en Stats desde v0.29.0). Falta que la app **notifique** cuando la
proyección cruza 100% antes del reset, sin esperar el umbral de consumo
(80/95%).

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-001 | Tras cada refresh exitoso, calcular la proyección del período actual (semana y sesión si hay ancla) sobre los snapshots del histórico. Si `toPercent > 100` (la regresión cruza la cuota antes del reset), disparar una notificación de ritmo. |
| REQ-002 | La alerta de ritmo se dispara **una sola vez por período** (identificado por el `start` del período). No se repite aunque la proyección siga sobre 100%. Al cambiar de período, se puede volver a disparar. |
| REQ-003 | Solo aplica si las notificaciones están activadas (`NOTIF_ENABLED`) y hay ≥ 2 snapshots en el período. Sin ancla de reset (sesión con API key) no hay proyección → no alerta. |
| REQ-004 | Mensaje tipo: "A este ritmo llegarás al 100% antes del reset (proyección: 112%)". Título diferenciado de las alertas de umbral. |
| REQ-005 | Reutilizar el canal `usage_alerts` y el sink `alertNotifier` existente. Sin nuevos canales ni permisos. |

### Decisiones

- Lógica pura nueva en `UsageHistory.kt`: `paceAlert(snapshots, period, resetAnchor, now, selector) → PaceAlert?` (devuelve `toPercent` + `periodStart`), testeable en JVM.
- Guard de duplicados en prefs: clave `last_pace_period_<WEEK|SESSION>` con el `start` del período ya notificado (patrón `LAST_NOTIFIED_*` de `AlertEngine`).
- El chequeo vive en `UsageRepository.propagate()` (después de `historyStore.record`, que actualiza el StateFlow con snapshots frescos).

---

## Feature B — Backup/restore del historial de snapshots (#23)

### Contexto

Los snapshots (FIFO 600 ≈ 25 días) viven solo en SecurePrefs de la app. La
serialización ya existe: `UsageHistoryStore.encodeSnapshots()` /
`parseSnapshots()`. Solo falta el flujo de export/import en la UI.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-010 | Exportar: botón en Configuración genera el JSON del historial y lo comparte vía `ACTION_CREATE_DOCUMENT` (SAF, `application/json`, nombre sugerido `ollama-usage-backup-YYYYMMDD.json`). Sin permisos de almacenamiento. |
| REQ-011 | Importar: botón en Configuración abre `ACTION_OPEN_DOCUMENT` (JSON), parsea, **mergea** con el historial existente (dedupe por timestamp exacto), ordena por timestamp, aplica cap FIFO 600. |
| REQ-012 | Formato del archivo: el mismo array `[{"t":ms,"s":pct,"w":pct},...]` de `encodeSnapshots` — sin esquema nuevo (YAGNI). |
| REQ-013 | Archivo corrupto o vacío: mostrar feedback (snackbar/toast) y **no modificar** el historial existente. |
| REQ-014 | Tras import, el StateFlow del ViewModel se refresca (los snapshots importados aparecen en Stats al volver). |

### Decisiones

- `UsageHistoryStore.mergeSnapshots(imported): List<UsageSnapshot>` — función nueva testeable que hace merge+dedupe+sort+cap y persiste.
- SAF con `ActivityResultLauncher` en `SettingsTab` (patrón Compose estándar; sin deps nuevas).

---

## Feature C — Color semáforo por nivel de consumo (#28)

### Contexto

Los cards de % de sesión/semana en Uso (y la barra del widget) usan color fijo
del tema. El issue pide semáforo verde → ámbar → rojo según cercanía al
límite, con umbrales consistentes con las alertas.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-020 | % de sesión y semana en la pantalla Uso: verde si `< umbral alerta`, ámbar si `[alerta, crítica)`, rojo si `≥ crítica`. Umbrales leídos de prefs (los mismos de las alertas, default 80/95). |
| REQ-021 | Barra de progreso del widget 4×2 (y el compacto de Feature E): mismo criterio de semáforo. |
| REQ-022 | La función de mapeo es pura y testeada: `paceColor(percent, alert, critical) → semáforo` en un objeto puro (sin Android). |
| REQ-023 | Con alertas desactivadas (`NOTIF_ENABLED=false`) se usan igual los umbrales default 80/95 (el semáforo es visual, no depende de las notificaciones). |

### Decisiones

- Colores semánticos fijos (verde `0xFF2E7D32`, ámbar `0xFFF9A825`, rojo `0xFFC62828`) definidos en `ui/Theme.kt` como `Color` constantes con comentario justificativo — M3 no tiene semáforo en su palette y la regla del repo permite excepciones justificadas en el commit (precedente: paleta del widget).
- Mapeo central en objeto puro `TrafficLight.kt` para que app y widget (vía `UsageWidgetProvider`) usen el mismo criterio.

---

## Feature D — Ancla de reset semanal automática (#15)

### Contexto

Con API key, `weeklyResetAt` no existe y se sintetiza como próximo domingo
21:00 CLT (`fallbackResetAnchor`). Si Ollama resetea en otro momento, todas las
proyecciones/ideal/balance quedan corridas. El histórico permite detectar el
reset real: el % semanal **cae bruscamente** entre dos snapshots.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-030 | Detectar reset semanal: entre dos snapshots consecutivos, si `w` cae ≥ 15 puntos porcentuales y pasaron ≤ 4 h entre ellos, se asume reset en el timestamp del snapshot bajo. Detección pura y testeada. |
| REQ-031 | La ancla detectada se persiste en prefs (`weekly_reset_anchor`) y se usa como `resetAnchor` semanal en Stats/proyección/balance en lugar del fallback del domingo 21:00. |
| REQ-032 | Override manual: si el usuario configuró `weeklyResetAt` explícitamente (cookie/scraper entrega el valor real, o existe un ajuste manual), **no** se auto-corrije — la detección solo aplica cuando el ancla viene del fallback. |
| REQ-033 | La detección corre en cada refresh (en `propagate`, barato: O(n) sobre ≤ 600 snapshots). Si detecta un reset más reciente que el guardado, actualiza el prefs. |
| REQ-034 | La ancla persistida expira con el período: se guarda el timestamp del reset detectado; los cálculos de período avanzan el ancla en múltiplos de 168 h (comportamiento existente de `periodsFor`/`currentPeriod`). |

### Decisiones

- Función pura en `UsageHistory.kt`: `detectWeeklyReset(snapshots): Long?` (último reset detectado en el histórico).
- Falsos positivos mitigados por la regla de caída ≥ 15 pp en ≤ 4 h (un consumo natural baja el % solo al resetear).
- `UsageRepository` orquesta: si la fuente es API key (sin `weeklyResetAt` real), tras `historyStore.record()` detecta y persiste.

---

## Feature E — Widget compacto 2×1 (#17)

### Contexto

El widget 4×2 actual (`UsageWidgetProvider` + `widget_usage.xml`) es grande.
Un 2×1 con solo semana + barrita cubre el vistazo rápido. Los AppWidgets son
la excepción documentada a Material 3 (RemoteViews, vistas XML clásicas).

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-040 | Nuevo widget 2×1: % de semana grande + `ProgressBar` horizontal. Sin sesión, sin countdown. |
| REQ-041 | Mismo provider (`UsageWidgetProvider`) con segundo receiver en el manifest + `widget_compact_info.xml` (`minWidth`/`minHeight` para 2×1, `resizeMode` horizontal|vertical). Layout nuevo `widget_compact.xml` (vistas clásicas, excepción justificada). |
| REQ-042 | `updateAll()` actualiza ambas variantes con el mismo `UsageData` persistido. |
| REQ-043 | Barra del widget compacto con semáforo (REQ-021). |

### Decisiones

- Un solo provider con la lógica compartida; el layout se elige según el `appWidgetId` recibido en `onUpdate`/`onAppWidgetOptionsChanged` (o dos métodos `bind4x2`/`bind2x1` por id de layout).
- Sin clase nueva ni Receiver distinto: menos código, mismo ciclo de updates del scheduler.

---

## Fuera de alcance

- Histórico por modelo (#20), resumen diario (#19), comparativa semanas (#18), múltiples cuentas (#25), Roborazzi (#26), y todas las mejoras de pulido visual (#27, #29–#37, #38).
- Cambios de esquema del archivo de backup (versionado del formato) — YAGNI.

## Criterios de aceptación globales

1. Tests unitarios RED→GREEN por feature (JUnit4 + Mockk, JVM, patrón del repo).
2. `./gradlew testDebugUnitTest lintDebug assembleRelease` verde.
3. Validación en emulador AVD `test64` según AGENTS.md (flujos afectados, persistencia, sin crashes).
4. Release v0.31.0 (versionCode 42) con APK firmado + screenshots + envío por Telegram.