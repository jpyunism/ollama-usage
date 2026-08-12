# Spec: Balanza de consumo (déficit/superávit)

## Objective

En cada card de consumo (Sesión y Semana) de la pantalla principal, mostrar a la
derecha del tiempo restante hasta el reset si el consumo va en **déficit** o
**superávit** respecto al ritmo lineal esperado a esa altura del período.

- Déficit = hemos consumido MÁS de lo que correspondería a esta altura (vamos
  atrasados: quedará menos cuota para el resto del período).
- Superávit = hemos consumido MENOS de lo esperado (vamos con margen).

## Cálculo (función pura, testeable sin Android)

```
inicio      = resetAt - duración          (sesión: 24 h; semana: 168 h)
esperado%   = clamp(transcurrido / duración * 100, 0, 100)
delta (pp)  = consumoActual% - esperado%
```

- `delta > 0` → **déficit** (pp = delta)
- `delta < 0` → **superávit** (pp = |delta|)
- `delta ≈ 0` → sin etiqueta (en ritmo)

Supuestos:
- Reset semanal: domingo 21:00 CLT (el `weeklyResetAt` del API ya lo refleja;
  el cálculo usa solo el timestamp, sin zona explícita).
- Duración de sesión: 24 h, inicio = `sessionResetAt - 24h`.
- Si no hay `resetAt`, no se muestra nada (igual que hoy con el reset).

## UI

- Debajo de la barra de progreso, el texto del reset (existente) pasa a una `Row`:
  `Resetea en 12 h · Déficit 8%` (superávit: `· Superávit 5%`).
- Colores desde `MaterialTheme.colorScheme` (regla M3 del repo): déficit =
  `error`, superávit = `primary`.
- **Alcance (decidido)**: pantalla principal (cards Sesión/Semana), notificación
  persistente (línea de reset de cada cuota) y widget de home screen (línea de
  reset de sesión). Siempre visible, sin umbral mínimo de desvío.
- **Formato (decidido)**: `%` → "Déficit 8%" / "Superávit 5%" (con `formatPercent`).

## Commands

```
Test:  ./gradlew testDebugUnitTest
Lint:  ./gradlew lintDebug
Build: ./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/UsageData.kt   → computeBalance() (junto a formatPercent/formatReset)
app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageTab.kt → Row con reset + balanza
app/src/main/res/values/strings.xml + values-en/strings.xml   → strings déficit/superávit
app/src/test/java/com/jpyunism/ollamacloudusage/BalanceTest.kt → tests de la función pura
```

## Testing Strategy

- `BalanceTest.kt`: tabla con casos — mitad del período (esperado 50), inicio
  (esperado ~0), delta 0 (null), déficit, superávit, clamp cuando el reset ya
  pasó o falta poco.

## Boundaries

- Always: tests verdes + lint limpio antes de release; M3; strings es/en.
- Ask first: tocar la notificación persistente o el widget.
- Never: colores hardcodeados (regla M3); cambiar la duración de sesión/semana
  sin aprobación.

## Success Criteria

- [ ] `computeBalance` pura con tests (casos arriba).
- [ ] En ambas cards (sesión y semana) aparece la balanza a la derecha del reset.
- [ ] En ritmo exacto no muestra etiqueta.
- [ ] Suite completa verde + lint limpio.

## Open Questions

Resueltas: alcance = app + notificación persistente + widget; siempre visible
(sin umbral); formato `%`.
