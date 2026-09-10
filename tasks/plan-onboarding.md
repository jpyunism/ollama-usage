# Plan: Onboarding guiado (spec `docs/specs/01-onboarding-guiado.md`, issue #61)

Spec ya aprobado por el usuario. No se modifica. Implementacion estricta.

## Restricciones del repo

- Kotlin 1.9.x + Compose + Material 3 (establecido por AGENTS.md).
- DI manual: `AppContainer`; los nuevos componentes se exponen por ahi.
- `SecurePrefs` para prefs cifrados (AES-256-GCM). El flag `onboarding_completed`
  NO es un secreto -> se guarda en claro (no requiere encriptacion).
- Strings via `R.string.*` en ES y EN.
- `rememberSaveable` (no `remember`) para sobrevivir rotacion, por issue #64.
- `CookieWebView` ya existe y se reutiliza tal cual para el paso WebView.
- `OllamaApiUsage` ya hace una llamada real a `https://ollama.com/api/usage`
  con Bearer; el `OllamaApiKeyValidator` nuevo hace una llamada ligera (200 OK
  => valida; 401 => invalida; timeout/error => no concluyente). La spec deja
  puerta abierta al endpoint `/api/me` o `/api/tags`; usamos `/api/usage` por
  consistencia con el resto del repo (AuthSource.API_KEY).

## Stack / decisiones

- Navegacion entre 3 pasos: `HorizontalPager` + `rememberPagerState` (mismo
  patron que `UsageScreen`). La spec sugiere Navigation Compose, pero la
  estructura ya usa HorizontalPager y no necesitamos backstack real (el flujo
  es lineal y "Saltar" salta a Main). Decido: HorizontalPager.
- Sin dependencias nuevas: `HorizontalPager`, `Material3`, `OkHttp`,
  `MockK`, `JUnit`, `coroutines-test` ya estan todas en el proyecto.
- ViewModel `OnboardingViewModel` no requiere Context: recibe `prefs` y el
  `validator` por inyeccion, igual que `UsageViewModel`. Factory por
  companion.

## Pasos

1. **OnboardingPrefs** (helper puro sobre SharedPreferences) + tests.
2. **OllamaApiKeyValidator** (OkHttp ligero, suspend fun `validate(key): Boolean`)
   + 5 tests (200 OK, 401, 500, timeout, error de red).
3. **OnboardingViewModel** (estado: currentStep, pickedMethod, isValidating,
   apiKeyInput, cookieCaptured, validationError) + tests de transiciones.
4. **Strings** ES + EN (todos los textos nuevos + boton Saltar).
5. **Composables UI**:
   - `OnboardingScreen` (raiz con HorizontalPager de 3 paginas + boton Saltar
     global arriba).
   - `OnboardingWelcomeStep`.
   - `OnboardingMethodStep` (elige WebView vs API key; usa CookieWebView si
     WebView; valida si API key).
   - `OnboardingValidateStep` (muestra "Continuar" cuando el metodo ya esta
     guardado / validado).
6. **Wire en MainActivity**: gate `if (!onboarding_completed) OnboardingScreen
   else UsageScreen`. Tras completar el flujo, `vm.markCompleted()` persiste
   el flag y MainActivity vuelve a `UsageScreen` (gate lee de prefs al recompose).
7. **CHANGELOG.md**: crear entrada en ES explicando la nueva feature.
8. **Build + test + lint**.

## Decisiones de scope que el spec deja abiertas (resueltas)

- Q1 (re-mostrar al cambiar metodo): NO, solo primer launch (spec recomienda).
- Q2 (permiso notificaciones durante onboarding): NO en este PR, queda fuera
  de scope. El permiso ya se pide en `MainActivity.onCreate` antes de
  `setContent`, asi que al primer launch el sistema operativo ya muestra el
  dialog aparte del onboarding.
- Q3 (elegir tema en onboarding): NO, ya hay 8 temas en Settings.
