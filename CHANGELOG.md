# Changelog

Todas las novedades de la app, agrupadas por version. Sigue semver
(`MAJOR.MINOR.PATCH`).

## v0.34.1 (en curso) — issue #61

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
