# Spec: Onboarding guiado para primera configuracion

Issue: #61
Estado: draft
Autor: Hermes (subagente spec, 2026-09-10)

## Contexto y objetivo

Hoy cuando un usuario nuevo instala la app y no tiene cookie/API key, aterriza
directo en la pantalla de login sin contexto: no sabe que hace la app, que
permisos necesita, ni por que debe pegar una cookie. Esto produce abandono
en los primeros 30 segundos y soporte repetido en GitHub.

**Objetivo:** que un usuario nuevo complete la primera configuracion en menos
de 60 segundos, entienda que hace la app y que permisos consume, y llegue a la
pantalla principal con un metodo de auth valido funcionando.

**Usuario objetivo:** primera vez que abre la app, sin auth previa. Despues de
completarlo una vez, no se vuelve a mostrar salvo que se fuerce desde
Settings > Ayuda.

## Assumptions (confirmar antes de implementar)

1. El usuario puede elegir entre WebView (cookie) o API key desde el dia 1
   (multi-cuenta ya soporta API key; cookie sigue siendo el metodo legacy).
2. El endpoint de validacion de API key es una llamada ligera a
   `https://ollama.com/api/me` o equivalente. Si no existe, usamos
   `https://ollama.com/api/tags` (devuelve 401 rapido si la key es invalida).
3. El WebView de login ya existe (`CookieWebView`); solo hay que envolverlo
   y mostrar progreso de deteccion de cookies.
4. No queremos romper el flujo actual de "pegar cookie manualmente" desde
   Settings; el onboarding es una capa adicional, no un reemplazo.

## Tech stack

- Kotlin 1.9.x (mismo que el proyecto)
- Jetpack Compose + Material 3 (consistente con el resto)
- Navigation Compose para el flujo de 3 pantallas
- OkHttp (ya en deps) para el ping de validacion de API key
- SharedPreferences cifrado (`SecurePrefs`) para persistir el flag
  `onboarding_completed`

## Comandos

```bash
# Tests unitarios del flujo
./gradlew testDebugUnitTest --tests "com.jpyunism.ollamacloudusage.Onboarding*"
# Build
./gradlew assembleDebug
# Lint
./gradlew lintDebug
# Test instrumentado del flujo (Compose UI test)
./gradlew connectedDebugAndroidTest --tests "com.jpyunism.ollamacloudusage.OnboardingFlowTest"
```

## Estructura propuesta

```
app/src/main/java/com/jpyunism/ollamacloudusage/
  ui/
    onboarding/
      OnboardingScreen.kt          # raiz con HorizontalPager de 3 paginas
      OnboardingWelcomeStep.kt     # pantalla 1
      OnboardingMethodStep.kt      # pantalla 2 (WebView vs API key)
      OnboardingValidateStep.kt    # pantalla 3 (validacion en vivo)
      OnboardingViewModel.kt       # estado + validacion
    UsageScreen.kt                 # mod: gate "if !onboarding_completed -> Onboarding"
  OnboardingPrefs.kt               # helpers para leer/escribir el flag
  OllamaApiKeyValidator.kt         # ping de validacion (inyectable)
app/src/test/.../
  OnboardingPrefsTest.kt
  OnboardingViewModelTest.kt
  OllamaApiKeyValidatorTest.kt
app/src/androidTest/.../
  OnboardingFlowTest.kt            # UI test del flujo completo
```

## Code style (ejemplo representativo)

```kotlin
@Composable
fun OnboardingMethodStep(
    onPickWebView: () -> Unit,
    onPickApiKey: (String) -> Unit,
    isValidating: Boolean,
    validationError: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.onboarding_method_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        // Dos Card grandes con icono + titulo + descripcion
        MethodCard(
            icon = Icons.Outlined.AccountCircle,
            title = stringResource(R.string.onboarding_method_webview),
            description = stringResource(R.string.onboarding_method_webview_desc),
            onClick = onPickWebView,
        )
        MethodCard(
            icon = Icons.Outlined.Key,
            title = stringResource(R.string.onboarding_method_apikey),
            description = stringResource(R.string.onboarding_method_apikey_desc),
            onClick = { /* abre dialog con TextField + boton Validar */ },
        )
        if (isValidating) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        validationError?.let { err ->
            Text(
                text = err,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
```

Convenciones:
- Composables privados con PascalCase, publicos exportables con prefijo del
  feature (`Onboarding*`).
- Strings siempre via `R.string.*` (ES/EN, ya se mantiene el estandar).
- Estilo visual consistente con `UsageScreen`/`SettingsTab`.
- Estados en `rememberSaveable` (no `remember` — ver issue #64).

## Testing strategy

- **Unit tests (JVM, MockK):** `OnboardingPrefsTest` (lectura/escritura del
  flag), `OnboardingViewModelTest` (transiciones de estado), y
  `OllamaApiKeyValidatorTest` (mockear OkHttp para respuestas validas,
  invalidas, timeout, 401, 500, error de red).
- **Test instrumentado (Compose UI):** `OnboardingFlowTest` que recorre las
  3 paginas, valida que el boton "Saltar" funciona, y que completar el
  flujo navega a la pantalla principal.
- **Cobertura objetivo:** >= 80% en archivos nuevos del feature.
- **No testear:** animaciones, Material 3 components per se, scroll.

## Boundaries

- **Always do:**
  - Persistir el flag `onboarding_completed` en `SecurePrefs`.
  - Localizar todos los strings (ES/EN).
  - Mostrar CTA "Saltar" en cada paso (es un onboarding, no una prision).
  - Validar en el emulador (API 35) antes de mergear, segun AGENTS.md.
  - Registrar el cambio en CHANGELOG.md bajo la seccion de la proxima version.
- **Ask first:**
  - Cambiar el endpoint de validacion de API key.
  - Agregar dependencias (Navigation Compose ya esta; no deberia necesitar mas).
  - Cambiar el orden de los pasos del flujo.
- **Never do:**
  - Reemplazar el flujo actual de Settings > Configurar cookie (debe seguir
    accesible para usuarios existentes que renuevan).
  - Mostrar el onboarding a usuarios que ya tienen auth valida.
  - Bloquear el uso de la app si el onboarding falla a mitad (degradar
    gracefully a "ir a Settings").

## Success criteria

1. Usuario nuevo ve las 3 pantallas en orden al primer launch.
2. Usuario puede saltar el onboarding desde cualquier paso.
3. Al elegir API key, el ping de validacion responde en <3s con feedback
   visual (OK/FAIL).
4. Al elegir WebView, la pantalla muestra progreso de deteccion y CTA
   "Continuar" cuando detecta `aid` + `__Secure-session`.
5. Al completar, el flag `onboarding_completed = true` se persiste.
6. En el segundo launch, la app abre directo en la pantalla principal.
7. Todos los tests pasan; lint limpio; build release firmado en verde.
8. Validado en emulador API 35: flujo completo, rotacion, dark mode, ES/EN.

## Open questions

1. ¿Mostrar el onboarding tambien cuando se cambia de cookie a API key (o
   viceversa) por primera vez? Mi recomendacion: NO, solo en el primer
   launch. Configuracion de auth adicional va por Settings.
2. ¿Pedimos permiso de notificaciones (POST_NOTIFICATIONS) durante el
   onboarding? Mi recomendacion: SI, con explicacion de por que (alertas
   de umbral). Se pide despues del paso 3, no antes.
3. ¿Incluimos un paso opcional de "elegir tema de color" en el onboarding?
   Mi recomendacion: NO, ya hay 8 temas en Settings; mantener onboarding
   en 3 pasos para no abrumar.

## Estimacion

3-4 horas de implementacion + 1h de test/QA. Total: 4-5h.
