# Spec: Gráfico de barras de consumo por período (Stats)

## Objective

En la sección **Estadísticas**, agregar un **gráfico de barras** que muestre el
% de consumo alcanzado **antes de cada reset**: una barra por semana (o por
sesión, según el toggle) con el pico de consumo de ese período. Da una vista
rápida de "cuánto se consumió al finalizar cada semana/sesión".

## Fuente de datos

Reutiliza la infraestructura existente de `UsageHistory.kt` (snapshots
acumulados localmente, `periodsFor` + `peakPercent`). No hay datos nuevos que
persistir.

- Nueva función pura `periodBars(snapshots, period, resetAnchor, now, selector)`:
  devuelve `List<PeriodBar>` — una entrada por período con datos (los períodos
  vacíos se omiten, igual que `periodsFor`), en orden cronológico.
- `PeriodBar(start, end, peakPercent, inProgress)`:
  - `peakPercent` = pico (máx %) del período = "consumo antes del reset"
    (decisión del usuario: **pico**, consistente con el resumen existente).
  - `inProgress = end > now` (decisión del usuario: **incluir el período
    actual en curso** como barra, distinguible visualmente).
- Implementación: `periodsFor(...).map { g -> PeriodBar(g.start, g.end,
  peakPercent(g, selector) ?: 0.0, g.end > now) }`. Sin lógica nueva de
  agrupación — reutiliza la existente (misma alineación a resetAnchor, mismos
  períodos omitidos).

## UI

- **Nueva card "Consumo por período"** **debajo** de la card del gráfico de
  línea existente (se agrega, no reemplaza). Comparte el mismo toggle
  **Semana / Sesión** de la card superior (el `period` state ya vive en
  `StatsTab`).
- **Compose Canvas** (sin librerías de charts — YAGNI, stdlib primero).
- Eje Y: 0–100 con grid y labels 0 / 50 / 100 (mismo patrón del gráfico actual:
  `Y_LABELS`, `yFor`).
- Eje X: label con la fecha de inicio de cada período (`formatDate`, "3 ago").
- Barras: una por período, ancho proporcional al slot disponible, esquinas
  superiores redondeadas (`CornerRadius` solo arriba), color primario.
  **Valor % encima de cada barra** (texto pequeño, `10.sp`).
- **Período actual en curso**: barra con `alpha = 0.45` + etiqueta "en curso"
  (texto pequeño bajo la fecha o sobre la barra). No confundir con cerrado.
- **Muchos períodos** (barras angostas): si `n > 8`, se omiten los labels de
  fecha intermedios (solo primero y último) para evitar solapamiento; el % se
  mantiene encima de cada barra. Si `n > 14`, se omiten también los % (solo
  tooltip-like: se mantienen los labels de fecha primero/último). El ancho
  mínimo de barra es 4.dp; si el slot es menor, las barras se centran en su
  slot sin solaparse.
- **Un solo período** (solo el actual): se dibuja una barra centrada.
- Estado vacío: si no hay snapshots, la tab ya muestra `EmptyStats` (sin
  cambios).
- Material 3 estricto (regla del repo): colores/tipografía desde
  `MaterialTheme`, sin colores hardcodeados.

## Commands

```
Test:  ./gradlew testDebugUnitTest
Lint:  ./gradlew lintDebug
Build: ./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/
  UsageHistory.kt          → + PeriodBar + periodBars() (función pura)
  ui/StatsTab.kt          → + card "Consumo por período" con bar chart Canvas
app/src/test/java/com/jpyunism/ollamacloudusage/
  UsageHistoryTest.kt     → + tests de periodBars (TDD)
app/src/main/res/values/strings.xml + values-en/strings.xml → strings nuevos
```

## Testing Strategy

- `UsageHistoryTest.kt`: tests de `periodBars` (función pura):
  - una barra por período con datos, en orden cronológico;
  - `peakPercent` correcto (pico del período, no el último snapshot);
  - `inProgress` correcto según `now` (período actual = true, cerrados = false);
  - períodos vacíos omitidos (mismo comportamiento que `periodsFor`);
  - sin snapshots → lista vacía;
  - alineación al `resetAnchor` (semana y sesión).
- UI: verificación manual (barras se dibujan, toggle cambia semana/sesión,
  barra "en curso" distinguible, labels no se solapan con muchos períodos).

## Boundaries

- Always: tests verdes + lint limpio antes de release; M3; strings es/en;
  release v0.19.0 al cerrar (regla AGENTS.md).
- Ask first: agregar dependencias de charts; reemplazar el gráfico de línea;
  cambiar la definición de "consumo antes del reset" (pico → último snapshot);
  cambiar el comportamiento de labels con muchos períodos.
- Never: colores hardcodeados; tocar widget/notificación (fuera de alcance);
  guardar datos nuevos fuera del dispositivo.

## Success Criteria

- [ ] Card "Consumo por período" con una barra por semana/sesión (toggle).
- [ ] Valor de cada barra = % consumido antes del reset (pico del período).
- [ ] Período actual distinguible ("en curso", alpha reducido).
- [ ] Tests de `periodBars` verdes + suite completa verde + lint limpio.
- [ ] Release v0.19.0 (versionCode 26) con APK por Telegram.

## Open Questions

Resueltas (aprobadas por el usuario):
1. **¿Incluir el período actual en curso como barra?** → **SÍ**, con distinción
   visual ("en curso" + alpha reducido).
2. **¿Valor de la barra = pico o último snapshot del período?** → **PICO**
   (máx %), consistente con el resumen existente.
