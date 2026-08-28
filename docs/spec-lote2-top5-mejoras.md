# Spec: Lote 2 top-5 mejoras (issues #25, #19, #20, #18, #22)

Fuente: backlog GitHub jpyunism/ollama-usage. 5 features independientes sobre
la base del lote 1 (v0.31.0). Implementación en orden de dependencia/valor:
A → B → C → D → E, TDD RED→GREEN por feature. Sin dependencias nuevas.

---

## Feature A — Múltiples cuentas/API keys con switcher (#25)

### Contexto

Hoy la app guarda UNA credencial (`API_KEY` o `COOKIE` en prefs). El issue
pide monitorear varias cuentas y cambiar entre ellas rápido. Alcance:
multi-cuenta para **API keys**; la cookie queda como método legacy de una
sola cuenta (no se complica el scraper de cookie).

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-101 | Lista de cuentas en prefs: JSON `accounts` = `[{"id":uuid,"label":str,"apiKey":str}]`. CRUD completo: agregar (desde Configuración), renombrar, eliminar, y elegir la activa (`active_account_id`). |
| REQ-102 | Cambiar de cuenta activa dispara un refresh inmediato y actualiza todas las superficies (Uso, notificación persistente, widget) con los datos de la nueva cuenta. |
| REQ-103 | El histórico de snapshots es **por cuenta**: cada cuenta tiene su propio JSON de snapshots (clave `usage_history_<accountId>`); el historial sin sufijo (`usage_history`) se mantiene como el de la cuenta legacy/única inicial. |
| REQ-104 | El historial de la cuenta activa alimenta Stats/proyección/anclas/balance. Al cambiar de cuenta, Stats muestra el histórico de esa cuenta. |
| REQ-105 | Migración: si existe API key única previa, se importa como primera cuenta (label "Cuenta 1") y queda activa. Sin API key (solo cookie) no hay lista y todo funciona como hoy. |
| REQ-106 | El switcher es accesible desde la pantalla Uso (chips/selector compacto) y su gestión (agregar/renombrar/eliminar) desde Configuración. |
| REQ-107 | Errores por cuenta: si el refresh de la activa falla con credencial inválida, el error identifica la cuenta (texto existente de InvalidApiKey basta). |

### Decisiones

- `AccountStore` (objeto nuevo, lógica pura sobre JSON + prefs): `list/add/rename/remove/active`, testeado en JVM con prefs in-memory.
- `UsageHistoryStore` gana parámetro `storageKey` (default `KEY_HISTORY`); el repo crea el store según la cuenta activa.
- `UsageRepository` lee la credencial de la cuenta activa (o la legacy si no hay lista). El switcher es una función del VM: `switchAccount(id)` → persiste activo + `refresh()`.
- Sin Room ni DataStore (YAGNI): JSON en prefs como el resto.

---

## Feature B — Resumen diario programado (#19)

### Contexto

Las notificaciones actuales son por umbral o ritmo. Falta un resumen puntual
programado ("hoy consumiste X% de la semana, te queda Y% a este ritmo") a una
hora elegida por el usuario.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-110 | Ajuste en Configuración: switch "Resumen diario" + hora (time picker, default 21:00). Persiste en prefs (`DAILY_SUMMARY_ENABLED`, `DAILY_SUMMARY_HOUR`, `DAILY_SUMMARY_MINUTE`). |
| REQ-111 | A la hora configurada, un worker (WorkManager `OneTimeWorkRequest` con delay hasta la próxima ocurrencia; se reprograma tras cada ejecución) hace refresh (reusa `refreshAndPropagate`) y notifica el resumen en el canal `usage_alerts` con ID de notificación propio. |
| REQ-112 | Mensaje del resumen: % semanal, % de sesión y, si hay proyección del período semanal, "a este ritmo llegarías a Z%". Generado por función pura `dailySummaryText(...)` testeada. |
| REQ-113 | Si el refresh falla (sin red), se notifica un resumen con los últimos datos del histórico (último snapshot) y timestamp de "última actualización". Si no hay datos, no se notifica nada. |
| REQ-114 | Reprogramación: al cambiar hora/switch, o al reiniciar el dispositivo (boot receiver ya existente si lo hay; si no, el worker se reprograma desde `schedule()` de la app). |

### Decisiones

- `DailySummaryWorker` (CoroutineWorker): refresh + notificación. Lógica de texto en función pura.
- Programación helper `nextDailyRunMillis(hour, minute, now, zone)` en código puro testeado.
- Sin permisos nuevos: usa el canal existente y `POST_NOTIFICATIONS` ya pedido.

---

## Feature C — Histórico por modelo (#20)

### Contexto

Los snapshots solo guardan `%` de sesión/semana; el desglose por modelo se
pierde. El issue pide tocar un modelo y ver su evolución.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-120 | Extender `UsageSnapshot` con desglose opcional por modelo: `models: Map<String, Double>` (% por modelo del período semanal) — nullable/null para snapshots antiguos (compatibilidad hacia atrás al parsear JSON). |
| REQ-121 | Serialización: campo `"m": {"modelo": pct}` en el JSON (ausente = sin datos). El parseo tolera entradas sin `m`. |
| REQ-122 | Grabación: en cada refresh, el snapshot guarda el % de cada modelo de `weeklyModels` (los requests/percent ya vienen de la fuente). El dedupe existente (15 min, mismos %) se mantiene — cambia también si cambia la distribución de modelos. |
| REQ-123 | Detalle por modelo: al tocar un modelo en la lista de Uso se abre una vista (bottom sheet o pantalla) con: gráfico de línea del % del modelo sobre los snapshots que lo tienen, y % actual. Reusa el patrón de dibujo de `UsageChart`. |
| REQ-124 | Sin datos suficientes (< 2 snapshots con ese modelo) se muestra el estado vacío correspondiente. |
| REQ-125 | Backup/export (Feature B lote 1) incluye `m` automáticamente (misma serialización). |

### Decisiones

- Selector nuevo: `modelPercent(snapshot, model)` (null si el modelo no está).
- El merge de backup usa `distinctBy(timestamp)` existente — con modelos incluidos, el snapshot completo va junto.
- Cap FIFO 600 sin cambios (el `m` crece el JSON ~1-2 KB por snapshot; aceptable en prefs).

---

## Feature D — Comparativa semana actual vs anterior (#18)

### Contexto

Stats ya dibuja la línea del consumo semanal. Falta comparar contra la
semana anterior, alineada por tiempo transcurrido desde el reset.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-130 | Función pura `comparisonSeries(snapshots, anchor, now)` → par de series: (actual, previa) donde cada punto es (% semanal, horas desde el inicio de su período). La serie previa se recorta a la duración transcurrida de la actual. |
| REQ-131 | En el gráfico de Stats (modo Semana), overlay de la semana anterior como línea punteada gris (`onSurfaceVariant`) con leyenda "Semana anterior". Toggle (chip/icono) para mostrar/ocultar, default ON. |
| REQ-132 | La comparativa solo aplica a WEEK (la sesión con API key no tiene ancla real; con ancla de sesión por cookie funciona igual vía el ancla existente). |
| REQ-133 | Sin datos de la semana anterior (o < 2 snapshots) no se dibuja nada y el toggle no aparece. |

### Decisiones

- Reusa `periodsFor(snapshots, WEEK, anchor)`: los dos últimos grupos cerrados son "actual" y "anterior".
- Dibujo dentro de `UsageChart` con parámetro opcional `comparison: List<Pair<Float, Double>>` (horas→%), sin tocar la firma actual para otros usos.

---

## Feature E — Compartir estado de consumo (#22)

### Contexto

Un toque para compartir/copiar el estado actual sin screenshot.

### Requisitos

| ID | Requisito |
|----|-----------|
| REQ-140 | Función pura `shareSummaryText(data, othersLabel)` → "📊 Ollama Cloud (plan): semana X% · sesión Y% · top: modelo Z%". Top = modelo con mayor % de la semana; sin modelos → se omite la parte "top". |
| REQ-141 | Botón "Compartir" (icono Share) junto a los botones Actualizar/Cambiar acceso en Uso. Al tocar: copia al portapapeles + abre share sheet (ACTION_SEND, text/plain) con el mismo texto. |
| REQ-142 | i18n: textos base desde strings.xml (los % y modelos interpolados); ES/EN. |

### Decisiones

- Sin librerías: `ClipboardManager` + `Intent.createChooser`. Snackbar de confirmación "Copiado" reusa el snackbar host existente.

---

## Fuera de alcance

- Multi-cuenta con cookies de sesión (solo API keys).
- Histórico por modelo en el widget o en notificaciones.
- Export CSV (#16), Roborazzi (#26), pulido visual (#27, #29–#37, #38).
- Comparativa por sesión (solo semanal).

## Criterios de aceptación globales

1. Tests unitarios RED→GREEN por feature (JUnit4 + Mockk, JVM, patrón del repo).
2. `./gradlew testDebugUnitTest lintDebug assembleRelease` verde.
3. Validación en emulador AVD `test64` según AGENTS.md (flujos afectados, persistencia tras force-stop, sin crashes).
4. Release v0.32.0 (versionCode 44) con APK firmado + screenshots + envío por Telegram.