# Todo: Onboarding guiado (issue #61, spec 01)

## Discovery
- [x] Leer spec `docs/specs/01-onboarding-guiado.md`
- [x] Inspeccionar `MainActivity`, `AppContainer`, `SecurePrefs`, `PrefsKeys`
- [x] Inspeccionar `CookieWebView`, `CookieExtractor`, `AccountStore`, `OllamaApiUsage`
- [x] Confirmar que `HorizontalPager` ya esta en uso (en `UsageScreen`)
- [x] Verificar baseline build OK

## Implementacion
- [ ] 1. `OnboardingPrefs` (helper flag) + `OnboardingPrefsTest` (5+ casos)
- [ ] 2. `OllamaApiKeyValidator` + `OllamaApiKeyValidatorTest` (5+ casos: 200, 401, 500, timeout, error red)
- [ ] 3. `OnboardingViewModel` + `OnboardingViewModelTest` (transiciones)
- [ ] 4. Strings ES + EN (welcome / method / validate / skip / continuar / errors)
- [ ] 5. Composables UI (`OnboardingScreen`, `WelcomeStep`, `MethodStep`, `ValidateStep`)
- [ ] 6. Wire gate en `MainActivity` (lee flag al recompose)
- [ ] 7. `CHANGELOG.md` (entrada feature)

## Validacion
- [ ] `./gradlew testDebugUnitTest --tests "com.jpyunism.ollamacloudusage.OnboardingPrefsTest"`
- [ ] `./gradlew testDebugUnitTest --tests "com.jpyunism.ollamacloudusage.OllamaApiKeyValidatorTest"`
- [ ] `./gradlew testDebugUnitTest --tests "com.jpyunism.ollamacloudusage.OnboardingViewModelTest"`
- [ ] `./gradlew testDebugUnitTest` (regresion: todos los tests)
- [ ] `./gradlew assembleDebug`
- [ ] `./gradlew lintDebug`

## Cierre
- [ ] Commit `feat(onboarding): implement guided onboarding flow (#61)`
- [ ] Push a `main`
