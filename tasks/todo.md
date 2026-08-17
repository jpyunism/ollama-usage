# Tasks: Autoguardado de configuración + orden lógico de secciones

## Task 1: Strings — settings_saved nuevo, save_settings eliminado

- [x] Agregar `settings_saved` ("Configuración guardada" / "Settings saved") en
      `values/strings.xml` y `values-en/strings.xml`
- [x] Eliminar `save_settings` ("Guardar configuración" / "Save settings") de
      ambos archivos
- Verify: `./gradlew lintDebug` (sin hardcoded strings)
- Files: `app/src/main/res/values/strings.xml`,
  `app/src/main/res/values-en/strings.xml`

## Task 2: UsageScreen — SnackbarHost + debounce de 1 s

- [x] `SnackbarHostState` + `SnackbarHost` en el Scaffold
- [x] `onSettingsChanged` con `Job` cancelable + `delay(1000)` +
      `showSnackbar(settings_saved, SnackbarDuration.Short)`
- [x] Pasar `onSettingsChanged` a `SettingsTab`
- Verify: compila (`./gradlew compileDebugKotlin`)
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt`

## Task 3: SettingsTab — autoguardado + reorden de secciones

- [x] Eliminar staging local (`enabled`, `weeklyAlert`, etc.) y botón
      "Guardar configuración"
- [x] Consumir `settings: AlertSettings` directo; cada cambio →
      `vm.updateSettings(...)` + `onSettingsChanged()`
- [x] Reordenar: Apariencia (Idioma → Modo → Temas de color) → Alertas de
      consumo (Notificaciones, Límite semanal, Sesión, Pantalla de bloqueo,
      Reset de cuota, Frecuencia de refresco) → Actualización
- Verify: `./gradlew testDebugUnitTest lintDebug` (verde)
- Files: `app/src/main/java/com/jpyunism/ollamacloudusage/ui/SettingsTab.kt`

## Task 4: Verificación completa + release

- [x] `./gradlew testDebugUnitTest lintDebug assembleRelease` (JAVA_HOME=
      /home/jyunis/jdks/jdk-17.0.20+8, ANDROID_HOME=/home/jyunis/android-sdk)
- [x] Bump versionCode 33 + versionName 0.22.0 en `app/build.gradle.kts`
- [x] Commit + push a main + tag v0.22.0
- [x] Release en GitHub con APK firmado + notas
- [x] Enviar APK por Telegram (chat 15710279)
- Files: `app/build.gradle.kts`
