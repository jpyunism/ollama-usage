# Changelog

Todas las novedades de la app, agrupadas por version. Sigue semver
(`MAJOR.MINOR.PATCH`).

## Unreleased

### Fix: secciones de Configuracion mal alineadas al expandir

Al expandir una seccion en Configuracion el contenido quedaba pegado al borde
de la tarjeta (sin el padding de 16 dp de la cabecera) y algunas secciones
duplicaban el encabezado (icono + titulo + subtitulo) dentro del contenido.

- `SettingsSection` ahora aplica padding horizontal de 16 dp al contenido
  (alineado con la cabecera) y padding inferior de 16 dp.
- Solo la cabecera es clickeable (no la tarjeta entera): tocar un slider,
  switch o boton del contenido ya no colapsa la seccion.
- Nuevo slot `trailing` en la cabecera para controles como el Switch, que
  evita duplicar el encabezado (aplicado en Resumen diario).
- `UpdateSection` ya no repite el encabezado dentro del contenido.
- Fix adicional: el time picker del resumen diario guardaba siempre la hora
  inicial (el estado elegido no se leia al confirmar).

## v0.36.1 (2026-09-11)

### Corregido (issue #74): alerta de ritmo en bucle con API key

Con API key, el ancla de sesión es `fallbackResetAnchor(SESSION)` que devuelve
null: sin inicio de período no se puede proyectar el ritmo y el guard por
período nunca coincidía, por lo que la alerta de ritmo se disparaba en cada
refresh. Ahora:

- `checkPaceAlerts()` saltea la alerta cuando el ancla es null (no se puede
  calcular sin inicio del período).
- `paceAlert()` devuelve null sin ancla (defensa en la capa pura).
- Test: API key con 2 refrescos consecutivos verifica que la alerta de ritmo
  de sesión no se repite.

### Fix: hint cuando la comparativa semanal no esta disponible (issue #80)

La comparativa semana actual vs anterior (`comparisonSeries`) necesitaba un
`resetAnchor` real. Con API key el anchor de sesion es null y la comparativa se
desactivaba silenciosamente, sin que el usuario supiera por que no la veia.

Ahora la comparativa usa solo el ancla real (cookie/scraper o el reset
detectado automaticamente, issue #15) y, si no hay ancla, se muestra un hint
"Comparativa no disponible sin fecha de reset (solo cookie)" en lugar de
desactivarla en silencio.

### Fix: widget no se actualiza en Android < 12 (issue #79)

`widgetSaver` y `widgetUpdater` se ejecutaban dentro de `propagate()`, que corre
en el `ioDispatcher`. Pero `AppWidgetManager.updateAppWidget()` exige main
thread en Android < 12, así que la actualización podía fallar silenciosamente y
el widget quedaba sin refrescar.

Ahora los side-effects del widget (`saveData` + `updateAll`) corren en el main
dispatcher (`Dispatchers.Main`, inyectable como `mainDispatcher`), mientras el
resto del pipeline sigue en el `ioDispatcher`. Se agregó un test que verifica
que `widgetSaver`/`widgetUpdater` se ejecutan en el main dispatcher.

### Fix: apiKeyValidator usa el httpClient compartido (issue #81)

`UsageViewModel` defaulteaba a `OllamaApiKeyValidator()` (con su propio
`OkHttpClient` sin interceptors). Al validar la API key en Settings, la
validacion hacia una llamada HTTP extra por fuera del cliente compartido.

Ahora se quito el default del parametro y el factory inyecta siempre
`OllamaApiKeyValidator(client = container.httpClient)`, el mismo cliente
compartido por el resto de la app (interceptors, timeouts).

### Fix: markChecked se ejecuta aunque el check de update falla (issue #76)

`UsageWorker` ejecutaba `UpdateChecker.markChecked()` despues de `runCatching`.
Si `UpdateChecker.check()` fallaba con excepcion, `runCatching` capturaba el
error pero `markChecked()` igual se ejecutaba, asi que el usuario no veia la
notificacion de update disponible aunque esta existiera.

Ahora `markChecked()` se ejecuta solo dentro del `if (info != null)`: si el
check falla, no se marca y el proximo ciclo reintenta.

### Fix: backoff exponencial se resetea al reiniciar el servicio (issue #77)

`UsageMonitorService` reseteaba `consecutiveFailures = 0` en cada
`onStartCommand`. Con `START_STICKY`, si el sistema reiniciaba el servicio, el
backoff se perdía y volvía a martillar ollama.com.

Ahora el contador de fallos consecutivos se persiste en prefs y se carga al
arrancar el servicio, de modo que un reinicio sticky conserva el backoff
acumulado (1, 2, 4... 30 min máx).

### Fix: banner de cookie expira no se actualiza tras renovar (issue #78)

Cuando la cookie expiraba, `refreshAndPropagate()` fallaba con
`CookieExpiredException` y la UI mostraba el banner de "cookie expira pronto".
Al pegar una cookie nueva, `saveCookie()` guardaba y registraba la renovación,
pero el banner podía seguir mostrándose porque el estado interno no se
recalculaba.

Ahora `UsageRepository` recalcula `cookieExpiryStatus()` al inicio de cada
`refreshAndPropagate()` y al registrar una renovación, y `UsageViewModel`
propaga ese estado al banner tras cada refresh (aunque el fetch falle). El
banner desaparece en cuanto se renueva la cookie.

### Fix: notificaciones de umbral se sobreescriben (issue #73)

Todas las alertas de umbral (semanal, sesion, pace) compartian el mismo ID de
notificacion (1001), asi que si dos alertas disparaban en el mismo ciclo la
segunda reemplazaba a la primera. Ahora cada tipo de alerta tiene su propio ID:

- Semanal: `WEEKLY_ALERT_ID = 1007`
- Sesion: `SESSION_ALERT_ID = 1008`
- Pace semanal: `PACE_WEEKLY_ID = 1009`
- Pace sesion: `PACE_SESSION_ID = 1012`

`notifyLimit()` recibe el ID como parametro (default 1001) y `UsageRepository`
pasa el ID correcto segun el tipo. Si semana y sesion cruzan umbral en el mismo
ciclo, el usuario ve ambas notificaciones.

### Fix: cookie/API key se pierde tras update (issue #75)

`SecurePrefs.purgeLegacy()` borraba el XML legacy (`ollama_usage`, cookie/API
key en claro) antes de migrar al formato cifrado (`ollama_usage_secure_v2`).
Los usuarios que aún no habían migrado perdían la cookie: el viejo se borraba
y el nuevo cifrado no existía todavía.

Ahora `migrateLegacy()` lee el valor legacy con SharedPreferences, lo cifra y
lo guarda en el formato actual, y solo después borra los archivos de formatos
anteriores. La migración no sobreescribe un secreto ya presente en el destino.

## v0.36.0 (2026-09-10)

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

### Recordatorio proactivo de cookie (issue #62)

La cookie de sesion de ollama.com expira periodicamente y antes el usuario
solo se enteraba al querer usar la app. Ahora la app detecta proactivamente
cuando la cookie esta por expirar (menos de 3 dias estimados) o ya expiro y
avisa con un CTA para renovarla:

- **Banner persistente** en la pantalla principal: "Tu cookie expira pronto"
  con el boton "Renovar ahora" que abre el WebView de login de ollama.com
  para capturar la cookie nueva automaticamente.
- **Notificacion** (si las alertas estan activadas) cuando la cookie expira
  o esta por expirar, con tap que abre la app.
- **Reset automatico**: al renovar la cookie exitosamente se registra la
  nueva fecha y el banner desaparece.
- **No molesta con API key**: el recordatorio solo aplica cuando el metodo
  de auth es cookie.

### Componentes nuevos

- `CookieExpiry` (`:core:model`) — logica pura del calculo de dias restantes
  y umbral de aviso, testeable en JVM.
- `UsageRepository.cookieExpiryStatus()` / `recordCookieRenewal()` /
  `notifyCookieExpiryIfNeeded()` (`:core:data`).
- `UsageNotifier.notifyCookieExpiry()` (`:core:data`).
- `CookieExpiryBanner` (`:app`) — banner Material 3 con CTA.
- `UsageViewModel.cookieExpiry` / `renewCookie()` (`:feature:usage`).
- `UsageWorker` y `UsageMonitorService` notifican en cada ciclo.

### Tests

- `CookieExpiryTest` (8 casos): sin renovacion, recien renovada, umbrales de
  3 dias, expirada, vida util custom.
- `UsageRepositoryTest` (4 casos nuevos): API key no molesta, sin renovacion
  EXPIRED, renovacion reciente OK, persistencia del timestamp.

### Localizacion

- 7 strings nuevos en ES y EN (banner, CTA, notificaciones).

### Notificación proactiva de reset de sesión (issue #57)

La app ahora avisa al usuario 1 hora antes de que se resetee la sesión de
Ollama Cloud, para que no pierda la ventana de uso. Solo notifica si el
consumo de la sesión supera el 70% (caso en que importa avisar).

- `SessionResetScheduler` (`:core:model`): lógica pura del delay (resetAt - 1h)
  y del umbral de disparo (> 70%).
- `SessionResetWorker` (`:core:notify`): OneTimeWorkRequest con delay hasta
  el aviso; al disparar re-verifica el consumo con el último snapshot y no
  notifica si bajó del 70% (criterio de cancelación).
- Se reprograma en cada refresh que actualice el `resetAt` (vía
  `UsageRepository` → `SessionResetWorker.schedule`).
- Strings ES/EN en `:core:ui`.

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
