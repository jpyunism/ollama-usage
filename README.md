# Ollama Cloud Usage

App Android para ver el consumo del plan de **Ollama Cloud** (session usage, weekly usage y desglose por modelo).

[![Auditoría Motoko](https://raw.githubusercontent.com/motoko-section9/powned-by-motoko/main/badges/ollama-usage-10.webp)](https://github.com/motoko-section9/powned-by-motoko)

## Funcionalidades

- **Session usage** (%) con hora de reset
- **Weekly usage** (%) con desglose por modelo (requests y %)
- **Alertas configurables por %**: umbral de alerta y crítico independientes para semana y sesión (sliders 50–99%), con switch maestro de notificaciones
- **Consumo en pantalla de bloqueo**: notificación permanente (ongoing) con semana/sesión/plan, visible sin desbloquear; incluye extras de **Samsung Live Notifications / Now Bar** (estilo estándar + progreso + chip)
- **Frecuencia de refresco configurable**: slider de **1 min a 12 h** — de 1 a 14 min usa un servicio en primer plano (preciso), de 15 min en adelante usa WorkManager
- **Notificaciones automáticas** al cruzar los umbrales configurados (revisión cada 4h en segundo plano con WorkManager, sin duplicados hasta que baje del umbral)
- **Material You**: colores dinámicos en Android 12+, tema claro/oscuro automático
- **8 temas de color**: Sistema (dinámico), Índigo, Esmeralda, Teal, Océano, Violeta, Rosa y Ámbar — se aplican al instante y se guardan
- **Navegación por tabs** (Uso / Alertas / Temas) con Material 3 NavigationBar
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
./gradlew testDebugUnitTest   # 32 tests: scraper (parsing real sin red) + ViewModel + umbrales + temas + refresco
```

## CI

GitHub Actions corre `testDebugUnitTest` + `lintDebug` en cada push/PR (`.github/workflows/ci.yml`). Dependabot revisa dependencias semanalmente.

## Seguridad

- La cookie de sesión se guarda **cifrada** (EncryptedSharedPreferences + Android Keystore, AES256), no en claro.
- Contraseñas de firma del release: solo en `local.properties` (no versionado) o variables de entorno, nunca en el repo.

## Setup

1. Abre el proyecto en Android Studio (o `./gradlew assembleRelease` — el wrapper está versionado, no necesitas Gradle manual).
2. En la app, pega tu cookie de `ollama.com` (DevTools → Application → Cookies: `aid=...; __Secure-session=...`).
3. Toca "Actualizar" para refrescar.

> ⚠️ La cookie de sesión expira. Cuando la web deje de funcionar, copia una nueva.

## Build

- `./gradlew assembleDebug` — APK debug
- `./gradlew assembleRelease` — APK release firmado (keystore local, NO versionado)

## Disclaimer

Uso personal. No afiliado con Ollama. Scraping de tu propia cuenta con tu propia cookie — respeta los términos de servicio.
