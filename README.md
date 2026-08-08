# Ollama Cloud Usage

App Android para ver el consumo del plan de **Ollama Cloud** (session usage, weekly usage y desglose por modelo).

## Funcionalidades

- **Session usage** (%) con hora de reset
- **Weekly usage** (%) con desglose por modelo (requests y %)
- **Alertas configurables por %**: umbral de alerta y crítico independientes para semana y sesión (sliders 50–99%), con switch maestro de notificaciones
- **Notificaciones automáticas** al cruzar los umbrales configurados (revisión cada 4h en segundo plano con WorkManager, sin duplicados hasta que baje del umbral)
- **Material You**: colores dinámicos en Android 12+, tema claro/oscuro automático
- **Navegación por tabs** (Uso / Alertas) con Material 3 NavigationBar
- **Edge-to-edge** con insets correctos (IME, system bars)
- **Release firmado** con R8 + shrink (APK de ~1.4 MB)

## Cómo funciona

La página `https://ollama.com/settings` muestra el consumo en tiempo real pero no tiene API pública. La app:

1. Guarda tu cookie de sesión (`aid` + `__Secure-session`) localmente.
2. Hace GET a `https://ollama.com/settings` con esa cookie.
3. Parsea el HTML (JSoup) y muestra session/weekly usage y desglose por modelo.

## Stack

- Kotlin + Jetpack Compose (Material 3)
- OkHttp + JSoup (scraping)
- ViewModel + StateFlow (con dispatcher inyectable para tests)
- WorkManager (checks periódicos + notificaciones)
- JUnit4 + Mockk + kotlinx-coroutines-test

## Tests

```bash
./gradlew testDebugUnitTest   # 20 tests: scraper + ViewModel + umbrales de alerta
```

## Setup

1. Abre el proyecto en Android Studio (o `./gradlew assembleRelease`).
2. En la app, pega tu cookie de `ollama.com` (DevTools → Application → Cookies: `aid=...; __Secure-session=...`).
3. Toca "Actualizar" para refrescar.

> ⚠️ La cookie de sesión expira. Cuando la web deje de funcionar, copia una nueva.

## Build

- `./gradlew assembleDebug` — APK debug
- `./gradlew assembleRelease` — APK release firmado (keystore local, NO versionado)

## Disclaimer

Uso personal. No afiliado con Ollama. Scraping de tu propia cuenta con tu propia cookie — respeta los términos de servicio.
