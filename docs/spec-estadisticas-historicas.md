# Spec: Estadísticas de uso histórico (gráfico de consumo)

## Objective

Agregar una sección **Estadísticas** con un gráfico de consumo porcentual a lo
largo del tiempo, con toggle **Semana / Sesión**. El gráfico muestra la
evolución del % de cuota consumida dentro de cada período y deja ver cuánto
porcentaje de la semana (o sesión) se logró consumir antes de cada reset.

## Fuente de datos (decisión clave)

La API de Ollama Cloud (`/api/usage`) **solo entrega el estado actual**
(session/weekly usage % + resets). No existe endpoint histórico. Por lo tanto:

- La app **acumula snapshots localmente** en cada refresh exitoso (manual,
  worker periódico, arranque): `(timestamp, sessionPercent, weeklyPercent)`.
- El histórico se construye con el tiempo de uso de la app. El gráfico muestra
  lo que se haya registrado (si recién se instala, la sección muestra un estado
  vacío con hint de que se irá llenando).
- **Storage**: JSON en `SharedPreferences` (SecurePrefs, mismo mecanismo actual).
  Sin Room ni dependencias nuevas (YAGNI).
- **Dedupe**: no guardar un snapshot si el % es idéntico al anterior y pasaron
  < 15 min (evita ruido con refrescos frecuentes).
- **Límite**: retener máx. 600 snapshots por período (semana y sesión) — con
  refresco cada 60 min ≈ 25 días de historia continua; suficiente para ver
  varios ciclos de reset semanal. FIFO (descartar los más viejos).

## Gráfico

- **Compose Canvas** (sin librerías de charts — YAGNI, stdlib primero).
- Eje Y: % de consumo (0–100, con labels 0 / 50 / 100).
- Eje X: tiempo (últimos snapshots; labels de fecha en los extremos).
- Serie: línea + área suave (gradiente del color primario).
- **Líneas verticales punteadas en cada reset** (semana: cada domingo 21:00 CLT;
  sesión: cada 24 h desde el primer snapshot) — el "diente de sierra" muestra
  cuánto se consumió antes de cada reset.
- **Resumen del último período completo**: "Semana del 3 ago: 62% consumido
  antes del reset" (pico del último período cerrado). Si el período actual aún
  no cierra, se muestra el pico del período anterior cerrado.
- **Resumen del período actual en curso** (decidido): "Vas 45% en la semana
  actual" (consumo actual vs pico del período en curso).
- Toggle **Semana / Sesión** (SegmentedButton M3 o FilterChips) que cambia la
  serie y los marcadores de reset.
- **Tooltip al tocar** (decidido): al tocar el gráfico se muestra el % exacto
  del punto más cercano (fecha + %). Implementación: detectar tap en el Canvas,
  buscar el snapshot más cercano en X y dibujar un marcador + burbuja con
  "12 ago, 14:30 · 62%".

## UI

- **Nueva tab "Estadísticas"** en la NavigationBar (Usage / Estadísticas /
  Settings), icono `Icons.Outlined.BarChart` / `Icons.Filled.BarChart`
  (material-icons-extended ya está en deps).
- Contenido: card con el toggle Semana/Sesión, el gráfico (Canvas) y el resumen
  del último período.
- Estado vacío (sin snapshots aún): texto explicativo + hint de que se llena
  con el uso.
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
  UsageHistory.kt          → modelo + agregación pura (períodos, resets, picos)
  UsageHistoryStore.kt     → persistencia JSON en SecurePrefs (dedupe + límite FIFO)
  ui/StatsTab.kt           → tab Estadísticas: toggle + gráfico Canvas + resumen
  ui/UsageScreen.kt        → nueva tab en NavigationBar
app/src/test/java/com/jpyunism/ollamacloudusage/
  UsageHistoryTest.kt     → tests de agregación (períodos, picos, resets)
app/src/main/res/values/strings.xml + values-en/strings.xml → strings nuevos
```

## Testing Strategy

- `UsageHistoryTest.kt` (función pura, sin Android): agrupar snapshots en
  períodos semanales/sesiones, calcular pico por período, detectar resets,
  dedupe y límite FIFO del store (con fake de prefs o lógica pura separada).
- UI: verificación manual (gráfico se dibuja, toggle cambia serie, estado vacío).

## Boundaries

- Always: tests verdes + lint limpio antes de release; M3; strings es/en;
  release v0.18.0 al cerrar (regla AGENTS.md).
- Ask first: agregar dependencias de charts; interacción táctil (tooltips);
  cambiar el límite de retención o la ventana de dedupe.
- Never: colores hardcodeados; tocar widget/notificación (fuera de alcance);
  guardar datos de uso fuera del dispositivo.

## Success Criteria

- [ ] Los snapshots se acumulan en cada refresh exitoso (dedupe + límite).
- [ ] Tab Estadísticas con gráfico Semana/Sesión (toggle) dibujado en Canvas.
- [ ] Marcadores de reset visibles; resumen del último período con % consumido
      antes del reset.
- [ ] Estado vacío claro cuando aún no hay datos.
- [ ] Suite completa verde + lint limpio + release v0.18.0.

## Open Questions

Resueltas (aprobadas por el usuario):
1. **Tooltip al tocar**: SÍ en v1 (marcador + burbuja con fecha y % del punto
   más cercano al tap).
2. **Ventana del gráfico**: todo lo acumulado (límite 600 snapshots FIFO).
3. **Resumen**: ambos — último período cerrado ("Semana del 3 ago: 62%") y
   período actual en curso ("Vas 45% en la semana actual").
