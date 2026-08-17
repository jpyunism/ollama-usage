# Plan: Autoguardado de configuración + orden lógico de secciones

## Overview

Eliminar el botón "Guardar configuración" de la pantalla de Configuración:
cada cambio persiste al instante vía el ViewModel y, 1 s después del último
cambio, un snackbar temporal confirma "Configuración guardada". Las secciones
se reordenan: **Apariencia** (Idioma → Modo → Temas de color) → **Alertas de
consumo** → **Actualización**.

## Architecture Decisions

- **Sin staging local**: `SettingsTab` deja de usar `remember { mutableStateOf }`
  para los valores de alertas; consume directamente el parámetro `settings:
  AlertSettings` (ya viene del `collectAsStateWithLifecycle` en `UsageScreen`).
  Cada handler de cambio construye el nuevo `AlertSettings` y llama
  `vm.updateSettings(...)` (que ya persiste en prefs y reprograma el worker si
  cambió la frecuencia). `updateTheme`/`updateDarkMode`/`updateLanguage` ya
  persisten solos — sin cambios en el ViewModel.
- **Snackbar debounced en UsageScreen**: el `Scaffold` de `UsageScreen` gana un
  `SnackbarHostState` + `SnackbarHost`. `SettingsTab` recibe
  `onSettingsChanged: () -> Unit` y lo invoca en cada cambio. El handler en
  `UsageScreen` cancela el `Job` anterior y lanza uno nuevo con
  `delay(1000)` + `showSnackbar(settings_saved, Short)`. El string se captura
  con `stringResource` fuera de la coroutine (regla del repo).
- **Idioma**: al cambiar idioma la activity se recrea (comportamiento actual);
  el snackbar no aplica a ese cambio puntual (la recreación lo descarta).
- **Sliders**: `onValueChange` dispara `updateSettings` en cada tick (barato:
  `SharedPreferences.apply()`); el debounce del snackbar absorbe el ruido.
- **Orden de secciones** (aprobado): Apariencia (Idioma, Modo, Temas) →
  Alertas de consumo (Notificaciones, Límite semanal, Sesión, Pantalla de
  bloqueo, Reset de cuota, Frecuencia de refresco) → Actualización.
- **Strings**: +`settings_saved` (es/en), −`save_settings` (es/en).

## Task List

- [ ] Task 1: Strings — agregar `settings_saved`, eliminar `save_settings`
  - Files: `app/src/main/res/values/strings.xml`, `values-en/strings.xml`
  - Verify: `./gradlew lintDebug` (sin hardcoded strings)
- [ ] Task 2: `UsageScreen` — SnackbarHost + debounce de 1 s
  - `SnackbarHostState` + `SnackbarHost` en el Scaffold
  - `onSettingsChanged` con `Job` cancelable + `delay(1000)` +
    `showSnackbar(..., SnackbarDuration.Short)`
  - Pasar `onSettingsChanged` a `SettingsTab`
  - Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt`
- [ ] Task 3: `SettingsTab` — autoguardado + reorden de secciones
  - Eliminar staging local y botón "Guardar configuración"
  - Cada cambio → `vm.updateSettings(...)` + `onSettingsChanged()`
  - Reordenar: Apariencia (Idioma → Modo → Temas) → Alertas → Actualización
  - Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/SettingsTab.kt`
- [ ] Task 4: Verificación completa
  - `./gradlew testDebugUnitTest lintDebug assembleRelease` (JAVA_HOME +
    ANDROID_HOME del server)
  - Files: ninguno (solo verificación)

## Risks

- Sliders de umbral: al arrastrar se persiste en cada tick — aceptable
  (apply() es asíncrono y barato); el snackbar solo aparece al soltar (1 s
  después del último tick).
- Recreación por idioma: el snackbar de ese cambio no se muestra — aceptado
  en spec.
