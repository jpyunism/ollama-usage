# 10 — Fix: API key rota por el cambio de contrato de `/api/usage`

## Objetivo

Configurar Ollama Cloud con una API key válida debe mostrar el consumo real
(sesión, semana, desglose por modelo) en vez de `network error: No value for
limits`.

## Por qué

Evidencia con una key real (2026-11-15, HTTP 200):

```
GET https://ollama.com/api/usage   (Bearer)
{"range":"7d","scope":"self","granularity":"day","from":"…","totals":{"request_count":3063},"buckets":[…]}
```

La respuesta ya no trae `limits.session` ni `limits.weekly`. `OllamaApiUsage`
hacía `root.getJSONObject("limits")`, que lanza `JSONException: No value for
limits`; `UsageError.fromThrowable` lo mapea a `Network` y la UI lo muestra como
error de red. `ollama/ollama#19511` confirma el cambio de contrato.

Reemplazo oficial: `GET /api/balance` (`ollama/ollama#18829`):

```json
{"included":{"session":{"remaining_percent":94.91,"resets_at":"2026-11-15T22:00:00Z"},
             "weekly":{"remaining_percent":84.17,"resets_at":"2026-11-16T00:00:00Z"}},
 "purchased":{"balance_usd":0}}
```

`remaining_percent` es lo que **queda**: el consumo es `100 - restante`.

## Alcance y límites

- `core/data/.../OllamaApiUsage.kt` — fuente de datos y parseo.
- `core/data/src/test/.../OllamaApiUsageTest.kt` — regresión del formato nuevo.

**NO** tocar: `OllamaApiKeyValidator` (sigue usando `/api/usage` como ping y
sigue devolviendo 200), `UsageRepository`, la UI ni `UsageData`.

## Contexto embebido

- `OllamaApiUsage : UsageScraper` es la única implementación con API key;
  `UsageRepository` la inyecta como `apiScraper`.
- El desglose por modelo vive en `/api/usage?range=24h|7d&group_by=model` →
  `totals.groups[] = {model, request_count}`.
- Ambos endpoints comparten un límite de 10 req/min por usuario; el fetch hace 3
  llamadas. El desglose se pide best-effort (`runCatching` → lista vacía).
- `UsageData.sessionResetAt` / `weeklyResetAt` son `Instant?`; `resets_at` viene
  en ISO-8601 UTC.

## Criterios de aceptación

1. **Dado** el JSON de `/api/balance` de arriba, **cuando** se parsea, **entonces**
   `sessionPercent = 5.09`, `weeklyPercent = 15.83` y ambas fechas de reset están
   presentes.
2. **Dado** el esquema de créditos (`allowance_usd=60`, `balance_usd=45`),
   **entonces** el consumo es 25 %.
3. **Dado** un JSON sin `included` ni `limits`, **entonces** se lanza
   `UnsupportedUsageResponse` (mensaje legible), no un `JSONException` críptico.
4. **Dado** el endpoint de grupos por modelo, **entonces** se listan los modelos
   con su porcentaje derivado de `request_count`.
5. Con una key real, la app muestra sesión/semana/modelos sin errores.

## Evidencia

- Captura del emulador con la key real: `5.9% used · Resets in 52 min`,
  `16% used · Resets in 2 h 52 min`, desglose por modelo.
- Tests de `:core:data` verdes.
