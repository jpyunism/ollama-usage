# Spec: Orden y agrupación de modelos en la vista de consumo

## Objective

En las cards de consumo (Sesión y Semana) de la pantalla principal:

1. **Ordenar los modelos por % de uso descendente** (mayor → menor), tanto en la
   barra de colores como en la lista de modelos debajo de la barra.
2. **Resolver el problema de legibilidad** de los modelos con poco %: hoy un
   modelo con 0.5% se dibuja como una línea de ~1px apenas visible (peso mínimo
   0.1). Solución aprobada: en la **barra** se agrupan los modelos bajo un umbral
   en un segmento **"Otros"** con color neutro (colorKey=null); en la **lista**
   se muestra el detalle de **cada modelo individual** (por poco que se haya
   usado), ordenado por % desc.
3. **Barra con uniones continuas**: solo los bordes exteriores de la barra son
   curvos (clip del contenedor); las uniones entre segmentos son rectas (sin
   shape por segmento).

## Cálculo (función pura, testeable sin Android)

```
groupModels(models, threshold = 3.0) → List<UsageSegment>
```

- `UsageSegment(label, percent, modelCount, colorKey)`:
  - `label` = nombre del modelo, o "Otros" para el grupo.
  - `percent` = % del modelo, o suma de % del grupo.
  - `modelCount` = 1 para modelos individuales; N para "Otros".
  - `colorKey` = nombre del modelo (para asignar color de la paleta); `null`
    para "Otros" (color neutro).

Reglas:

1. Ordenar entrada por `percent` desc (tiebreak: `requests` desc) — función
   `sortedByUsage()` reutilizada por la lista.
2. Modelos con `percent >= threshold` → segmento individual.
3. Modelos con `percent < threshold`:
   - Si hay **≥ 2** → un solo segmento "Otros" con la suma de sus % y
     `modelCount = N`.
   - Si hay **1 solo** → se mantiene individual (no agrupar un único modelo).
4. Si "Otros" quedaría como **único** segmento (todos < umbral) → no agrupar:
   mostrar todos individuales (la barra queda con todos los colores).
5. "Otros" siempre va al final (es el de menor % por construcción).
6. `groupModels(models, threshold = 3.0, othersLabel = "Otros")` — el label se
   pasa desde la UI (stringResource) para i18n; la función pura no depende de
   recursos Android.

## UI

### Barra de colores (`LinearUsageBar`)

- Segmentos en el orden de `groupModels` (mayor % → menor %).
- "Otros" se dibuja con color neutro: `MaterialTheme.colorScheme.outlineVariant`.
- **Uniones continuas**: el contenedor `Row` lleva `clip(RoundedCornerShape(6.dp))`
  y cada segmento es un `Box` con `background(color)` sin shape → solo los
  bordes exteriores quedan curvos.
- Se mantiene el peso mínimo `coerceAtLeast(0.1f)` para segmentos individuales
  (con la agrupación ya no hay líneas de 1px para modelos chicos).
- `contentDescription` accesible con los segmentos (label + %).

### Lista de modelos

- Filas en el orden de `sortedByUsage` (mayor % → menor %).
- **Detalle individual de TODOS los modelos** (incluidos los < umbral): nombre +
  requests + %, igual que hoy. No hay fila "Otros" en la lista.
- **Indicador de color**: cada fila muestra un círculo pequeño (10.dp) con el
  color de paleta del modelo (`modelColor`), el mismo que aparece en la barra —
  así se puede correlacionar visualmente la lista con los segmentos.

### Strings (es/en)

- "Otros" / "Others" (label del segmento agrupado en la barra + contentDescription).

## Commands

```
Test:  ./gradlew testDebugUnitTest
Lint:  ./gradlew lintDebug
Build: ./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/UsageData.kt    → UsageSegment + groupModels()
app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageTab.kt  → barra y lista usan groupModels()
app/src/main/res/values/strings.xml + values-en/strings.xml    → strings "Otros"
app/src/test/java/com/jpyunism/ollamacloudusage/GroupModelsTest.kt → tests de la función pura
```

## Testing Strategy

- `GroupModelsTest.kt` (TDD, tabla de casos):
  - `sortedByUsage`: orden desc por % (y tiebreak por requests).
  - Modelos ≥ umbral individuales; < umbral agrupados (≥2) o individuales (1).
  - Todos < umbral → sin agrupar.
  - Suma de % de "Otros" correcta y `modelCount` correcto.
  - "Otros" siempre al final.
  - Umbral default 3.0 y umbral custom; label custom.

## Boundaries

- Always: tests verdes + lint limpio antes de release; M3; strings es/en.
- Ask first: cambiar el umbral de agrupación (default propuesto 3%), tocar el
  widget o la notificación persistente.
- Never: colores hardcodeados (regla M3); cambiar la duración de sesión/semana.

## Success Criteria

- [ ] `groupModels` y `sortedByUsage` puras con tests (casos arriba).
- [ ] Barra y lista ordenadas de mayor % a menor %.
- [ ] Barra: modelos < 3% agrupados en "Otros" (color neutro); uniones continuas
      (solo bordes exteriores curvos).
- [ ] Lista: detalle individual de todos los modelos.
- [ ] Sin líneas de 1px: todo segmento visible en la barra.
- [ ] Suite completa verde + lint limpio.

## Open Questions

Resueltas: umbral 3%; lista con detalle individual (sin agrupar); barra con
uniones continuas (clip en el contenedor).
