# 11 — Propuestas: control de frecuencia de refresco con mayor precisión

> Documento de decisión. La spec definitiva (contrato de implementación) se
> escribe solo para la opción que elija JuanPa.

## Estado actual (medido en código)

`feature/settings/.../RefreshSection.kt:36-47`

```kotlin
Slider(
    value = settings.refreshIntervalMinutes.toFloat(),
    onValueChange = { onSave(settings.copy(refreshIntervalMinutes = it.toInt())) },
    valueRange = 1f..720f,
    steps = 30,
)
```

- Rango 1–720 min con **31 posiciones discretas**: cada paso salta ~23 min.
  No se puede elegir 5, 10, 20, 45 ni 90 minutos. Eso es lo impreciso.
- Motor (`UsageScheduler`):
  - `>= 15 min` → WorkManager periódico (`ExistingPeriodicWorkPolicy.UPDATE`).
  - `1–14 min` → `UsageMonitorService` en primer plano con notificación
    persistente (`delay(interval)` en corrutina) y backoff exponencial ante
    fallos.
- **Deuda detectada:** el FGS es `dataSync` con `targetSdk 36`. Android 15+
  impone un tope de **6 h/día** para ese tipo de FGS y hoy no se maneja: con
  refresco de 1–14 min el servicio se corta a mitad de día y el refresco queda
  en manos del fallback de WorkManager (15 min) sin avisar.

## Límites del sistema (lo que ninguna UI puede romper)

| Motor | Mínimo | Exactitud real |
|---|---|---|
| WorkManager periódico | 15 min | No exacta: batching + Doze; puede retrasarse horas |
| FGS con `delay()` | cualquiera | Exacta mientras viva; **cap 6 h/día** en Android 15+ |
| `AlarmManager.setAndAllowWhileIdle` | — | ±1 min con pantalla activa; en Doze ~9–15 min entre disparos |
| `AlarmManager.setExactAndAllowWhileIdle` | — | Al segundo, pero exige `SCHEDULE_EXACT_ALARM`, permiso que Play restringe a apps de alarma/calendario |

## Propuestas

### P1 — Presets + minuto exacto (solo UI)

Chips `1 · 5 · 10 · 15 · 30 · 1 h · 2 h · 6 h · 12 h` + botones −/+ de 1 min y
campo numérico para cualquier valor entre 1 y 720. El texto indica qué motor se
activará ("servicio en primer plano" / "WorkManager").

- **Precisión de configuración:** al minuto.
- **Precisión de ejecución:** la misma de hoy (cambia el control, no el motor).
- **Esfuerzo:** bajo — 1 archivo de UI + strings + test de la lógica de formateo.
- **Riesgo:** ninguno.

### P2 — Selector por motor + transparencia + fix del cap 6 h (UI + scheduler)

Tres modos con nombre propio, cada uno mapeado a su motor real:

| Modo | Rango | Motor | Qué promete |
|---|---|---|---|
| Tiempo real | 1–5 min | FGS (con fix del cap: al cortarse cae a 15 min y avisa) | Lo más al día posible |
| Equilibrado | 10–30 min | FGS si <15, WorkManager si ≥15 | Compromiso |
| Ahorro | 60–720 min | WorkManager | Batería |

Dentro de cada modo, ajuste al minuto (reusa el control de P1).

- **Precisión:** configuración al minuto + expectativa honesta de ejecución.
- **Esfuerzo:** medio (UI + `UsageScheduler` + servicio + strings).
- **Riesgo:** bajo; corrige un bug real (cap de 6 h).

### P3 — Alarmas inexactas para 5–60 min, sin notificación permanente (motor nuevo)

`AlarmManager.setAndAllowWhileIdle` + `BroadcastReceiver` que refresca y
reprograma. Elimina la notificación permanente del FGS en el rango medio.

- **Precisión:** ±1 min con pantalla activa; ~9–15 min en Doze.
- **Esfuerzo:** medio-alto (receiver, reprogramación, supervivencia a reboot,
  convivencia con WorkManager y con el FGS).
- **Riesgo:** medio; los OEMs agresivos (Samsung/Xiaomi) matan alarmas.

### P4 — Alarmas exactas al segundo (`SCHEDULE_EXACT_ALARM`)

Precisión de reloj, pero Play restringe el permiso a apps de alarma/calendario y
el usuario debe concederlo a mano. Riesgo de rechazo en revisión.

- **Esfuerzo:** medio.
- **Riesgo:** alto (política Play). **No recomendada.**

## Recomendación

**P1 + P2 juntas**: arregla el control (precisión al minuto, que es lo que se
pide) y de paso la mentira de precisión del rango 1–14 min (cap de 6 h), sin
meter alarmas ni permisos nuevos. P3 solo si aparece la necesidad real de
precisión sin notificación permanente.

## Pendiente de decisión

- ¿P1, P1+P2, P2 sola o P3?
- ¿Qué valores en los presets? (propuesta: 1, 5, 10, 15, 30, 60, 120, 360, 720)
- ¿El campo numérico admite segundos? (propuesta: no, mínimo 1 min)
