# 09 — Fix: tocar "Actualizar" mata la app (FGS sin `startForeground`)

## Objetivo

Tocar el botón **Update** del banner de actualización (o el de Configuración ›
Actualización) nunca debe terminar el proceso de la app. Si falta el permiso
*Instalar apps desconocidas*, la app debe seguir viva y mostrar el aviso con el
CTA para concederlo.

## Por qué

Reproducido en emulador (`test64`, API 35, APK debug con `versionName = 0.0.1`
para forzar el banner). Al tocar Update, el proceso principal muere con:

```
android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
Context.startForegroundService() did not then call Service.startForeground():
ServiceRecord{... com.jpyunism.ollamacloudusage/.UpdaterService}
  at com.jpyunism.ollamacloudusage.UpdaterService$Companion.start(UpdaterService.kt:146)
  at com.jpyunism.ollamacloudusage.UsageViewModel.startUpdateDownload(UsageViewModel.kt:541)
  at com.jpyunism.ollamacloudusage.ui.UsageScreenKt...UsageScreen(UsageScreen.kt:135)
```

`UpdaterService.onStartCommand()` validaba `canRequestPackageInstalls()` **antes**
de llamar `startForeground()`. Sin el permiso (estado por defecto: el permiso lo
tiene la app que instala —Telegram, el explorador—, no Ollama Usage) el servicio
retornaba sin llamar `startForeground()`; el sistema espera ~5 s y mata el
proceso. Para el usuario: la app "se congela" en el último frame y el update
automático nunca avanza.

Bug secundario que enmascaraba el reporte: la `Application` se inicializa también
en el proceso `:crash` (CrashActivity) y ahí `WorkManager.getInstance()` lanza
`IllegalStateException: WorkManager is not initialized properly`, matando la
pantalla de error. El crash original solo quedaba en `crash.log` y logcat.

## Alcance y límites

Tocar solo:

- `core/notify/src/main/java/com/jpyunism/ollamacloudusage/UpdaterService.kt`
- `app/src/main/java/com/jpyunism/ollamacloudusage/OllamaUsageApp.kt`
- `app/src/main/java/com/jpyunism/ollamacloudusage/ui/UsageScreen.kt` (aviso de
  permiso en el banner + reintento al reanudar)
- `feature/usage/.../UsageViewModel.kt` (cancelar el collector anterior)
- `app/src/androidTest/.../UpdaterServiceStartTest.kt`

**NO** tocar: la lógica de `UpdateChecker`, el parseo de releases, el resto de
la UI, ni hacer refactors de paso.

## Contexto embebido

- `UpdaterService` es un FGS `dataSync` (manifest `:app`). Todo camino de
  `startForegroundService()` debe llamar `startForeground()` en ~5 s, **sin
  excepciones**, o el sistema mata el proceso con `ForegroundServiceDidNotStartInTimeException`.
- `UpdaterService.state: MutableStateFlow<DownloadState>` es el canal por el que
  la UI se entera del progreso; publicar `NeedsPermission` ahí es suficiente
  para que `UsageScreen`/`UpdateSection` muestren el aviso (el VM colecta ese
  flow en `startUpdateDownload`).
- `UsageViewModel` pone `_download = Downloading(0)` antes de invocar el lambda
  `onUpdateDownload`; el `StateFlow` re-emite su valor actual al suscribirse, así
  que un `NeedsPermission` publicado dentro de `start()` llega igual.
- Proceso `:crash`: `OllamaUsageApp.onCreate` agenda `UsageScheduler.schedule`
  (WorkManager) y `DailySummaryWorker.schedule`; en un proceso secundario eso no
  aplica y crashea.

## Criterios de aceptación

1. **Dado** el APK con update disponible y **sin** el permiso de instalar apps,
   **cuando** se toca Update, **entonces** el proceso sobrevive (`pid` estable) y
   se observa `DownloadState.NeedsPermission` (texto + botón "Dar permiso").
2. **Dado** el permiso concedido, **cuando** se toca Update, **entonces** el
   servicio arranca en foreground, descarga el APK con progreso y abre el
   instalador del sistema.
3. **Dado** un crash cualquiera, **cuando** se abre `CrashActivity`, **entonces**
   el proceso `:crash` no crashea por WorkManager y la pantalla de error se ve.
4. `./gradlew testDebugUnitTest lintDebug assembleRelease` verde.

## Evidencia requerida

- logcat sin `ForegroundServiceDidNotStartInTimeException` al tocar Update.
- `adb shell pidof com.jpyunism.ollamacloudusage` idéntico antes/después del tap.
- Capturas del estado `NeedsPermission` y de la descarga en progreso.
