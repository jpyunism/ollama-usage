# Implementation Plan: Balanza de consumo (déficit/superávit)

## Overview

Mostrar en las cards de Sesión y Semana (app), en la notificación persistente y
en el widget, si el consumo va en déficit o superávit respecto al ritmo lineal
esperado del período. Reset semanal: domingo 21:00 CLT (viene en `weeklyResetAt`
del API; el cálculo usa solo timestamps, sin zona explícita).

## Architecture Decisions

- **Función pura `computeBalance(percent, resetAt, now, duration)`** en
  `Balance.kt` (nuevo, sin dependencias Android), mismo patrón que
  `formatPercent`/`formatReset` en `UsageData.kt`:
  - `inicio = resetAt - duration`; `esperado = clamp(elapsed/duration * 100, 0, 100)`
  - `delta = percent - esperado`; `delta > 0` → `DEFICIT`, `delta < 0` → `SURPLUS`
  - Fuera de rango (`now >= resetAt` o `now <= inicio`) o `resetAt == null` → `null`
  - `delta` redondeado a 1 decimal = 0 → `null` (en ritmo; evita "Déficit 0%")
- **`Balance`** = `data class Balance(status: BalanceStatus, percentDelta: Double)`.
- **Label**: helper puro `balanceLabel(balance, deficitTemplate, surplusTemplate)`
  → "Déficit 8%" / "Superávit 5%" usando `formatPercent`. Cada consumidor pasa
  los templates desde resources (es/en).
- **UI app**: el texto de reset pasa a `Row` con `·` separador: reset texto
  existente + label de balanza (déficit `colorScheme.error`, superávit `primary`).
- **Notificación persistente**: tras cada línea de reset (semana y sesión),
  agregar ` · Déficit 8%` dentro del mismo `buildString`.
- **Widget**: reusa `widget_session_reset` (TextView secundario ya existente):
  `"<reset> · Déficit 8%"` vía string compuesto. Sin colores extra (paleta fija
  del widget, excepción RemoteViews documentada).
- **Duración sesión = 24 h**; **semana = 168 h**. No hay config de duración.

## Task List

- [ ] Task 1: Balance.kt + BalanceTest.kt (TDD: tests primero)
- [ ] Task 2: UI UsageTab (Row reset + balanza en Sesión y Semana) + strings es/en
- [ ] Task 3: Notificación persistente (UsageNotifier) + strings es/en
- [ ] Task 4: Widget (UsageWidgetProvider) + strings widget
- [ ] Task 5: Release v0.17.0 (bump versionCode 24, tests+lint+assemble, tag, GitHub release, APK por Telegram)

### Checkpoint: Tasks 1-4
- [ ] BalanceTest verde + suite completa verde + lint limpio
- [ ] Balanza visible en app, notificación y widget (manual)

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Reset recién pasado → esperado 100 → falso superávit | Med | Fuera de rango (`now >= resetAt`) devuelve null; el API manda reset futuro |
| Ruido "Déficit 0%" | Bajo | delta redondeado a 1 decimal == 0 → null |
| Romper notificación/widget existentes | Med | Solo se agrega texto a líneas existentes; strings nuevos, sin tocar IDs de layout |

## Open Questions

Ninguna (resueltas en spec: alcance app+notificación+widget; siempre visible;
formato `%`).
