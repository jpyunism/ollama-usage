# Spec: Navegación por swipe horizontal entre secciones

## Objective

Permitir pasar de una sección a otra de la app deslizando horizontalmente
(swipe), sincronizado con la bottom bar (NavigationBar). La app tiene 3 tabs
inferiores — **Uso** (Usage), **Estadísticas** (Stats) y **Configuración**
(Settings) — definidas en `UsageScreen.kt`, que hoy cambia de sección solo
tocando la tab (un `when(tab)` que descarta la sección anterior).

**Qué se construye:** un `HorizontalPager` que contiene las 3 secciones como
páginas, con sincronización bidireccional:

- **Deslizar** (swipe horizontal) cambia la página y actualiza la tab
  seleccionada en la `NavigationBar`.
- **Tocar una tab** anima el pager hasta esa página.

**Quién es el usuario:** el usuario final de la app Android (consumo de Ollama
Cloud). **Por qué:** navegación más natural y rápida entre secciones, estándar
en apps Material 3.

**Qué es "éxito":** deslizar entre las 3 secciones funciona en ambos sentidos,
la bottom bar refleja siempre la sección visible, tocar una tab lleva a su
página, y no se regresa el comportamiento actual (la tab activa sobrevive a la
recreación por cambio de idioma).

### Acceptance criteria (testeables)

- [ ] Deslizar de Uso → Estadísticas → Configuración y en sentido inverso
      cambia la página y la tab resaltada en la bottom bar.
- [ ] Tocar cualquier tab de la bottom bar anima el pager a esa página y la
      resalta.
- [ ] Tras cambiar de idioma (que recrea la activity), la app vuelve a la
      **misma sección** en la que estaba (no regresión de v0.22.1).
- [ ] El banner de actualización (`UpdateBanner`) sigue visible por encima del
      pager en las 3 secciones (comportamiento actual).
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes.
- [ ] Validación manual en emulador (AVD `test64`) sin crashes/ANRs.

## Requisitos (IDs)

- **REQ-001 — Swipe horizontal entre secciones.** Las 3 secciones se renderizan
  como páginas de un `HorizontalPager` (`androidx.compose.foundation.pager`),
  en el orden del enum `Tab` (Usage=0, Stats=1, Settings=2). El swipe en ambos
  sentidos navega entre páginas.
- **REQ-002 — Sincronización bidireccional pager ↔ NavigationBar.** El estado
  del pager es la única fuente de verdad de la sección activa. Deslizar
  actualiza la tab seleccionada; tocar una tab ejecuta
  `pagerState.animateScrollToPage(index)`.
- **REQ-003 — Persistencia de la tab activa tras recreación.** La sección
  activa sobrevive a `recreate()` (cambio de idioma). Se logra con
  `rememberPagerState` (que internamente usa `rememberSaveable`), reemplazando
  el `var tab by rememberSaveable { ... }` actual. **No regresión** del fix
  v0.22.1.
- **REQ-004 — Preservación del estado de cada sección.** Al navegar por swipe,
  el estado interno de cada sección (posición de scroll de Uso/Configuración,
  selección Semana/Sesión de Estadísticas) no se pierde. Se logra manteniendo
  las páginas compuestas con `beyondViewportPageCount = Tab.entries.size - 1`
  (ver Decisiones).
- **REQ-005 — Banner de actualización fijo.** `UpdateBanner` permanece fuera
  del pager (encima), visible en las 3 secciones, igual que hoy.
- **REQ-006 — Material 3 estricto y sin dependencias nuevas.** Solo
  `androidx.compose.material3.*` y `androidx.compose.foundation.pager` (ya
  incluido vía BOM). Sin colores/tipografías hardcodeadas, sin vistas clásicas.
- **REQ-007 — Accesibilidad.** Los `NavigationBarItem` conservan su
  `contentDescription` (ya presente). El swipe del pager es el gesto estándar
  de TalkBack; no se añaden barreras semánticas.
- **REQ-008 — TDD RED→GREEN + release.** Test unitario para la lógica
  extraíble (mapeo tab↔página) antes de implementar; validación en emulador y
  release obligatorio al cerrar (regla AGENTS.md).

## Tech Stack

- Kotlin + Jetpack Compose (Material 3), `compileSdk 36`, `minSdk 26`.
- `androidx.compose:compose-bom:2024.09.03` → `foundation 1.7.x`, donde
  `HorizontalPager` y `rememberPagerState` son **estables** (sin
  `@OptIn(ExperimentalFoundationApi::class)`).
- `androidx.compose.material3:material3` (NavigationBar, Scaffold, etc.).
- **Sin dependencias nuevas** (el pager ya está en `foundation`, transitiva del
  BOM). No se usa Navigation Compose ni ViewPager2.

## Commands

```bash
# Entorno (server)
export JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8
export ANDROID_HOME=/home/jyunis/android-sdk

# Test unitario (TDD RED→GREEN)
./gradlew testDebugUnitTest

# Lint
./gradlew lintDebug

# Build release firmado
./gradlew assembleRelease

# Verificación completa (regla AGENTS.md)
./gradlew testDebugUnitTest lintDebug assembleRelease
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/ui/
  UsageScreen.kt   → reemplaza el when(tab) por HorizontalPager + rememberPagerState;
                     sincroniza pager ↔ NavigationBar; mantiene UpdateBanner fijo
  UsageTab.kt      → sin cambios (página 0)
  StatsTab.kt      → sin cambios (página 1)
  SettingsTab.kt   → sin cambios (página 2)
app/src/test/java/com/jpyunism/ollamacloudusage/
  TabPagerMappingTest.kt → test unitario del mapeo tab↔página (REQ-008)
docs/
  spec-swipe-entre-secciones.md → esta spec
```

Nota: `MainActivity.kt` no cambia (sigue montando `UsageScreen(vm)`).

## Code Style

- Material 3 estricto (regla del repo): colores/tipografía desde
  `MaterialTheme`, sin valores hardcodeados.
- El pager es la única fuente de verdad; **no** se mantiene un `var tab`
  duplicado. La tab activa se deriva de `pagerState.currentPage`.

Esqueleto de referencia (no es la implementación final, solo el patrón):

```kotlin
val pagerState = rememberPagerState(pageCount = { Tab.entries.size })

Scaffold(
    bottomBar = {
        NavigationBar {
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
        }
    },
) { innerPadding ->
    Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding).imePadding()) {
        if (update != null && download !is DownloadState.Downloading) {
            UpdateBanner(version = update!!.versionName, onClick = { vm.startUpdateDownload(update!!) })
        }
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = Tab.entries.size - 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (Tab.entries[page]) {
                Tab.Usage -> UsageTab(vm, state)
                Tab.Stats -> StatsTab(vm.history.collectAsStateWithLifecycle().value)
                Tab.Settings -> SettingsTab(vm, settings, onSettingsChanged)
            }
        }
    }
}
```

## Testing Strategy

- **Framework:** JUnit4 + Mockk + kotlinx-coroutines-test (ya en el repo). Los
  tests viven en `app/src/test/` (unitarios, sin Android).
- **Qué se testea (REQ-008):** la lógica pura extraíble del mapeo
  tab↔página. Se extrae un helper mínimo (p.ej. `Tab.entries` como orden
  canónico y una función `tabForPage(page: Int)` / `pageForTab(tab: Tab)`) y se
  testea:
  - `Tab.entries.size == 3` y el orden Usage → Stats → Settings.
  - Round-trip: `tabForPage(pageForTab(t)) == t` para las 3 tabs.
  - `tabForPage` fuera de rango devuelve un valor seguro (o lanza) — según se
    decida en implementación.
- **Qué NO se testea en unit:** el gesto de swipe y la animación (requieren
  Compose UI test / emulador). El repo no tiene infra de Compose UI tests en
  CI; la verificación del gesto es **manual en emulador** (obligatoria por
  AGENTS.md).
- **TDD:** escribir el test primero (RED), verlo fallar, implementar (GREEN).
- **Verify:** `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes +
  navegación manual en emulador.

## Boundaries

- **Always:**
  - Material 3 exclusivo (regla AGENTS.md); sin `androidx.compose.material.*`
    (M2) ni vistas clásicas.
  - Strings es/en en `values/strings.xml` y `values-en/strings.xml` (si se
    añade alguno).
  - Tests verdes + lint limpio antes de release.
  - Validar en emulador (AVD `test64`) antes de publicar, incluyendo la
    regresión de idioma (cambiar idioma no resetea la tab).
  - Release obligatorio al cerrar: bump `versionCode` (+1) y `versionName`
    (semver) en `app/build.gradle.kts`, commit + push + tag `v<versionName>`,
    release en GitHub con APK firmado y envío por Telegram (chat 15710279).
- **Ask first:**
  - Agregar cualquier dependencia nueva (no debería hacer falta).
  - Cambiar el orden o el número de tabs.
  - Cambiar `beyondViewportPageCount` a un valor distinto de
    `Tab.entries.size - 1` (afecta preservación de estado vs. costo de
    composición).
  - Introducir Navigation Compose o ViewPager2.
- **Never:**
  - Tocar el widget de home screen (`UsageWidgetProvider` +
    `res/layout/widget_usage.xml`) ni las notificaciones (fuera de alcance).
  - Hardcodear colores/tipografías.
  - Cambiar el contrato de `AlertSettings`, las claves de prefs ni el
    `UsageViewModel`.
  - Romper la persistencia de la tab activa tras recreación (regresión v0.22.1).

## Success Criteria

- [ ] Swipe horizontal navega entre las 3 secciones en ambos sentidos
      (REQ-001).
- [ ] Bottom bar y pager siempre sincronizados: deslizar actualiza la tab,
      tocar la tab anima la página (REQ-002).
- [ ] Cambiar idioma (recreación) conserva la sección activa (REQ-003).
- [ ] El estado de cada sección (scroll, período Semana/Sesión) se conserva al
      navegar por swipe (REQ-004).
- [ ] `UpdateBanner` visible en las 3 secciones, fijo sobre el pager (REQ-005).
- [ ] Sin dependencias nuevas; Material 3 estricto (REQ-006).
- [ ] `contentDescription` intacto en las tabs (REQ-007).
- [ ] Test unitario del mapeo tab↔página en verde (REQ-008).
- [ ] `testDebugUnitTest`, `lintDebug` y `assembleRelease` verdes.
- [ ] Validación en emulador sin crashes/ANRs + release publicado (v0.23.0,
      versionCode 35).

## Decisiones (técnicas)

- **Enfoque elegido: `HorizontalPager` + `rememberPagerState`**
  (`androidx.compose.foundation.pager`). Es el patrón canónico de Material 3
  para tabs con swipe, ya está en `foundation` (sin dependencia nueva) y es
  estable en la versión del BOM.

- **Alternativas descartadas:**
  1. **`swipeable`/`draggable` + `AnimatedContent` manual** — más código,
     propenso a errores de gesto/umbral, no da el feel nativo del pager.
     Descartado.
  2. **`TabRow` (M3) + pager** — `TabRow` es para tabs superiores, no para
     bottom navigation; aquí se mantiene `NavigationBar`. No aplica.
  3. **`ViewPager2` vía `AndroidView`** — viola la regla "sin vistas clásicas"
     y añade interop innecesaria. Descartado.
  4. **Navigation Compose** — añade una dependencia y un modelo de rutas que la
     app no usa (YAGNI). Descartado.

- **Fuente de verdad única:** se elimina `var tab by rememberSaveable` y se
  deriva la tab de `pagerState.currentPage`. `rememberPagerState` ya es
  saveable, por lo que la persistencia tras recreación se mantiene sin código
  extra (REQ-003). `currentPage` (no `settledPage`) da feedback inmediato de la
  tab durante el arrastre.

- **Preservación de estado por sección (REQ-004):** `beyondViewportPageCount =
  Tab.entries.size - 1` mantiene las 3 páginas compuestas, conservando scroll y
  estado `remember` (p.ej. el período de Stats) al navegar. Con 3 páginas
  ligeras el costo de composición es despreciable. Alternativa (si se prefiere
  lazy): convertir el estado efímero de cada tab a `rememberSaveable` — más
  invasivo, se descarta como opción por defecto.

- **Gestos/overscroll:** el pager es horizontal y las páginas usan
  `verticalScroll`; ejes ortogonales no entran en conflicto (nested scroll de
  Compose los resuelve). No se requiere manejo especial de overscroll ni
  `userScrollEnabled=false`.

- **Animación:** swipe usa el fling por defecto del pager; tocar tab usa
  `animateScrollToPage` (transición suave).

## Open Questions

1. **Preservación de estado (REQ-004):** ¿se acepta `beyondViewportPageCount =
   2` (mantener las 3 páginas compuestas) como enfoque por defecto, o se
   prefiere lazy + `rememberSaveable` por tab? *(Recomendado: la primera.)*
2. **`tabForPage` fuera de rango:** ¿devolver `Tab.Usage` como fallback o
   lanzar `IndexOutOfBoundsException`? *(Recomendado: fallback a `Tab.Usage`.)*
3. **Versión del release:** se asume `0.23.0` / `versionCode 35` (feature
   menor). Confirmar si se prefiere otro número.
