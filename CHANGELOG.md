# Changelog

Todas las novedades de la app, agrupadas por version. Sigue semver
(`MAJOR.MINOR.PATCH`).

## Unreleased

### Validacion en vivo de API key (issue #63)

Al pegar una API key en el flujo de Configuracion (agregar cuenta o cambiar
acceso), la app la valida en vivo contra ollama.com antes de guardar:

- Spinner mientras valida.
- "API key valida" en verde cuando el ping responde 2xx.
- "API key invalida o sin permisos" en rojo cuando responde 401.
- Degrada a "no se pudo validar" (no concluyente) si hay timeout/red/servidor.
- El boton Guardar queda deshabilitado hasta que la validacion pase.

Reutiliza `OllamaApiKeyValidator` (timeout 5s) y aplica el mismo patron de
feedback en el dialogo de agregar cuenta y en el setup de acceso.

### Refactor (issue #65): Gradle multi-module

El modulo `app` (95 archivos .kt) se dividio en 9 modulos Gradle por capa
y feature, mejorando el incremental build time y forzando boundaries
claros entre componentes. Refactor mecanico (`git mv`), sin cambio de
logica ni de API publica.

- `:core:model` — data classes puras (UsageData, UsageHistory, Balance,
  TrafficLight, ProjectionEngine, ResetStrings, DailySummary, PrefsKeys).
- `:core:net` — `HttpClientFactory` (OkHttpClient builder compartido).
- `:core:data` — repos/scrapers/stores (UsageRepository, OllamaApiUsage,
  OllamaUsageScraper, UsageHistoryStore, AccountStore, SecurePrefs,
  UpdateChecker, AlertEngine, UsageError), `UsageNotifier`,
  `UsageWidgetProvider` y `AppContainer` (DI wiring).
- `:core:notify` — services/workers (UsageMonitorService, UsageWorker,
  UsageScheduler, DailySummaryWorker, UpdaterService, CrashReporter,
  CrashActivity).
- `:core:ui` — Theme, AppDarkMode/AppLanguage/AppTheme, SettingsSection
  (IconBox/ResetModeChip) y todos los recursos compartidos (strings ES/EN,
  colors, themes, drawables, layouts, xml).
- `:feature:usage` — UsageScreen/UsageTab/UsageViewModel, onboarding
  (OnboardingScreen + steps + OnboardingViewModel/OnboardingPrefs),
  CookieSetup/CookieWebView, ProjectionCard.
- `:feature:settings` — SettingsTab + secciones de configuracion.
- `:feature:stats` — StatsTab.
- `:app` — solo entry point (OllamaUsageApp, MainActivity, UsageScreen
  orquestador) + manifest + proguard.

Los tests viajan con su codigo (mismo modulo). `settings.gradle.kts`
incluye los 9 modulos. R8 sigue generando el mapping file y el APK release
queda firmado con el mismo cert.

## v0.35.0 (2026-09-10)

### Onboarding guiado para primera configuracion (issue #61)

Antes, un usuario nuevo aterrizaba directo en la pantalla de login sin
saber que hacia la app ni por que necesitaba una cookie o API key. Ahora:

- **3 pantallas** en un flujo guiado (Welcome / Method / Validate) con
  navegacion swipe entre paginas.
- **Bienvenida** explica que hace la app y los beneficios antes de pedir
  credenciales.
- **Elegir metodo** entre WebView (captura automatica de cookie al iniciar
  sesion) o API key (pegar + validar contra ollama.com).
- **Validacion** de la API key contra `https://ollama.com/api/usage`
  (Bearer token) con feedback inmediato: valida, invalida (401) o
  no concluyente (5xx / timeout).
- **Boton "Saltar"** en la TopAppBar, siempre visible: el usuario puede
  irse a Configuracion y configurar la auth despues.
- **Flag `onboarding_completed`** se persiste en `SecurePrefs` (en claro,
  no es un secreto). En el segundo arranque la app abre directo en la
  pantalla principal.

### Componentes nuevos

- `OnboardingPrefs` — helper de flag, testeable sin Android.
- `OllamaApiKeyValidator` — ping ligero al endpoint, mapea HTTP a
  `Result.{Valid, Invalid, Inconclusive}`, inyectable.
- `OnboardingViewModel` — estado del flujo, navegacion, validacion,
  persistencia al completar.
- `OnboardingScreen` (raiz con HorizontalPager),
  `OnboardingWelcomeStep`, `OnboardingMethodStep`, `OnboardingValidateStep`
  — Material 3, consistente con el resto del repo.
- `MainActivity` — gate `if !onboarding_completed -> OnboardingScreen else
  UsageScreen`.

### Tests

- `OnboardingPrefsTest` (7 casos): lectura/escritura/reset/round-trip.
- `OllamaApiKeyValidatorTest` (10 casos): 200, 201, 401, 403, 500, 503,
  IOException, key vacia, key en blanco, trim de espacios.
- `OnboardingViewModelTest` (14 casos): estado inicial, navegacion
  forward/back, pickMethod, validacion (3 outcomes), persistencia API key,
  persistencia cookie, skip, reset de validacion al cambiar input.

### Localizacion

- 24 strings nuevos en ES y EN (titulos, bullets, errores, CTAs).

### No rompe

- El flujo existente de "Configuracion > Cambiar acceso" sigue accesible:
  el onboarding NO lo reemplaza, solo agrega una capa para el primer launch.
- Si el usuario falla a mitad del onboarding, la app degrada
  gracefully a Configuracion (puede seguir saltando).

### Corregido (issue #64)

- El estado de la UI ahora sobrevive a la rotacion del dispositivo y a la
  muerte de proceso: se migraron los estados de `remember` a
  `rememberSaveable` en las pestanas de uso, estadisticas y ajustes. Esto
  incluye el filtro de periodo, la comparativa semanal, el tooltip del
  grafico, el sheet de evolucion por modelo, los dialogos de cuentas y el
  time picker del resumen diario. Se agrego el test instrumentado
  `UiStateSurviveRotationTest`.

### Agregado (issue #54)

- Proyeccion de agotamiento en la pantalla principal: tarjeta que
  muestra, a este ritmo de consumo, cuando se agotaria la cuota semanal y de
  sesion. Solo informativa; se oculta si hay menos de 3 snapshots en el
  historico. Logica pura en `ProjectionEngine` (regresion lineal sobre los
  ultimos 7 dias), UI en `ProjectionCard`, expuesta via `UsageViewModel`.

### Refactor (issue #58)

- `SettingsTab.kt` (997 lineas) se dividio en secciones colapsables en
  `ui/settings/`: `SettingsSection` (composable generico con
  `rememberSaveable` + `animateContentSize`), `AlertSection`, `ThemeSection`,
  `AccountsSection`, `BackupSection`, `UpdateSection`, `DailySummarySection`,
  `RefreshSection` y `Thresholds`. `SettingsTab.kt` quedo como orquestador
  (71 lineas). Sin cambio de funcionalidad.
