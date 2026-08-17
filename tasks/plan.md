# Plan: Navegación por swipe horizontal entre secciones

## Overview

Reemplazar el `when(tab)` de `UsageScreen` por un `HorizontalPager` con
`rememberPagerState`, de modo que las 3 secciones (Usage / Stats / Settings)
se naveguen deslizando horizontalmente y la `NavigationBar` quede
sincronizada bidireccionalmente con el pager. El pager pasa a ser la **única
fuente de verdad** de la sección activa: se elimina `var tab by
rememberSaveable` y la tab se deriva de `pagerState.currentPage`.
`rememberPagerState` ya es saveable, por lo que la tab sigue sobreviviendo a
`recreate()` (cambio de idioma) — **sin regresión del fix v0.22.1**.

Spec de referencia: `docs/spec-swipe-entre-secciones.md` (REQ-001 a REQ-008).

## Architecture Decisions

- **`HorizontalPager` + `rememberPagerState`** (`androidx.compose.foundation.pager`).
  Estable en el BOM `2024.09.03` (foundation 1.7.x): **sin** `@OptIn` ni
  dependencias nuevas. El pager ya viene transitivamente vía
  `androidx.compose.material3:material3` → `foundation`.
- **Fuente de verdad única:** se borra `var tab by rememberSaveable { ... }`.
  La tab activa se lee de `pagerState.currentPage`; el `NavigationBarItem.selected`
  se calcula como `pagerState.currentPage == index`. Tocar una tab lanza
  `scope.launch { pagerState.animateScrollToPage(index) }`.
- **Persistencia tras recreación (REQ-003):** `rememberPagerState` usa
  `rememberSaveable` internamente, por lo que al `recreate()` (cambio de idioma)
  el `currentPage` se restaura solo. **No** se añade lógica extra de
  `rememberSaveable` — elimina el riesgo de doble fuente de verdad. Es el
  reemplazo directo del `var tab` saveable actual: misma garantía, menos estado.
- **Preservación del estado por sección (REQ-004):**
  `beyondViewportPageCount = Tab.entries.size - 1` (= 2) mantiene las 3 páginas
  compuestas, conservando scroll vertical de Uso/Configuración y la selección
  Semana/Sesión de Estadísticas al navegar por swipe. Con 3 páginas ligeras el
  costo de composición es despreciable. (Decisión confirmada en spec, Open
  Question 1 — enfoque recomendado.)
- **`UpdateBanner` fijo (REQ-005):** sigue fuera del pager, dentro del `Column`
  superior del `Scaffold`, encima del `HorizontalPager`. Comportamiento
  idéntico al actual.
- **Mapeo tab↔página extraíble (REQ-008):** se añaden funciones puras
  `tabForPage(page: Int): Tab` y `pageForTab(tab: Tab): Int` (top-level en
  `UsageScreen.kt`, junto al enum `Tab`, para mantener todo el código de tabs
  en un mismo archivo). `tabForPage` fuera de rango devuelve `Tab.Usage` como
  fallback seguro (Open Question 2 — recomendado). Estas funciones son la
  única lógica testeable en unit; el gesto/animación se valida en emulador.
- **Material 3 estricto (REQ-006):** no se añade ninguna dependencia. Sin
  `@OptIn` (el pager es estable). Sin colores/tipografías hardcodeadas.
- **Accesibilidad (REQ-007):** los `NavigationBarItem` conservan su
  `contentDescription` (ya presente). El swipe es el gesto estándar de TalkBack;
  no se añaden barreras semánticas.
- **`MainActivity.kt` no cambia** (sigue montando `UsageScreen(vm)`).

## TDD (RED → GREEN)

El repo usa **JUnit4 puro** (`org.junit.Assert.*`), tests en
`app/src/test/` (sin Android). Estilo: nombres con backticks en español,
`assertEquals`/`assertNull` directos, sin frameworks de UI en unit. No hay
infra de Compose UI test en unit (solo `ui-test-junit4` en `androidTest`), así
que el gesto/animación se verifica manualmente en emulador.

1. **RED** — Crear `app/src/test/.../TabPagerMappingTest.kt` que referencia
   `tabForPage` / `pageForTab` (aún no existen) → **no compila = RED**. El
   test cubre:
   - `Tab.entries.size == 3` y orden Usage(0) → Stats(1) → Settings(2).
   - Round-trip: `tabForPage(pageForTab(t)) == t` para las 3 tabs.
   - `pageForTab(Tab.Usage)==0`, `Stats==1`, `Settings==2`.
   - `tabForPage(-1) == Tab.Usage` y `tabForPage(99) == Tab.Usage` (fallback).
2. **GREEN** — Añadir `tabForPage`/`pageForTab` en `UsageScreen.kt` + refactor
   del `when(tab)` a `HorizontalPager`. `./gradlew testDebugUnitTest` verde.

## Cambios exactos por archivo

### 1. `app/src/test/java/com/jpyunism/ollamacloudusage/TabPagerMappingTest.kt` (NUEVO — RED)

Test JUnit4, paquete `com.jpyunism.ollamacloudusage` (mismo paquete que el enum
`Tab` está en `...ui`... — ver nota). El enum `Tab` vive en
`com.jpyunism.ollamacloudusage.ui`, por lo que el test importa ese paquete o se
ubica en `...ui`. **Decisión:** ubicar el test en
`app/src/test/java/com/jpyunism/ollamacloudusage/ui/TabPagerMappingTest.kt`
(mismo paquete que `Tab`, acceso directo a `Tab.entries` y a las funciones
top-level). Casos listados arriba (sección TDD).

### 2. `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt` (MODIFICAR — GREEN)

- **Imports nuevos** (sin `@OptIn`):
  - `androidx.compose.foundation.layout.Arrangement` (si hace falta)
  - `androidx.compose.foundation.pager.HorizontalPager`
  - `androidx.compose.foundation.pager.rememberPagerState`
- **Imports a eliminar:** `rememberSaveable`, `mutableStateOf`,
  `setValue` (si quedan sin uso tras borrar `var tab`). Revisar tras el
  refactor — `remember`/`mutableStateOf` siguen usándose para
  `settingsSaveJob`.
- **Funciones puras nuevas** (top-level, debajo del `enum Tab`):
  ```kotlin
  /** Índice de página dentro del pager para la tab dada. */
  fun pageForTab(tab: Tab): Int = Tab.entries.indexOf(tab).let {
      if (it < 0) 0 else it
  }

  /** Tab correspondiente a una página del pager; fuera de rango → Usage. */
  fun tabForPage(page: Int): Tab = Tab.entries.getOrElse(page) { Tab.Usage }
  ```
- **`UsageScreen` body** (cambios dentro del composable):
  - **Borrar:** `var tab by rememberSaveable { mutableStateOf(Tab.Usage) }`.
  - **Añadir** (en su lugar):
    ```kotlin
    val pagerState = rememberPagerState(pageCount = { Tab.entries.size })
    ```
    `scope` ya existe (`rememberCoroutineScope()`).
  - **`bottomBar` / `NavigationBar`:**
    - Iterar con `forEachIndexed`:
      ```kotlin
      Tab.entries.forEachIndexed { index, t ->
          NavigationBarItem(
              selected = pagerState.currentPage == index,
              onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
              icon = {
                  Icon(
                      if (pagerState.currentPage == index) t.selectedIcon else t.icon,
                      contentDescription = stringResource(t.labelRes),
                  )
              },
              label = { Text(stringResource(t.labelRes)) },
          )
      }
      ```
  - **Contenido (innerPadding):** el `Box` anidado que contenía el
    `when(tab)` se reemplaza por:
    ```kotlin
    HorizontalPager(
        state = pagerState,
        beyondViewportPageCount = Tab.entries.size - 1,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (tabForPage(page)) {
            Tab.Usage -> UsageTab(vm, state)
            Tab.Stats -> StatsTab(vm.history.collectAsStateWithLifecycle().value)
            Tab.Settings -> SettingsTab(vm, settings, onSettingsChanged)
        }
    }
    ```
    `UpdateBanner` **permanece donde está** (en el `Column` superior, antes del
    pager) — no se mueve dentro del pager. El `Column(Modifier.fillMaxSize())`
    que envuelve banner + contenido se conserva; solo cambia el `Box` interno
    por el `HorizontalPager`.
- **`MainActivity.kt`:** sin cambios.
- **`UsageTab.kt` / `StatsTab.kt` / `SettingsTab.kt`:** sin cambios (ya son
  composables de página; el pager los envuelve sin tocar su API).

### 3. `app/build.gradle.kts` (MODIFICAR — solo release)

- `versionCode = 34` → `35`
- `versionName = "0.22.1"` → `"0.23.0"`
- Sin cambios de dependencias.

### 4. Sin cambios en strings / widget / ViewModel

- No se añaden strings (no hay etiquetas nuevas).
- `UsageWidgetProvider` y `res/layout/widget_usage.xml` intocables (boundary).
- `UsageViewModel` intocable (boundary).

## Orden de implementación

1. **Test RED** (`TabPagerMappingTest.kt`) → `./gradlew testDebugUnitTest`
   falla (no compila: símbolos inexistentes).
2. **Funciones puras** `tabForPage`/`pageForTab` en `UsageScreen.kt` →
   `./gradlew testDebugUnitTest` verde (GREEN parcial: lógica mapeo).
3. **Refactor `UsageScreen`** a `HorizontalPager` + sync bidireccional con
   `NavigationBar` (borrar `var tab`, añadir `rememberPagerState`,
   `beyondViewportPageCount = 2`, `animateScrollToPage` en `onClick`).
4. **Lint + release build:**
   `./gradlew lintDebug assembleRelease`.
5. **Bump versión** en `app/build.gradle.kts` (0.23.0 / code 35).
6. **Validación en emulador** (AVD `test64`) — ver sección siguiente.
7. **Publicación:** commit + push + tag `v0.23.0` + release GitHub + APK por
   Telegram (regla AGENTS.md).

## Estrategia de verificación

### Build / test / lint (server)

```bash
export JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8
export ANDROID_HOME=/home/jyunis/android-sdk

./gradlew testDebugUnitTest        # RED primero, luego GREEN
./gradlew lintDebug                # sin warnings nuevos / sin strings hardcoded
./gradlew assembleRelease          # APK firmado (CN=JuanPa)
# Combinado (regla AGENTS.md):
./gradlew testDebugUnitTest lintDebug assembleRelease
```

### Validación en emulador (AVD `test64`, API 35) — obligatoria

```bash
export ANDROID_HOME=/home/jyunis/android-sdk
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
sg kvm -c "setsid nohup emulator -avd test64 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -no-snapshot > /tmp/emulator.log 2>&1 < /dev/null &"
adb wait-for-device
# hasta sys.boot_completed=1
adb shell getprop sys.boot_completed

adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.jpyunism.ollamacloudusage/.MainActivity
```

Casos manuales (usar `adb shell input tap` / `input swipe` / `uiautomator
dump` + `screencap`):

1. **Swipe Uso → Stats → Settings** (`adb shell input swipe` izquierda) y
   sentido inverso: la tab resaltada de la bottom bar cambia con cada página
   (REQ-001, REQ-002). Screenshot en cada sección.
2. **Tocar cada tab** de la bottom bar (`input tap` sobre el ítem): el pager
   anima hasta esa página y la tab se resalta (REQ-002).
3. **No regresión idioma (REQ-003):** con la app en la tab Stats, ir a
   Configuración → cambiar idioma (es↔en); la activity se recrea y la app
   vuelve a la **misma sección** (Stats). Verificar con `screencap`.
4. **Persistencia tras force-stop (REQ-003/004):** `adb shell am force-stop
   com.jpyunism.ollamacloudusage` + relanzar; la tab y el estado de cada
   sección (scroll de Uso/Configuración, período Semana/Sesión de Stats) se
   conservan.
5. **Banner (REQ-005):** si hay actualización disponible, el `UpdateBanner`
   se ve sobre el pager en las 3 secciones (simular/verificar con el flujo de
   update si aplica; como mínimo, confirmar que el layout no lo oculta).
6. **Crash/ANR:** `adb logcat -d -b crash | grep -E "FATAL EXCEPTION|ANR in
   com.jpyunism.ollamacloudusage"` → vacío.
7. **Capturas:** revisar con
   `skills/vision-delegation/scripts/describe_image.py` para confirmar
   visualmente el estado de cada sección.

```bash
adb emu kill
```

Solo tras esto se publica el release.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Regresión v0.22.1: al recrear por idioma se pierde la tab. | `rememberPagerState` es saveable (mismo mecanismo que el `var tab` anterior). Se elimina el estado duplicado en vez de añadir otro. Validación manual #3. |
| Conflicto de gestos: swipe horizontal del pager vs. scroll vertical de las páginas. | Ejes ortogonales; el nested scroll de Compose los resuelve (spec, Decisiones). Sin `userScrollEnabled=false`. Validar en emulador. |
| Estado de Stats (Semana/Sesión) se pierde al swipar. | `beyondViewportPageCount = 2` mantiene las 3 páginas compuestas (REQ-004). Validación manual #4. |
| `tabForPage` fuera de rango lanza excepción inesperada. | Fallback a `Tab.Usage` (`getOrElse`); testeado en unit (caso -1 y 99). |
| Borrar imports deja imports sin usar → lint warnings. | Revisar imports de `UsageScreen.kt` tras el refactor; eliminar `rememberSaveable`/`mutableStateOf`/`setValue` solo si quedan sin uso. `./gradlew lintDebug` debe quedar limpio. |
| `animateScrollToPage` desde `onClick` necesita `CoroutineScope`. | `scope` ya existe (`rememberCoroutineScope()`); no hace falta crear otro. |
| Over-scroll del pager en los extremos. | Comportamiento por defecto del pager (efeito rebote en API ≥ 31, nada en < 31). Aceptable; no se configura nada extra. |

## Estimación de duración por paso

| Paso | Duración estimada |
|---|---|
| 1. Test RED (`TabPagerMappingTest.kt`) | 10 min |
| 2. Funciones `tabForPage`/`pageForTab` (GREEN lógica) | 5 min |
| 3. Refactor `UsageScreen` a `HorizontalPager` + sync | 25 min |
| 4. `lintDebug` + `assembleRelease` (verde, ajuste de imports) | 10 min |
| 5. Bump versión 0.23.0 / code 35 | 2 min |
| 6. Validación en emulador (6 casos + logcat + capturas) | 30 min |
| 7. Publicación (commit + push + tag + GitHub release + Telegram) | 10 min |
| **Total estimado** | **~1 h 30 min** |