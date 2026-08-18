# Spec: Pull-to-refresh (swipe hacia abajo)

## Objective

Permitir refrescar el consumo manualmente **deslizando hacia abajo**
(pull-to-refresh), el patrón estándar de Material 3, en las tabs **Uso** y
**Estadísticas**.

Hoy el refresh manual es solo el botón "Actualizar" de la tab Uso, y ejecuta
`vm.refresh()`, que setea `UiState.Loading` **ocultando todo el contenido** con
un spinner a pantalla completa. Con pull-to-refresh el contenido permanece
visible y un indicador en el top muestra el progreso de la actualización; el
spinner full-screen queda reservado para la carga inicial y el cambio de
credenciales.

**Quién es el usuario:** el usuario final de la app Android (consumo de Ollama
Cloud). **Por qué:** gesto natural y estándar en apps M3; el refresh manual
actual interrumpe la lectura del consumo.

**Qué es "éxito":** deslizar hacia abajo en Uso o Estadísticas dispara un
refresh que deja el contenido visible, muestra el indicador M3 mientras corre y
actualiza datos/histórico al terminar; el botón "Actualizar" mantiene su
función con la misma UX no destructiva; nada de esto rompe la carga inicial ni
el flujo de auth.

### Acceptance criteria (testeables)

- [ ] En la tab Uso (estado Success), el gesto pull-down dispara `refresh` y
      muestra el indicador; el contenido NO desaparece durante la carga.
- [ ] En la tab Estadísticas, el gesto pull-down dispara el mismo refresh.
- [ ] El botón "Actualizar" existente usa la misma UX no destructiva (indicador
      + contenido visible).
- [ ] `isRefreshing` del ViewModel es `true` durante el refresh y `false` al
      terminar (éxito o error).
- [ ] El refresh desde pull NO setea `UiState.Loading` si ya hay contenido.
- [ ] Refrescos rápidos no se pisan (cancelación del job previo, intacta).
- [ ] La carga inicial (sin datos) y el cambio de credenciales siguen mostrando
      el spinner full-screen actual.
- [ ] `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes.
- [ ] Validación manual en emulador (AVD `test64`) sin crashes/ANRs.

## Requisitos (IDs)

- **REQ-001 — Pull-to-refresh en la tab Uso.** En `UiState.Success`, el
  contenido scrollable de `UsageTab` se envuelve en
  `PullToRefreshBox` (`androidx.compose.material3.pulltorefresh`): el gesto
  vertical hacia abajo dispara `vm.refresh(fromPull = true)`. El indicador usa
  el default de M3 (`PullToRefreshDefaults.Indicator`), colores del tema.
- **REQ-002 — Pull-to-refresh en la tab Estadísticas.** `StatsTab` recibe
  `isRefreshing` y `onRefresh` por parámetro (sigue sin conocer el ViewModel) y
  envuelve su contenido scrollable en el mismo `PullToRefreshBox`.
- **REQ-003 — Refresh silencioso en el ViewModel.** Nueva bandera
  `isRefreshing: StateFlow<Boolean>` y parámetro `fromPull: Boolean = false`
  en `refresh()`. Con `fromPull = true` NO se setea `UiState.Loading` (el
  contenido Success/Error permanece); la bandera se enciende al inicio y se
  apaga en `finally` (éxito o error).
- **REQ-004 — Botón "Actualizar" no destructivo.** El botón existente de
  `SuccessContent` pasa a `vm.refresh(fromPull = true)`: misma UX que el gesto
  (contenido visible + indicador).
- **REQ-005 — Concurrencia intacta.** La cancelación del job previo
  (`refreshJob?.cancel()`) se mantiene para refrescos rápidos (REQ-018 del
  refactor).
- **REQ-006 — TDD RED→GREEN.** Tests unitarios del ViewModel (ver Testing
  Strategy) escritos antes de la implementación.
- **REQ-007 — Material 3 estricto, sin dependencias nuevas.**
  `pulltorefresh` ya viene en `material3 1.4.0` (estable desde 1.3.0, BOM
  2026.06.01). Sin colores/tipografías hardcodeadas; el indicador usa
  `MaterialTheme.colorScheme`.
- **REQ-008 — Release obligatorio.** Al cerrar: bump a `v0.28.0`
  (`versionCode 40`), validación en emulador (AGENTS.md) y release con APK
  firmado + screenshots por Telegram.

## Tech Stack

- Kotlin + Jetpack Compose (Material 3), `compileSdk 36`, `minSdk 26`.
- `androidx.compose.material3:material3:1.4.0` (BOM 2026.06.01) —
  `PullToRefreshBox` + `rememberPullToRefreshState` estables.
- **Sin dependencias nuevas.**

## Commands

```bash
export JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8
export ANDROID_HOME=/home/jyunis/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew testDebugUnitTest    # TDD RED→GREEN
./gradlew lintDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest lintDebug assembleRelease   # verificación completa
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/
  UsageViewModel.kt  → refresh(fromPull), isRefreshing: StateFlow<Boolean>
  ui/UsageTab.kt     → PullToRefreshBox alrededor de SuccessContent (REQ-001);
                       botón Actualizar usa refresh silencioso (REQ-004)
  ui/StatsTab.kt     → PullToRefreshBox; nueva firma (history, isRefreshing, onRefresh) (REQ-002)
  ui/UsageScreen.kt  → pasa isRefreshing + onRefresh a StatsTab
app/src/test/java/com/jpyunism/ollamacloudusage/
  UsageViewModelTest.kt → tests de isRefreshing y refresh silencioso (REQ-006)
docs/
  spec-pull-to-refresh.md → esta spec
```

## Code Style

- Material 3 estricto (regla del repo): el indicador usa los defaults de M3
  (`PullToRefreshDefaults.Indicator` sin parámetros custom de color).
- `StatsTab` no recibe el ViewModel: solo `HistoryState`, `Boolean` y callback.
- No se tocan `UsageRepository`, widget, notificaciones ni `AlertSettings`.

Esqueleto de referencia (patrón, no la implementación final):

```kotlin
// UsageTab.kt — rama Success
PullToRefreshBox(
    isRefreshing = isRefreshing,
    onRefresh = onRefresh,           // vm.refresh(fromPull = true)
    modifier = Modifier.fillMaxSize(),
) {
    SuccessContent(data, lastUpdated, onRefresh, onChangeAuth)
}

// StatsTab.kt
@Composable
fun StatsTab(history: HistoryState, isRefreshing: Boolean, onRefresh: () -> Unit) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        /* Column scrollable actual */
    }
}
```

## Testing Strategy

- **Framework:** JUnit4 + Mockk + kotlinx-coroutines-test (ya en el repo).
- **Qué se testea (REQ-006)** en `UsageViewModelTest` (RED primero):
  - `isRefreshing` es `true` justo después de llamar `refresh()` (antes de
    avanzar el scheduler) y `false` tras `advanceUntilIdle()`.
  - `refresh(fromPull = true)` con datos cargados NO cambia `UiState.Success`
    a `Loading` (el estado sigue siendo `Success` inmediatamente después de la
    llamada) y `isRefreshing` vuelve a `false` al terminar.
  - `refresh(fromPull = true)` con fallo: el estado pasa a `Error` (comporta­
    miento existente) e `isRefreshing` queda en `false` (sin quedar pegado).
  - El refresh normal (`fromPull = false`) sigue seteando `Loading` (no
    regresión).
- **Qué NO se testea en unit:** el gesto de pull y la animación del indicador
  (requieren Compose UI test / emulador). Verificación manual en emulador
  (obligatoria por AGENTS.md).
- **Verify:** `./gradlew testDebugUnitTest lintDebug assembleRelease` verdes +
  navegación manual en emulador (pull en Uso y Estadísticas, botón
  Actualizar, carga inicial, cambio de idioma no resetea la tab).

## Boundaries

- **Always:**
  - Material 3 exclusivo (regla AGENTS.md).
  - Tests verdes + lint limpio antes de release.
  - Validar en emulador (AVD `test64`) antes de publicar, incluyendo la
    regresión de idioma (cambiar idioma no resetea la tab).
  - Release obligatorio al cerrar: bump `versionCode` (+1) y `versionName`
    (semver) en `app/build.gradle.kts`, commit + push + tag `v<versionName>`,
    release en GitHub con APK firmado y envío por Telegram (chat 15710279).
- **Ask first:**
  - Agregar cualquier dependencia nueva (no debería hacer falta).
  - Aplicar pull-to-refresh también a la tab Configuración (no tiene semántica
    de refresh; descartado por defecto).
  - Cambiar el indicador por uno custom (se usa el default M3).
- **Never:**
  - Tocar el widget de home screen ni las notificaciones (fuera de alcance).
  - Hardcodear colores/tipografías.
  - Cambiar el contrato de `AlertSettings`, las claves de prefs ni el pipeline
    de `UsageRepository`.
  - Romper la persistencia de la tab activa tras recreación (regresión v0.22.1).
  - Dejar `isRefreshing` pegada en `true` tras un error de red.

## Success Criteria

- [ ] Pull-down en Uso (Success) refresca con indicador sin ocultar contenido
      (REQ-001).
- [ ] Pull-down en Estadísticas refresca igual (REQ-002).
- [ ] `refresh(fromPull = true)` no setea `Loading` con contenido previo;
      `isRefreshing` refleja el ciclo completo (REQ-003).
- [ ] Botón "Actualizar" con UX no destructiva (REQ-004).
- [ ] Refrescos rápidos no se pisan (REQ-005).
- [ ] Tests RED→GREEN (REQ-006); sin dependencias nuevas; M3 estricto (REQ-007).
- [ ] `testDebugUnitTest`, `lintDebug` y `assembleRelease` verdes.
- [ ] Validación en emulador sin crashes/ANRs + release publicado (v0.28.0,
      versionCode 40) con APK y screenshots (REQ-008).

## Decisiones (técnicas)

- **Enfoque elegido: `PullToRefreshBox` de material3.** API estable desde
  material3 1.3.0; ya disponible en la versión del BOM (1.4.0). Es el patrón
  canónico M3: maneja el gesto (nested scroll sobre el hijo scrollable), el
  estado y el indicador.

- **Alternativas descartadas:**
  1. **accompanist-swiperefresh** — librería externa deprecada (su funcionalidad
     se movió a material3). Añadiría una dependencia muerta. Descartado.
  2. **`Modifier.nestedScroll` + indicador manual** — reimplementar el gesto y
     el estado del indicador a mano: más código y propenso a bugs de umbral.
     Descartado.
  3. **Reusar `UiState.Loading` para el pull** — ocultaría el contenido con el
     spinner full-screen (UX actual del botón), que es justo lo que se quiere
     evitar. Descartado; por eso nace `isRefreshing` (REQ-003).

- **`isRefreshing` separado de `UiState`:** el pull-to-refresh clásico mantiene
  el contenido visible; `UiState` solo describe el contenido (Idle/Loading/
  Success/Error), no el progreso de un refresh en segundo plano. La bandera
  extra es el mínimo necesario para el indicador y es testeable en unit.

- **`refresh(fromPull: Boolean = false)`:** el parámetro con default preserva
  todos los call sites existentes (init, saveCookie, saveApiKey) que siguen con
  la UX de carga completa; solo pull y botón usan el modo silencioso.

- **Alcance Uso + Estadísticas:** ambas son vistas de datos que se refrescan;
  Configuración es configuración local (sin semántica de refresh). El costo de
  incluir Stats es mínimo (misma API) y el usuario espera el gesto en toda
  vista de datos.

- **StatsTab sin ViewModel:** se mantiene el desacoplamiento actual (recibe
  `HistoryState`); se le agregan `isRefreshing: Boolean` y `onRefresh: () ->
  Unit` hoisteados desde `UsageScreen` (patrón state hoisting).

## Open Questions

1. **Indicador:** se usa el default M3 (`PullToRefreshDefaults.Indicator`).
   ¿Se prefiere algún ajuste (p.ej. `scale = false` o umbral distinto)?
   *(Recomendado: default, sin cambios.)*
2. **Versión del release:** se asume `0.28.0` / `versionCode 40` (feature
   menor). Confirmar si se prefiere otro número.
