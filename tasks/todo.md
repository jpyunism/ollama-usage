# Tasks: Navegación por swipe horizontal entre secciones

## Task 1: Test RED — mapeo tab↔página (REQ-008)

- [x] Crear `app/src/test/java/com/jpyunism/ollamacloudusage/ui/TabPagerMappingTest.kt`
      (JUnit4, mismo paquete que el enum `Tab`)
- [ ] Casos:
  - `Tab.entries.size == 3` y orden Usage(0) → Stats(1) → Settings(2)
  - Round-trip: `tabForPage(pageForTab(t)) == t` para las 3 tabs
  - `pageForTab`: `Usage==0`, `Stats==1`, `Settings==2`
  - `tabForPage(-1) == Tab.Usage` y `tabForPage(99) == Tab.Usage` (fallback)
- Acceptance: el test referencia `tabForPage`/`pageForTab` (aún inexistentes)
- Verify: `./gradlew testDebugUnitTest` → **RED** (no compila: símbolos
  inexistentes)
- Files: `app/src/test/java/com/jpyunism/ollamacloudusage/ui/TabPagerMappingTest.kt`

## Task 2: GREEN lógica — funciones `tabForPage`/`pageForTab` (REQ-008)

- [x] Añadir en `UsageScreen.kt` (top-level, bajo el `enum Tab`):
  - `fun pageForTab(tab: Tab): Int` → `indexOf` con fallback 0
  - `fun tabForPage(page: Int): Tab` → `getOrElse { Tab.Usage }`
- Acceptance: el test de Task 1 compila y pasa
- Verify: `./gradlew testDebugUnitTest` → **GREEN**
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt`

## Task 3: Refactor `UsageScreen` a `HorizontalPager` (REQ-001 a REQ-007)

- [x] Imports nuevos: `androidx.compose.foundation.pager.HorizontalPager`,
      `androidx.compose.foundation.pager.rememberPagerState` (sin `@OptIn`)
- [x] Eliminar imports sin uso tras el refactor (`rememberSaveable`,
      `mutableStateOf`, `setValue` si quedan libres)
- [x] Borrar `var tab by rememberSaveable { mutableStateOf(Tab.Usage) }`
- [x] Añadir `val pagerState = rememberPagerState(pageCount = { Tab.entries.size })`
- [x] `NavigationBar`: iterar con `forEachIndexed`; `selected =
      pagerState.currentPage == index`; `onClick = { scope.launch {
      pagerState.animateScrollToPage(index) } }`; icono `selectedIcon` cuando
      `currentPage == index`
- [x] Reemplazar el `Box { when(tab) }` por `HorizontalPager(state =
      pagerState, beyondViewportPageCount = Tab.entries.size - 1)` con el
      `when (tabForPage(page))` dentro
- [x] `UpdateBanner` permanece en el `Column` superior, **fuera** del pager
- [x] `MainActivity.kt` sin cambios
- Acceptance: swipe ↔ bottom bar sincronizados bidireccionalmente; banner
  fijo; sin estado `tab` duplicado
- Verify: `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes;
  lint sin warnings nuevos
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt`

## Task 4: Bump versión (release)

- [x] `versionCode = 34` → `35`
- [x] `versionName = "0.22.1"` → `"0.23.0"`
- Acceptance: build release con la nueva versión
- Verify: `./gradlew assembleRelease` → APK
      `app/build/outputs/apk/release/app-release.apk` (firma CN=JuanPa)
- Files: `app/build.gradle.kts`

## Task 5: Validación en emulador (AVD `test64`) — obligatoria

- [x] Levantar emulador headless + `adb wait-for-device` hasta
      `sys.boot_completed=1`
- [x] Instalar APK release + abrir la app
- [x] 1. Swipe Uso→Stats→Settings y vuelta; tab resaltada cambia con cada
      página (REQ-001, REQ-002) — screenshots
- [x] 2. Tocar cada tab; pager anima a esa página (REQ-002)
- [x] 3. Cambiar idioma estando en Stats → tras recrear, vuelve a Stats
      (no regresión v0.22.1, REQ-003)
- [x] 4. `adb shell am force-stop` + relanzar → tab y estado por sección
      (scroll, Semana/Sesión) conservados (REQ-003/004)
- [x] 5. `UpdateBanner` visible sobre el pager en las 3 secciones (REQ-005)
- [x] 6. `adb logcat -d -b crash` sin `FATAL EXCEPTION`/`ANR` (sin crashes)
- [x] 7. Revisar capturas con
      `skills/vision-delegation/scripts/describe_image.py`
- [x] `adb emu kill`
- Acceptance: los 6 casos manuales pasan; sin crashes/ANRs
- Verify: logcat limpio + capturas visuales correctas
- Files: ninguno (solo verificación)

## Task 6: Publicación del release (regla AGENTS.md)

- [x] Commit + push a `main`
- [x] Tag `v0.23.0` apuntando al commit
- [x] Release en GitHub con APK firmado + notas de versión
- [x] Enviar APK por Telegram (chat `telegram:15710279`)
- Acceptance: release `v0.23.0` publicado con APK firmado CN=JuanPa
- Verify: tag y release visibles en GitHub; APK entregado
- Files: `app/build.gradle.kts`, `app/build/outputs/apk/release/app-release.apk`
