# AGENTS.md — Ollama Cloud Usage

## Estructura de módulos (Gradle multi-module)

El proyecto está dividido en 9 módulos Gradle. Cada feature/capa vive en su
propio módulo con sus tests. Respetar estos boundaries al agregar código:

- **`:app`** — entry point: `OllamaUsageApp`, `MainActivity`, `UsageScreen`
  (orquestador de las 3 tabs), manifest, proguard. Solo wiring, sin lógica.
- **`:core:model`** — data classes puras (sin dependencias Android):
  `UsageData`, `UsageHistory`, `Balance`, `TrafficLight`, `ProjectionEngine`,
  `ResetStrings`, `DailySummary`, `PrefsKeys`, etc.
- **`:core:net`** — `HttpClientFactory` (OkHttpClient builder).
- **`:core:data`** — repos, scrapers, stores y auth: `UsageRepository`,
  `OllamaUsageScraper`, `OllamaApiUsage`, `AccountStore`, `SecurePrefs`,
  `UsageHistoryStore`, `UpdateChecker`, `LocaleHelper`, `AlertEngine`,
  `UsageNotifier`, `UsageWidgetProvider`, `AppContainer` (DI wiring).
- **`:core:notify`** — services y workers: `UsageMonitorService`,
  `UsageWorker`, `UsageScheduler`, `DailySummaryWorker`, `UpdaterService`,
  `CrashReporter`, `CrashActivity`.
- **`:core:ui`** — `Theme`, `AppDarkMode`, `AppLanguage`, `AppTheme`,
  `SettingsSection` y **todos los recursos** (strings ES/EN, colors, themes,
  drawables, layouts, xml). Se exponen vía `api`.
- **`:feature:usage`** — `UsageTab`, `UsageViewModel`, `ProjectionCard`,
  `CookieSetup`, `CookieWebView`, onboarding completo (Screen + 3 steps + VM +
  Prefs).
- **`:feature:settings`** — `SettingsTab` + 8 secciones (`ui/settings/`).
- **`:feature:stats`** — `StatsTab`.

Reglas:
- Los tests van con su código (mismo módulo).
- `:core:model` no depende de nada; `:core:data` depende de `:core:model` y
  `:core:net`; `:core:notify` depende de `:core:data`; `:feature:*` dependen
  de las `:core:*` que necesiten; `:app` depende de todos.
- Los recursos compartidos van en `:core:ui` y se exponen vía `api`.
- Los manifests con permisos y services van en `:app` y `:core:notify`.
- Mover archivos con `git mv` para preservar blame.

## Publicación de releases (obligatorio)

Al terminar cualquier cambio de funcionalidad en este repo, **publicar siempre la nueva versión** antes de dar la tarea por cerrada:

1. Bump `versionCode` (+1) y `versionName` (semver) en `app/build.gradle.kts`.
2. Correr `./gradlew testDebugUnitTest lintDebug assembleRelease` (JAVA_HOME=/home/jyunis/jdks/jdk-17.0.20+8, ANDROID_HOME=/home/jyunis/android-sdk).
3. Commit + push a `main` y crear tag `v<versionName>` apuntando al commit.
4. Crear release en GitHub con el APK firmado (`app/build/outputs/apk/release/app-release.apk`) y notas de versión.
5. Enviar el APK por Telegram (chat `telegram:15710279`) con el `message` tool.

El APK debe quedar firmado con el cert CN=JuanPa (SHA-256 `05844a35e86e2cf17d604c54268f40b3f53f573e14e046baa722bf2148a5cdda`) para instalarse sobre versiones previas.

## Validación en emulador (obligatorio antes de publicar)

Antes de publicar un release, **validar los cambios navegando la app en el emulador** (no basta con que los tests pasen):

1. Levantar el emulador headless en el server (AVD `test64`, Android 15 / API 35):
   ```bash
   export ANDROID_HOME=/home/jyunis/android-sdk
   export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"
   sg kvm -c "setsid nohup emulator -avd test64 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -memory 2048 -no-snapshot > /tmp/emulator.log 2>&1 < /dev/null &"
   adb wait-for-device  # esperar hasta sys.boot_completed=1
   ```
2. Instalar el APK release y abrir la app: `adb install -r app/build/outputs/apk/release/app-release.apk` + `am start`.
3. Navegar manualmente (con `adb shell input tap` / `uiautomator dump` y capturas `screencap`) por las pantallas afectadas por el cambio, verificando:
   - El flujo principal de la feature/modificación.
   - Los settings que deben persistir (idioma, tema, modo oscuro, frecuencia de refresco) tras force-stop + relanzar.
   - Que cambiar idioma **no resetee la pestaña activa** (regresión conocida v0.22.1).
4. Verificar que no haya crashes ni ANRs: `adb logcat -d -b crash` y buscar `FATAL EXCEPTION` / `ANR in com.jpyunism.ollamacloudusage`.
5. Revisar las capturas con el script de visión (`skills/vision-delegation/scripts/describe_image.py`) para confirmar visualmente el estado.
6. Terminar con `adb emu kill`.

Solo después de esta validación se publica el release (pasos 1-5 de la sección anterior).

## UI: Material 3 (obligatorio)

Toda la interfaz debe usar **exclusivamente componentes Material 3** (`androidx.compose.material3.*`). No usar componentes de Material 2 (`androidx.compose.material.*`), ni vistas XML clásicas (`android.widget.*`, `appcompat`) para UI nueva. Esto incluye:

- Contenedores: `Card`, `Scaffold`, `TopAppBar`, `NavigationBar`, `ModalBottomSheet`, `Snackbar`.
- Inputs: `Button`, `FilledTonalButton`, `OutlinedButton`, `Switch`, `Slider`, `FilterChip`, `AssistChip`, `TextField`, `OutlinedTextField`.
- Feedback: `CircularProgressIndicator`, `LinearProgressIndicator`, `AlertDialog`.
- Tipografía, colores y formas: usar siempre `MaterialTheme.typography`, `MaterialTheme.colorScheme` y `MaterialTheme.shapes` (nunca colores/tipografías hardcodeadas).
- Iconos: `androidx.compose.material.icons` (Icons.Filled / Icons.Outlined).

La app ya usa `androidx.compose.material3:material3` y Material You (temas por color semilla); cualquier UI nueva debe seguir ese patrón. Si algo no existe en M3, justificar en el PR/commit por qué no se puede usar M3 en ese caso concreto.

### Única excepción: el widget de home screen

`UsageWidgetProvider` + `res/layout/widget_usage.xml` usan vistas clásicas (`LinearLayout`, `TextView`, `ProgressBar`) vía `RemoteViews`. Es obligatorio por el framework: los AppWidgets de Android solo aceptan `RemoteViews`, que no soporta Compose. No convertir el widget a Compose ni reemplazar el layout por uno Compose. Todo lo demás (actividad, pantallas, diálogos, notificaciones) debe ser Material 3.
