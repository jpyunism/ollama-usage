# Spec: Migrar estados UI a rememberSaveable

Issue: #64
Estado: draft
Autor: Hermes (subagente spec, 2026-09-10)

## Contexto y objetivo

Varios composables usan `remember { mutableStateOf(...) }` en vez de
`rememberSaveable { mutableStateOf(...) }` para estados que el usuario
espera mantener tras una rotacion o muerte de proceso.

Detectado en:
- `UsageTab.kt` L125: `modelSheet` (sheet de evolucion de modelo)
- `StatsTab.kt` L94: `period` (filtro WEEK/DAY/MONTH)
- `StatsTab.kt` L110: `showComparison` (toggle de comparativa)
- `StatsTab.kt` L319: `tooltip` (tooltip del chart)
- `SettingsTab.kt` multiples: `statusRes`, `showAdd`, `renameTarget`,
  `label`, `apiKey`, `showTimePicker`, `picked`

**Objetivo:** auditar y migrar TODOS los estados relevantes a
`rememberSaveable`, manteniendo la app funcional pero garantizando que
el estado UI sobrevive rotacion y muerte de proceso.

**No es objetivo:** persistir estados entre sesiones (DataStore/SecurePrefs)
— solo dentro de la misma sesion de la activity.

## Assumptions

1. Los tipos a persistir son todos Bundle-serializables: `String?`,
   `Int?`, `Boolean`, enums (con `Saver` simple). Nada de tipos no
   triviales.
2. El bug conocido: "cambiar idioma resetea la pestana activa" ya
   esta resuelto (regresion v0.22.1); no tocamos eso.
3. Los estados puramente de animacion (ej. `animateFloatAsState` target)
   NO migran — son transitorios por diseno.
4. El test de "Don't keep activities" + `am kill` es el criterio de
   aceptacion manual.

## Tech stack

- Kotlin 1.9.x
- Compose (sin nuevas deps; `rememberSaveable` ya esta en uso en
  otros lados del proyecto)
- Sin almacenamiento persistente

## Comandos

```bash
# Audit script: listar todos los `remember { mutableStateOf(`
./gradlew :app:lintDebug 2>&1 | grep -i "rememberSaveable"
# Busqueda manual
rg "remember \{ mutableStateOf" app/src/main/java
# Test
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest --tests "*SurviveRotationTest"
```

## Code style

```kotlin
// ANTES
var modelSheet by remember { mutableStateOf<String?>(null) }
var showComparison by remember { mutableStateOf(true) }

// DESPUES
var modelSheet by rememberSaveable { mutableStateOf<String?>(null) }
var showComparison by rememberSaveable { mutableStateOf(true) }

// Para enums
var period by rememberSaveable { mutableStateOf(HistoryPeriod.WEEK) }
// (HistoryPeriod es enum; ya es Bundle-serializable por default)

// Si el tipo NO es Bundle-serializable, usar Saver custom:
// var complexState by rememberSaveable(stateSaver = MySaver) { mutableStateOf(...) }
```

## Estructura propuesta

No se agregan archivos nuevos. Cambios minimos en archivos existentes:

```
app/src/main/java/com/jpyunism/ollamacloudusage/ui/
  UsageTab.kt        # 1 cambio (modelSheet)
  StatsTab.kt        # 3 cambios (period, showComparison, tooltip)
  SettingsTab.kt     # 7+ cambios (estado de dialogs y sheets)
```

## Testing strategy

- **Audit script (manual):** `rg "remember \{ mutableStateOf" app/src/main`
  debe devolver 0 resultados al terminar.
- **Test instrumentado nuevo:** `UiStateSurviveRotationTest` que:
  1. Abre cada pantalla.
  2. Cambia estados (abre un sheet, selecciona un filtro, etc.).
  3. Simula rotacion via `composeRule.activity.requestedOrientation`.
  4. Verifica que el estado se mantiene.
- **Test de muerte de proceso (manual):** con "Don't keep activities"
  en developer options + `adb shell am kill com.jpyunism.ollamacloudusage`,
  verificar que los estados se restauran al volver a la app.

## Boundaries

- **Always do:**
  - Migrar todos los `remember { mutableStateOf(...) }` que el usuario
    espera mantener tras rotacion.
  - Verificar con `Don't keep activities` en cada pantalla.
  - Localizar el comentario (KDoc) si el estado tiene comportamiento
    especial.
- **Ask first:**
  - Usar un `Saver` custom (si el tipo no es Bundle-serializable).
  - Cambiar el tipo del estado (ej. de `String?` a sealed class).
- **Never do:**
  - Migrar estados que son intencionalmente transitorios (animaciones,
    snackbars visibles, etc.).
  - Migrar estados que ya estan en el ViewModel (esos ya sobreviven
    via SavedStateHandle).

## Success criteria

1. `rg "remember \{ mutableStateOf" app/src/main` devuelve 0.
2. Cada estado listado en la seccion "Contexto" sobrevive:
   - Rotacion del dispositivo.
   - "Don't keep activities" + reabrir la app.
3. Tests pasan, lint limpio, validado en emulador API 35.
4. Sin regresiones de UI (screenshot regression o walkthrough manual).

## Estimacion

2h de cambios mecanicos + 30min de tests. Total: 2.5h.
