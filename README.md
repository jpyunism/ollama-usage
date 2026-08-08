# Ollama Cloud Usage

App Android para ver el consumo del plan de **Ollama Cloud** (session usage, weekly usage y desglose por modelo).

## Funcionalidades

- **Session usage** (%) con hora de reset
- **Weekly usage** (%) con desglose por modelo (requests y %)
- **Notificaciones automáticas** al llegar a 80% y 95% del límite semanal/sesión (revisión cada 4h en segundo plano con WorkManager)
- **Tema claro/oscuro** automático
- **Edge-to-edge** con insets correctos (IME, system bars)
- **Release firmado** con R8 + shrink (APK de ~1.3 MB)

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
./gradlew testDebugUnitTest   # 10 tests: scraper + ViewModel
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
