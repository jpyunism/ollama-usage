# Spec: Autoguardado de configuración + orden lógico de secciones

## Objective

La pantalla de Configuración guarda cada cambio automáticamente (sin botón
"Guardar configuración") y, 1 segundo después del último cambio, muestra un
mensaje temporal confirmando que se guardó. Además, las secciones se reordenan
en un orden más lógico.

## Requisitos

1. **Autoguardado**: cada interacción (switch, slider, chip de reset, tema,
   modo, idioma) persiste al instante vía `vm.updateSettings(...)`,
   `vm.updateTheme`, `vm.updateDarkMode` o `vm.updateLanguage`. Se elimina el
   botón "Guardar configuración" y su estado local de staging.
2. **Snackbar debounced**: 1 segundo después del último cambio de configuración
   se muestra un snackbar temporal "Configuración guardada" (auto-dismiss).
   Cambios consecutivos reinician el timer (debounce de 1 s). Si el snackbar ya
   está visible y llega otro cambio, se descarta y se reprograma.
3. **Orden de secciones (nuevo)**:
   1. **Apariencia** — Idioma, Modo claro/oscuro, Temas de color (el idioma se
      agrupa dentro de Apariencia).
   2. **Alertas de consumo** — Notificaciones (master), Límite semanal, Sesión
      actual, Pantalla de bloqueo, Reset de cuota, Frecuencia de refresco.
   3. **Actualización** — versión, chequeo y descarga (última, como sección de
      mantenimiento).
4. **Idioma**: al cambiar idioma la activity se recrea (comportamiento actual);
   el snackbar no aplica a ese cambio puntual (la recreación lo descarta).

## Commands

```
Test:  ./gradlew testDebugUnitTest
Lint:  ./gradlew lintDebug
Build: ./gradlew assembleRelease
```

## Project Structure

```
app/src/main/java/com/jpyunism/ollamacloudusage/ui/SettingsTab.kt → autoguardado + reorden de secciones
app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt → SnackbarHost en el Scaffold (hostState hoisted)
app/src/main/res/values/strings.xml + values-en/strings.xml     → +settings_saved, −save_settings
```

## Code Style

- Material 3 (regla del repo): snackbar vía `SnackbarHost`/`SnackbarHostState`
  del Scaffold; sin colores hardcodeados.
- Debounce con `rememberCoroutineScope()` + `Job` cancelable; el string del
  snackbar se captura con `stringResource` fuera de la coroutine.
- `AlertSettings` se construye desde el estado local en un helper
  `currentSettings()` y se pasa a `vm.updateSettings(...)` en cada cambio.

## Testing Strategy

- Unit: `UsageViewModelTest` ya cubre `updateSettings`/`updateTheme`/
  `updateDarkMode`/`updateLanguage` — sin cambios (el VM no cambia).
- UI: verificación manual (el repo no tiene infra de Compose UI tests).
- Verify: `testDebugUnitTest` + `lintDebug` + `assembleRelease` verdes.

## Boundaries

- **Always**: Material 3, strings es/en, release obligatorio al cerrar
  (v0.22.0, versionCode 33, APK por Telegram).
- **Ask first**: nada nuevo (no se agregan dependencias).
- **Never**: tocar widget/notificación, cambiar el contrato de `AlertSettings`
  ni las claves de prefs.

## Success Criteria

- [ ] No existe botón "Guardar configuración"; cada cambio persiste al instante
      (reabrir la app conserva el valor).
- [ ] 1 s después del último cambio aparece el snackbar "Configuración
      guardada" y desaparece solo; cambios seguidos reinician el timer.
- [ ] Secciones en orden: Apariencia → Alertas de consumo → Actualización.
- [ ] `testDebugUnitTest`, `lintDebug` y `assembleRelease` verdes.

## Decisiones (aprobadas)

- **Idioma**: agrupado dentro de la sección **Apariencia** (primera sub-sección).
- **Orden**: **Apariencia** primero, luego **Alertas de consumo**, luego
  **Actualización**.
